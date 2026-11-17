package dev.theoria.catalyst;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Data component {@code theoria:catalysts} (ADR-006 Karar 2, schema frozen in Phase 13): the catalysts an item carries, in the order the player sees them,
 * plus how many times Netherite was applied. <b>Only {@code (id, level, grade)} is stored — never a value:</b> every effective value is looked up live in
 * {@link CatalystData} (cap × curve × tier basis), so rebalancing reaches items already made. Persistent and network-synchronized: the event listeners
 * ask both sides. An invalid record fails the codec and the component is dropped (the item loads catalyst-less; the world is not corrupted).
 */
public record Catalysts(List<Slot> slots, int netherite) {
    public static final Catalysts EMPTY = new Catalysts(List.of(), 0);

    /** One filled slot. {@code grade} is the material tier of a graded catalyst (Menzil Gücü) and 0 for every other. */
    public record Slot(Identifier id, int level, int grade) {
        static final Codec<Slot> CODEC = RecordCodecBuilder.create(i -> i.group(
                Identifier.CODEC.fieldOf("id").forGetter(Slot::id),
                Codec.intRange(1, 64).fieldOf("level").forGetter(Slot::level),
                Codec.intRange(0, 64).optionalFieldOf("grade", 0).forGetter(Slot::grade)).apply(i, Slot::new));
        static final StreamCodec<RegistryFriendlyByteBuf, Slot> STREAM_CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, Slot::id, ByteBufCodecs.VAR_INT, Slot::level, ByteBufCodecs.VAR_INT, Slot::grade, Slot::new);

        public CatalystData.@Nullable Def def() {
            return id.getNamespace().equals("theoria") ? CatalystData.table().defs().get(id.getPath()) : null;
        }
    }

    public static final Codec<Catalysts> CODEC = RecordCodecBuilder.<Catalysts>create(i -> i.group(
            Slot.CODEC.listOf().optionalFieldOf("slots", List.of()).forGetter(Catalysts::slots),
            Codec.intRange(0, 64).optionalFieldOf("netherite", 0).forGetter(Catalysts::netherite)).apply(i, Catalysts::new))
            .validate(Catalysts::validate);
    public static final StreamCodec<RegistryFriendlyByteBuf, Catalysts> STREAM_CODEC = StreamCodec.composite(
            Slot.STREAM_CODEC.apply(ByteBufCodecs.list()), Catalysts::slots, ByteBufCodecs.VAR_INT, Catalysts::netherite, Catalysts::new);

    public Catalysts {
        slots = List.copyOf(slots);
    }

    /** Codec rules of ADR-006 Karar 2: known id, unique id, level 1..levels, grade only on a graded catalyst, netherite ≤ max. The host's slot count is a rule, not a codec matter. */
    static DataResult<Catalysts> validate(Catalysts value) {
        CatalystData.Table table = CatalystData.table();
        Set<Identifier> seen = new HashSet<>();
        for (Slot slot : value.slots) {
            CatalystData.Def def = slot.def();
            if (def == null || def.kind() == CatalystData.Kind.NETHERITE) return DataResult.error(() -> "Unknown catalyst " + slot.id());
            if (!seen.add(slot.id())) return DataResult.error(() -> "Catalyst " + slot.id() + " appears twice");
            if (slot.level() > table.levels()) return DataResult.error(() -> "Catalyst level " + slot.level() + " above " + table.levels());
            if (slot.grade() != 0 && !def.graded()) return DataResult.error(() -> "Only a graded catalyst carries a grade: " + slot.id());
        }
        if (value.netherite > table.netheriteMax()) return DataResult.error(() -> "Netherite applications " + value.netherite + " above " + table.netheriteMax());
        if (value.slots.size() > table.slotMax() + table.platinumBonus()) return DataResult.error(() -> "More catalyst slots than any host has: " + value.slots.size());
        return DataResult.success(value);
    }

    public boolean isEmpty() {
        return slots.isEmpty() && netherite == 0;
    }

    public int indexOf(Identifier id) {
        for (int i = 0; i < slots.size(); i++) if (slots.get(i).id().equals(id)) return i;
        return -1;
    }

    public Catalysts withSlot(int index, Slot slot) {
        List<Slot> next = new ArrayList<>(slots);
        if (index == next.size()) next.add(slot);
        else next.set(index, slot);
        return new Catalysts(next, netherite);
    }

    public Catalysts withoutSlot(int index) {
        List<Slot> next = new ArrayList<>(slots);
        next.remove(index);
        return new Catalysts(next, netherite);
    }

    public Catalysts withNetherite(int applications) {
        return new Catalysts(slots, applications);
    }

    /** The component of a stack, or {@link #EMPTY}. */
    public static Catalysts of(ItemStack stack) {
        Catalysts value = stack.get(CatalystComponents.CATALYSTS.get());
        return value == null ? EMPTY : value;
    }
}
