package dev.theoria.curios;

import dev.theoria.core.Net;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/**
 * Client → server: "open my Curios Max screen". The server opens the menu for the sender only. {@code carried} is the stack on the cursor — only a CREATIVE player's counts (the server cannot
 * see a creative client's cursor, exactly as in Curios' own packet); everyone else's cursor is what the server already knows.
 */
public record OpenCuriosMaxPayload(ItemStack carried) implements CustomPacketPayload {
    public static final Type<OpenCuriosMaxPayload> TYPE = Net.type("open_curios_max");
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenCuriosMaxPayload> STREAM_CODEC = StreamCodec.composite(
            ItemStack.OPTIONAL_STREAM_CODEC, OpenCuriosMaxPayload::carried, OpenCuriosMaxPayload::new);

    public OpenCuriosMaxPayload() {
        this(ItemStack.EMPTY);
    }

    @Override
    public Type<OpenCuriosMaxPayload> type() {
        return TYPE;
    }
}
