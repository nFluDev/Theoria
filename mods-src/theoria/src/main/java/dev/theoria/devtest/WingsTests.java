package dev.theoria.devtest;

import dev.theoria.wings.WingChoice;
import dev.theoria.wings.WingsCatalog;
import dev.theoria.wings.WingsModule;
import dev.theoria.wings.WingsServer;
import dev.theoria.wings.WingsServer.Result;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Phase 4 GameTests, headless, against the REAL Fantastic Wings (a runtime-only dependency of the dev runs): the select request
 * wears / removes wings for a plain survival player, hostile ids never reach a command, and the choice survives death.
 * What a headless test cannot see — the 3D preview, the ESC button, the screen — is the unattended client scenario
 * ({@code runDevClient -PdevClient=wings}); how it LOOKS is the user's approval.
 */
final class WingsTests {
    private static final Pattern WING_TYPE = Pattern.compile("wing_type: \"([^\"]+)\"");

    private WingsTests() {}

    static void register(BiConsumer<String, Consumer<GameTestHelper>> add) {
        add.accept("wings_checks", WingsTests::checks);
        add.accept("wings_select_take", WingsTests::selectTake);
        add.accept("wings_survive_death", WingsTests::surviveDeath);
    }

    /** The wing Fantastic Wings itself reports for the player ("" = none), read the way a player would: /data get. */
    private static String worn(MinecraftServer server, ServerPlayer player) {
        List<String> out = new ArrayList<>();
        DevGameTests.runCommand(server, player, "data get entity @s neoforge:attachments", out);
        Matcher m = WING_TYPE.matcher(String.join(" ", out));
        return m.find() ? m.group(1) : "";
    }

    private static void checks(GameTestHelper h) {
        Dev.Report r = new Dev.Report("wings_checks");
        MinecraftServer server = h.getLevel().getServer();
        ServerPlayer player = FakePlayers.survival(h, "dev-wings-checks");
        List<String> out = new ArrayList<>();
        int passed = DevGameTests.runCommand(server, player, "theoria test wings", out);
        FakePlayers.remove(player);
        out.forEach(line -> r.line("out> %s", line));
        r.line("/theoria test wings -> %d checks passed", passed);
        r.write();
        h.assertTrue(passed >= 7, "/theoria test wings should pass >= 7 checks, got " + passed + " " + out);
        h.succeed();
    }

    private static void selectTake(GameTestHelper h) {
        Dev.Report r = new Dev.Report("wings_select_take");
        MinecraftServer server = h.getLevel().getServer();
        ServerPlayer player = FakePlayers.survival(h, "dev-wings");
        List<Identifier> catalog = WingsCatalog.ids(server.registryAccess());
        r.line("server catalog: %d wings %s", catalog.size(), catalog.stream().map(Identifier::getPath).toList());

        // Why the server must run /wings itself: a plain survival player cannot (ADR-002 d, "[kaynak]" until now).
        int[] own = {0};
        CommandSourceStack plain = player.createCommandSourceStack().withCallback((success, value) -> own[0] = success ? value : -1);
        server.getCommands().performPrefixedCommand(plain, "wings give @s fantasticwings:angel");
        String ownWorn = worn(server, player);
        r.line("player's own '/wings give' -> %d (expect <= 0: needs game-master permission); wings after: '%s'", own[0], ownWorn);

        Result dragon = WingsServer.select(player, "fantasticwings:dragon");
        String afterDragon = worn(server, player);
        String attachmentDragon = player.getData(WingsModule.CHOICE).id();
        Result angel = WingsServer.select(player, "fantasticwings:angel");
        String afterAngel = worn(server, player);
        r.line("select dragon -> %s, FW says '%s', attachment '%s'; then angel -> %s, FW says '%s'", dragon, afterDragon, attachmentDragon, angel, afterAngel);

        // Hostile / unknown input: nothing runs, nothing changes.
        List<String> hostile = List.of("fantasticwings:angel; op @s", "fantasticwings:angel\nop @s", "fantasticwings:angel op @s", "fantasticwings:nope",
                "minecraft:stone", "x".repeat(200), "FANTASTICWINGS:ANGEL", "../../etc", "a:b:c");
        List<String> hostileResults = new ArrayList<>();
        boolean hostileChanged = false;
        for (String bad : hostile) {
            Result res = WingsServer.select(player, bad);
            hostileResults.add(res.name());
            hostileChanged |= res != Result.UNKNOWN_WING || !worn(server, player).equals("fantasticwings:angel")
                    || !player.getData(WingsModule.CHOICE).id().equals("fantasticwings:angel");
        }
        boolean op = server.getPlayerList().isOp(new net.minecraft.server.players.NameAndId(player.getGameProfile()));
        r.line("%d hostile / unknown ids -> %s; wings still angel: %s; player became operator: %s", hostile.size(), hostileResults, !hostileChanged, op);

        Result off = WingsServer.select(player, "");
        String afterOff = worn(server, player);
        Result offAgain = WingsServer.select(player, "");
        r.line("take off -> %s, FW says '%s', attachment '%s'; take off again -> %s (no wings is not an error)", off, afterOff,
                player.getData(WingsModule.CHOICE).id(), offAgain);
        FakePlayers.remove(player);
        r.write();

        h.assertTrue(catalog.size() >= 11, "expected Fantastic Wings' 11 wings in the server registry, got " + catalog.size());
        h.assertTrue(own[0] <= 0 && ownWorn.isEmpty(), "a plain survival player ran /wings themselves — the permission premise of ADR-002 d changed");
        h.assertValueEqual(dragon, Result.EQUIPPED, "select dragon");
        h.assertValueEqual(afterDragon, "fantasticwings:dragon", "Fantastic Wings' own state after select dragon");
        h.assertValueEqual(attachmentDragon, "fantasticwings:dragon", "theoria:wings after select dragon");
        h.assertValueEqual(angel, Result.EQUIPPED, "switch to angel");
        h.assertValueEqual(afterAngel, "fantasticwings:angel", "Fantastic Wings' own state after switching");
        h.assertTrue(!hostileChanged, "hostile ids changed state: " + hostileResults);
        h.assertTrue(!op, "a hostile id made the player an operator — command injection");
        h.assertValueEqual(off, Result.REMOVED, "take off");
        h.assertValueEqual(afterOff, "", "Fantastic Wings' own state after take off");
        h.assertValueEqual(offAgain, Result.REMOVED, "take off when already bare");
        h.succeed();
    }

    private static void surviveDeath(GameTestHelper h) {
        Dev.Report r = new Dev.Report("wings_survive_death");
        MinecraftServer server = h.getLevel().getServer();
        ServerPlayer player = FakePlayers.survival(h, "dev-wings-death");
        int restoredBefore = WingsServer.restoredCount();
        WingsServer.select(player, "fantasticwings:fairy");
        String before = worn(server, player);

        ServerPlayer afterDeath = server.getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
        String attachment = afterDeath.getData(WingsModule.CHOICE).id();
        String wornAfterDeath = worn(server, afterDeath);
        int restoredAfterDeath = WingsServer.restoredCount();
        r.line("equipped fairy (FW '%s'); died + respawned: attachment '%s', FW '%s', restore handler ran %d time(s)", before, attachment, wornAfterDeath,
                restoredAfterDeath - restoredBefore);

        ServerPlayer afterReturn = server.getPlayerList().respawn(afterDeath, true, Entity.RemovalReason.CHANGED_DIMENSION);
        String wornAfterReturn = worn(server, afterReturn);
        String choiceAfterReturn = afterReturn.getData(WingsModule.CHOICE).id();
        r.line("End-style respawn keeping all data: attachment '%s', FW '%s', restore handler ran %d more time(s) (expect 0: nothing was lost)",
                choiceAfterReturn, wornAfterReturn, WingsServer.restoredCount() - restoredAfterDeath);

        WingsServer.select(afterReturn, "");
        ServerPlayer bare = server.getPlayerList().respawn(afterReturn, false, Entity.RemovalReason.KILLED);
        String bareWorn = worn(server, bare);
        String bareChoice = bare.getData(WingsModule.CHOICE).id();
        r.line("took wings off, died + respawned: attachment '%s', FW '%s' (expect none: no wings must appear)", bareChoice, bareWorn);

        // A removed wing (datapack changed): the stale choice must not crash the respawn.
        bare.setData(WingsModule.CHOICE, new WingChoice("fantasticwings:long_gone"));
        ServerPlayer stale = server.getPlayerList().respawn(bare, false, Entity.RemovalReason.KILLED);
        r.line("stale choice 'long_gone' + death: respawn survived, FW '%s'", worn(server, stale));
        FakePlayers.remove(stale);
        r.write();

        h.assertValueEqual(before, "fantasticwings:fairy", "FW state before dying");
        h.assertValueEqual(attachment, "fantasticwings:fairy", "choice after death (copyOnDeath)");
        h.assertValueEqual(wornAfterDeath, "fantasticwings:fairy", "wings worn again after death");
        h.assertValueEqual(restoredAfterDeath - restoredBefore, 1, "restore handler runs once per death");
        h.assertValueEqual(wornAfterReturn, "fantasticwings:fairy", "wings after End-style respawn");
        h.assertValueEqual(choiceAfterReturn, "fantasticwings:fairy", "choice after End-style respawn");
        h.assertValueEqual(WingsServer.restoredCount(), restoredAfterDeath, "End-style respawn must not re-apply");
        h.assertValueEqual(bareWorn, "", "wings appeared on a player who had taken them off");
        h.assertValueEqual(bareChoice, "", "choice after taking off and dying");
        h.succeed();
    }
}
