package dev.theoria.wings;

import dev.theoria.Theoria;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Server half of the wings feature. A survival player cannot run {@code /wings} (it needs game-master permission, ADR-002 d), so the
 * server runs it for them with the server's own command source — but only for an id that is a key of the server's own
 * {@code fantasticwings:wings} registry; text from a client never reaches a command line unchecked.
 */
public final class WingsServer {
    public enum Result { EQUIPPED, REMOVED, UNAVAILABLE, UNKNOWN_WING, FAILED }

    private static int restored;

    private WingsServer() {}

    /** Times the wings were put back on after a death (test evidence: the handler, not luck, restored them). */
    public static int restoredCount() {
        return restored;
    }

    /** Handles a select request: {@code raw} is a wing id, or "" to take the wings off. */
    public static Result select(ServerPlayer player, String raw) {
        Result result = raw.isEmpty() ? take(player) : equip(player, raw);
        switch (result) {
            case EQUIPPED -> notify(player, Component.translatable("theoria.wings.equipped", displayName(WingsCatalog.parse(raw))));
            case REMOVED -> notify(player, Component.translatable("theoria.wings.removed"));
            case UNAVAILABLE -> notify(player, Component.translatable("theoria.wings.unavailable"));
            default -> notify(player, Component.translatable("theoria.wings.failed"));
        }
        return result;
    }

    private static Result equip(ServerPlayer player, String raw) {
        Identifier id = WingsCatalog.parse(raw);
        RegistryAccess access = player.level().registryAccess();
        if (WingsCatalog.ids(access).isEmpty()) return Result.UNAVAILABLE;
        if (id == null || !WingsCatalog.contains(access, id)) {
            Theoria.LOGGER.warn("{} asked for an unknown wing '{}' — ignored", player.getGameProfile().name(), raw.length() > 40 ? raw.substring(0, 40) + "…" : raw);
            return Result.UNKNOWN_WING;
        }
        if (!run(player, "wings give @s " + id)) return Result.FAILED;
        player.setData(WingsModule.CHOICE, new WingChoice(id.toString()));
        return Result.EQUIPPED;
    }

    private static Result take(ServerPlayer player) {
        // "Target doesn't have wings to remove" is a failure for the command, not for us: the goal state (no wings) holds either way.
        run(player, "wings take @s");
        player.setData(WingsModule.CHOICE, WingChoice.NONE);
        return Result.REMOVED;
    }

    /** Fantastic Wings drops its wings when the player dies; the choice survives (copyOnDeath), so wear them again. */
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        // keeping all data (End return) keeps Fantastic Wings' own attachment too — nothing to restore.
        if (event.isEndConquered() || !(event.getEntity() instanceof ServerPlayer player)) return;
        WingChoice choice = player.getData(WingsModule.CHOICE);
        if (choice.none()) return;
        Identifier id = WingsCatalog.parse(choice.id());
        if (id == null || !WingsCatalog.contains(player.level().registryAccess(), id)) {
            Theoria.LOGGER.warn("{} had wings '{}' that the catalog no longer knows — not restored", player.getGameProfile().name(), choice.id());
            return;
        }
        if (run(player, "wings give @s " + id)) restored++;
    }

    /** Runs a command as the server with the player as {@code @s}; true if the command reported success. */
    static boolean run(ServerPlayer player, String command) {
        MinecraftServer server = player.level().getServer();
        boolean[] ok = {false};
        CommandSourceStack source = server.createCommandSourceStack().withEntity(player).withSuppressedOutput()
                .withCallback((success, value) -> ok[0] = success);
        server.getCommands().performPrefixedCommand(source, command);
        return ok[0];
    }

    /** The name the client shows: its lang entry when it has one, otherwise a tidied-up id. */
    static Component displayName(Identifier id) {
        return id == null ? Component.empty() : Component.translatableWithFallback(WingsCatalog.nameKey(id), WingsCatalog.prettyName(id));
    }

    private static void notify(ServerPlayer player, Component message) {
        player.sendSystemMessage(message, true);
    }
}
