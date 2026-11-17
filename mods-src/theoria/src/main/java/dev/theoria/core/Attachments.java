package dev.theoria.core;

import com.mojang.serialization.MapCodec;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.attachment.AttachmentType;

/** Registration helpers for NeoForge data attachments (all persistent player state lives in attachments, docs/03 §4). */
public final class Attachments {
    private Attachments() {}

    /**
     * Server-authoritative player state: saved with the entity, kept across death and synced to clients.
     * Replace the value with {@code setData} (which syncs); after mutating in place call {@code syncData}.
     */
    public static <T> Supplier<AttachmentType<T>> playerState(String name, Supplier<T> defaultValue, MapCodec<T> codec,
            StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec) {
        return register(name, defaultValue, b -> b.serialize(codec).copyOnDeath().sync(streamCodec));
    }

    /** Any other combination (not synced, lost on death, …). */
    public static <T> Supplier<AttachmentType<T>> register(String name, Supplier<T> defaultValue, UnaryOperator<AttachmentType.Builder<T>> options) {
        return TheoriaRegistries.ATTACHMENTS.register(name, () -> options.apply(AttachmentType.builder(defaultValue)).build());
    }
}
