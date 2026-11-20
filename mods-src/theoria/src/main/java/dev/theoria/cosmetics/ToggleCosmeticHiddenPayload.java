package dev.theoria.cosmetics;

import dev.theoria.core.Net;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → server: "flip the hide switch of my cosmetic slot {@code slot}" (0 = head … 3 = feet). The server accepts only 0..3 and only changes the sender's own data. */
public record ToggleCosmeticHiddenPayload(int slot) implements CustomPacketPayload {
    public static final Type<ToggleCosmeticHiddenPayload> TYPE = Net.type("toggle_cosmetic_hidden");
    public static final StreamCodec<ByteBuf, ToggleCosmeticHiddenPayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, ToggleCosmeticHiddenPayload::slot, ToggleCosmeticHiddenPayload::new);

    @Override
    public Type<ToggleCosmeticHiddenPayload> type() {
        return TYPE;
    }
}
