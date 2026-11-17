package dev.theoria.devtest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.theoria.core.Attachments;
import dev.theoria.core.Net;
import dev.theoria.core.check.Checks;
import io.netty.buffer.ByteBuf;
import java.util.function.Supplier;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.attachment.AttachmentType;

/**
 * Dev-only attachment + payload pair that exercises the core helpers end to end:
 * client sends {@link Ping} → server stores it in {@link #KEPT} (synced attachment) and answers {@link Pong}.
 */
public final class DevProbe {
    public record Probe(int value) {
        public static final Probe ZERO = new Probe(0);
        public static final MapCodec<Probe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.fieldOf("value").forGetter(Probe::value)).apply(i, Probe::new));
        public static final StreamCodec<ByteBuf, Probe> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Probe::value, Probe::new);
    }

    public record Ping(int value) implements CustomPacketPayload {
        public static final Type<Ping> TYPE = Net.type("dev_ping");
        public static final StreamCodec<ByteBuf, Ping> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Ping::value, Ping::new);

        @Override
        public Type<Ping> type() {
            return TYPE;
        }
    }

    public record Pong(int value) implements CustomPacketPayload {
        public static final Type<Pong> TYPE = Net.type("dev_pong");
        public static final StreamCodec<ByteBuf, Pong> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Pong::value, Pong::new);

        @Override
        public Type<Pong> type() {
            return TYPE;
        }
    }

    /** Standard player state: saved, synced, survives death. */
    public static Supplier<AttachmentType<Probe>> KEPT;
    /** Saved but neither synced nor copied on death — the control case. */
    public static Supplier<AttachmentType<Probe>> LOST;

    private DevProbe() {}

    static void init() {
        KEPT = Attachments.playerState("dev_probe", () -> Probe.ZERO, Probe.CODEC, Probe.STREAM_CODEC);
        LOST = Attachments.register("dev_probe_volatile", () -> Probe.ZERO, b -> b.serialize(Probe.CODEC));
        Net.serverbound(Ping.TYPE, Ping.STREAM_CODEC, (ping, player) -> {
            player.setData(KEPT, new Probe(ping.value()));
            Net.send(player, new Pong(ping.value()));
        });
        Net.clientbound(Pong.TYPE, Pong.STREAM_CODEC);
        Checks.register("core", "attachment_roundtrip", source -> {
            if (!(source.getEntity() instanceof ServerPlayer player)) return "skipped (no player)";
            Probe before = player.getData(KEPT);
            player.setData(KEPT, new Probe(before.value() + 1));
            int read = player.getData(KEPT).value();
            player.setData(KEPT, before);
            Checks.require(read == before.value() + 1, "attachment read back " + read);
            return "set/get ok, restored to " + before.value();
        });
    }
}
