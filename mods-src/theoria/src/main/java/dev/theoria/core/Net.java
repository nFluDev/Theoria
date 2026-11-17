package dev.theoria.core;

import dev.theoria.Theoria;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Payload registration in one place. Features declare payloads from the mod constructor; the client only *asks*
 * (serverbound), the server validates and answers through synced attachments or clientbound payloads (docs/03 §4).
 * Client-side handlers and sending to the server live in {@code core.client.NetClient}.
 */
public final class Net {
    /** Bump when any payload layout changes. */
    private static final String PROTOCOL = "1";
    private static final List<Consumer<PayloadRegistrar>> PENDING = new ArrayList<>();
    private static boolean registered;

    private Net() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(Net::onRegister);
    }

    public static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String path) {
        return new CustomPacketPayload.Type<>(Theoria.id(path));
    }

    /** Client → server request. The handler runs on the server thread with the sending player. */
    public static <T extends CustomPacketPayload> void serverbound(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, BiConsumer<T, ServerPlayer> handler) {
        add(registrar -> registrar.playToServer(type, codec, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) handler.accept(payload, player);
        }));
    }

    /** Server → client message. Its handler must be registered with {@code NetClient.handle}. */
    public static <T extends CustomPacketPayload> void clientbound(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        add(registrar -> registrar.playToClient(type, codec));
    }

    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static int payloadCount() {
        return PENDING.size();
    }

    private static void add(Consumer<PayloadRegistrar> registration) {
        if (registered) throw new IllegalStateException("Payloads must be declared from the mod constructor");
        PENDING.add(registration);
    }

    private static void onRegister(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL);
        PENDING.forEach(registration -> registration.accept(registrar));
        registered = true;
    }
}
