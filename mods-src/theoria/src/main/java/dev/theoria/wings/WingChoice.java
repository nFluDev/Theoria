package dev.theoria.wings;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The wing a player chose, as stored in the {@code theoria:wings} attachment (saved, synced to the client, copied on death —
 * Fantastic Wings itself drops its wings on death, so this is what makes them permanent: D14). Empty id = no wings.
 */
public record WingChoice(String id) {
    public static final WingChoice NONE = new WingChoice("");
    public static final MapCodec<WingChoice> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.optionalFieldOf("id", "").forGetter(WingChoice::id)).apply(i, WingChoice::new));
    public static final StreamCodec<ByteBuf, WingChoice> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.stringUtf8(WingsCatalog.MAX_ID_LENGTH), WingChoice::id, WingChoice::new);

    public boolean none() {
        return id.isEmpty();
    }
}
