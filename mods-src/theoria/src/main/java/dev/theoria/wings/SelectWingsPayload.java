package dev.theoria.wings;

import dev.theoria.core.Net;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → server: wear this wing; an empty id means "take the wings off". The server validates before it acts. */
public record SelectWingsPayload(String wingId) implements CustomPacketPayload {
    public static final Type<SelectWingsPayload> TYPE = Net.type("select_wings");
    public static final StreamCodec<ByteBuf, SelectWingsPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.stringUtf8(WingsCatalog.MAX_ID_LENGTH), SelectWingsPayload::wingId, SelectWingsPayload::new);

    @Override
    public Type<SelectWingsPayload> type() {
        return TYPE;
    }
}
