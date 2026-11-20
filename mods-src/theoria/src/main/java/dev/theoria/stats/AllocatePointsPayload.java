package dev.theoria.stats;

import dev.theoria.core.Net;
import io.netty.buffer.ByteBuf;
import java.util.Map;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → server: put these points into these stats (stat id → amount). The screen stages its +/- locally and sends ONE request on Apply; the
 * server validates every entry against the stat table and the points the player really has ({@link StatsServer#allocate}) — it never trusts the client's numbers.
 */
public record AllocatePointsPayload(Map<String, Integer> add) implements CustomPacketPayload {
    public static final Type<AllocatePointsPayload> TYPE = Net.type("allocate_points");
    public static final StreamCodec<ByteBuf, AllocatePointsPayload> STREAM_CODEC = PlayerStats.POINTS_CODEC.map(AllocatePointsPayload::new, AllocatePointsPayload::add);

    @Override
    public Type<AllocatePointsPayload> type() {
        return TYPE;
    }
}
