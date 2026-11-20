package dev.theoria.devtest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Survival players for GameTests. {@code GameTestHelper#makeMockServerPlayerInLevel} is hard-coded CREATIVE (takes no damage). */
final class FakePlayers {
    private FakePlayers() {}

    /** A logged-in survival player standing in the test structure. Remove with {@link #remove}. */
    static ServerPlayer survival(GameTestHelper h, String name) {
        ServerLevel level = h.getLevel();
        MinecraftServer server = level.getServer();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
        // An anonymous subclass on purpose: NeoForge's config sync skips players whose class is not exactly ServerPlayer (that is how its own GameTestPlayer survives server ticks);
        // a plain ServerPlayer that stays in the world across ticks crashes the server tick with "configsToSync should contain an entry" (docs/10 Q84).
        ServerPlayer player = new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation()) {};
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection); // Faz 11: other mods (Curios) send their own payloads on login; a bare mock connection refuses every modded payload
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        player.connection.markClientLoaded(); // otherwise ServerPlayer#isInvulnerableTo is true for 60 ticks
        Vec3 at = h.absoluteVec(new Vec3(1.5, 1, 1.5));
        player.teleportTo(at.x, at.y, at.z);
        return player;
    }

    static void remove(ServerPlayer player) {
        player.level().getServer().getPlayerList().remove(player);
    }
}
