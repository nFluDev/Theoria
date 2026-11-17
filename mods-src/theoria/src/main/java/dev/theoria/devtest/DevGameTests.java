package dev.theoria.devtest;

import com.mojang.serialization.JsonOps;
import dev.theoria.Theoria;
import dev.theoria.core.TheoriaRegistries;
import dev.theoria.devtest.DevProbe.Ping;
import dev.theoria.devtest.DevProbe.Probe;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

/**
 * GameTests ({@code ./gradlew runGameTestServer}). Two groups:
 * core_* — the mod skeleton; vanilla_* — vanilla/NeoForge behaviour our ADRs depend on, kept as regression
 * tests against NeoForge updates (ADR-001). 26.x GameTests are data-driven: function registry + test instance.
 */
public final class DevGameTests {
    private DevGameTests() {}

    public static void init(IEventBus modBus) {
        DevProbe.init();
        add("core_commands", DevGameTests::coreCommands);
        add("core_attachment_death", DevGameTests::coreAttachmentDeath);
        add("core_codecs", DevGameTests::coreCodecs);
        add("vanilla_enchant_cap", VanillaAssumptions::enchantCap);
        add("vanilla_combat_rules_cap", VanillaAssumptions::combatRulesCap);
        BalanceTests.register(DevGameTests::add);
        HudTests.register(DevGameTests::add);
        WingsTests.register(DevGameTests::add);
        GearTests.register(DevGameTests::add);
        CatalystTests.register(DevGameTests::add); // Faz 7b: the catalyst system (after the event-chain proof below)
        MobTests.register(DevGameTests::add); // Faz 8: species, spawn rules, zone tiers, drops, bosses
        EnchantLevelTests.register(DevGameTests::add); // Faz 7b: ADR-004/006 event-chain proof comes before any catalyst code
        add("dump_vanilla_mobs", VanillaMobDump::run);
        modBus.addListener(DevGameTests::registerTests);
    }

    private static void add(String name, Consumer<GameTestHelper> test) {
        TheoriaRegistries.TEST_FUNCTIONS.register(name, () -> test);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> env = event.registerEnvironment(Theoria.id("dev"), new TestEnvironmentDefinition.AllOf(List.of()));
        for (var fn : TheoriaRegistries.TEST_FUNCTIONS.getEntries()) {
            // Behaviour tests need room: real AI in a 24×24 arena (data/theoria/structure/dev_arena.nbt) so neighbouring tests do not share mobs, players or floors.
            String path = fn.getId().getPath();
            Identifier structure = path.startsWith("mobs_ai_") || path.startsWith("mobs_boss_") || path.equals("mobs_xp") ? Theoria.id("dev_arena") : Identifier.withDefaultNamespace("empty");
            event.registerTest(fn.getId(), new FunctionGameTestInstance(fn.key(), new TestData<>(env, structure, 200, 0, true)));
        }
    }

    // ------------------------------------------------------------ core
    /** Runs a command as the server with {@code entity} as @s; returns the result (-1 if it failed) and collects output. */
    static int runCommand(MinecraftServer server, Entity entity, String command, List<String> output) {
        int[] result = {-1};
        CommandSource sink = new CommandSource() {
            @Override
            public void sendSystemMessage(Component message) {
                output.add(message.getString());
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        };
        CommandSourceStack source = server.createCommandSourceStack().withSource(sink).withEntity(entity)
                .withCallback((success, value) -> result[0] = success ? value : -1);
        server.getCommands().performPrefixedCommand(source, command);
        return result[0];
    }

    private static void coreCommands(GameTestHelper h) {
        Dev.Report r = new Dev.Report("core_commands");
        MinecraftServer server = h.getLevel().getServer();
        ServerPlayer player = FakePlayers.survival(h, "dev-commands");
        List<String> out = new ArrayList<>();
        int version = runCommand(server, player, "theoria version", out);
        int core = runCommand(server, player, "theoria test core", out);
        int all = runCommand(server, player, "theoria test", out);
        int unknown = runCommand(server, player, "theoria test no_such_area", out);
        // A survival player without operator rights must not be able to run the checks, but may ask the version.
        int[] denied = {0};
        CommandSourceStack plain = player.createCommandSourceStack().withCallback((success, value) -> denied[0] = success ? value : -1);
        server.getCommands().performPrefixedCommand(plain, "theoria test core");
        FakePlayers.remove(player);
        out.forEach(line -> r.line("out> %s", line));
        r.line("version -> %d, test core -> %d, test (all) -> %d, unknown area -> %d, test core as non-op -> %d", version, core, all, unknown, denied[0]);
        r.write();
        h.assertValueEqual(version, 1, "/theoria version result");
        h.assertTrue(core >= 4, "/theoria test core should pass >= 4 checks, got " + core);
        h.assertTrue(all >= core, "/theoria test should run every area");
        h.assertValueEqual(unknown, -1, "unknown area must fail");
        h.assertTrue(denied[0] <= 0, "non-op must not run /theoria test");
        h.assertTrue(out.stream().anyMatch(line -> line.contains(Theoria.version())), "version output should contain the mod version");
        h.succeed();
    }

    private static void coreAttachmentDeath(GameTestHelper h) {
        Dev.Report r = new Dev.Report("core_attachment_death");
        MinecraftServer server = h.getLevel().getServer();
        ServerPlayer player = FakePlayers.survival(h, "dev-death");
        player.setData(DevProbe.KEPT, new Probe(42));
        player.setData(DevProbe.LOST, new Probe(7));
        ServerPlayer afterDeath = server.getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
        int kept = afterDeath.getData(DevProbe.KEPT).value();
        int lost = afterDeath.getData(DevProbe.LOST).value();
        r.line("death + respawn: playerState attachment %d (expect 42), plain attachment %d (expect 0)", kept, lost);
        afterDeath.setData(DevProbe.LOST, new Probe(9));
        ServerPlayer afterReturn = server.getPlayerList().respawn(afterDeath, true, Entity.RemovalReason.CHANGED_DIMENSION);
        int keptAll = afterReturn.getData(DevProbe.LOST).value();
        r.line("respawn keeping all data (End return): plain attachment %d (expect 9), playerState %d (expect 42)",
                keptAll, afterReturn.getData(DevProbe.KEPT).value());
        FakePlayers.remove(afterReturn);
        r.write();
        h.assertValueEqual(kept, 42, "copyOnDeath attachment after death");
        h.assertValueEqual(lost, 0, "non-copy attachment after death");
        h.assertValueEqual(keptAll, 9, "non-copy attachment after non-death respawn");
        h.succeed();
    }

    private static void coreCodecs(GameTestHelper h) {
        Dev.Report r = new Dev.Report("core_codecs");
        Probe probe = new Probe(1234);
        var json = Probe.CODEC.codec().encodeStart(JsonOps.INSTANCE, probe).getOrThrow();
        Probe fromJson = Probe.CODEC.codec().parse(JsonOps.INSTANCE, json).getOrThrow();
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        Ping.STREAM_CODEC.encode(buf, new Ping(98765));
        int bytes = buf.readableBytes();
        Ping fromWire = Ping.STREAM_CODEC.decode(buf);
        r.line("save codec: %s -> %s; stream codec: Ping(98765) -> %d bytes -> %s", json, fromJson, bytes, fromWire);
        r.write();
        h.assertValueEqual(fromJson, probe, "save codec roundtrip");
        h.assertValueEqual(fromWire.value(), 98765, "stream codec roundtrip");
        h.succeed();
    }
}
