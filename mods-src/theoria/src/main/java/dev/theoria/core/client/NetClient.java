package dev.theoria.core.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/** Client half of {@link dev.theoria.core.Net}: handlers for clientbound payloads and sending requests to the server. */
public final class NetClient {
    private static final List<Consumer<RegisterClientPayloadHandlersEvent>> PENDING = new ArrayList<>();

    private NetClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener((RegisterClientPayloadHandlersEvent event) -> PENDING.forEach(registration -> registration.accept(event)));
    }

    /** The handler runs on the client main thread. Declare from the client mod constructor. */
    public static <T extends CustomPacketPayload> void handle(CustomPacketPayload.Type<T> type, Consumer<T> handler) {
        PENDING.add(event -> event.register(type, (payload, context) -> handler.accept(payload)));
    }

    public static void send(CustomPacketPayload payload) {
        ClientPacketDistributor.sendToServer(payload);
    }
}
