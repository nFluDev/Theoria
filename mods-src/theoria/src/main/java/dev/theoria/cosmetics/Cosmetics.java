package dev.theoria.cosmetics;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * The four cosmetic armor slots of a player and their "hide" switches, as stored in the {@code theoria:cosmetics} attachment (saved, synced to EVERY client that receives the
 * player so others can see the look, copied on death — docs/04 §6). A cosmetic piece only decides what is DRAWN: it is never in an equipment slot, so it gives no armor, no
 * attribute and no enchantment (ADR-007 K3). A hidden slot draws NOTHING — neither the cosmetic piece nor the armor really worn there (the dot at the top-right of each cosmetic
 * slot, like Curios' own render toggle). The record is immutable; a change builds a new one ({@link #with}, {@link #withHidden}).
 *
 * @param hidden bit {@code i} set = the slot {@code SLOTS.get(i)} is hidden
 */
public record Cosmetics(ItemStack head, ItemStack chest, ItemStack legs, ItemStack feet, int hidden) {
    public static final Cosmetics NONE = new Cosmetics(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, 0);
    private static final int ALL = (1 << 4) - 1;
    /** Order of the slots in the menu, the attachment and the screen: head, chest, legs, feet. */
    public static final List<EquipmentSlot> SLOTS = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
    public static final MapCodec<Cosmetics> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("head", ItemStack.EMPTY).forGetter(Cosmetics::head),
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("chest", ItemStack.EMPTY).forGetter(Cosmetics::chest),
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("legs", ItemStack.EMPTY).forGetter(Cosmetics::legs),
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("feet", ItemStack.EMPTY).forGetter(Cosmetics::feet),
            com.mojang.serialization.Codec.INT.optionalFieldOf("hidden", 0).forGetter(Cosmetics::hidden)).apply(i, Cosmetics::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, Cosmetics> STREAM_CODEC = StreamCodec.composite(
            ItemStack.OPTIONAL_STREAM_CODEC, Cosmetics::head, ItemStack.OPTIONAL_STREAM_CODEC, Cosmetics::chest,
            ItemStack.OPTIONAL_STREAM_CODEC, Cosmetics::legs, ItemStack.OPTIONAL_STREAM_CODEC, Cosmetics::feet,
            net.minecraft.network.codec.ByteBufCodecs.VAR_INT, Cosmetics::hidden, Cosmetics::new);

    /** Only the four defined bits count (a save or packet may carry anything). */
    public Cosmetics {
        hidden &= ALL;
    }

    /** The vanilla "empty armor slot" background icon of a slot, drawn in an empty worn or cosmetic slot. */
    public static net.minecraft.resources.Identifier emptyIcon(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> net.minecraft.world.inventory.InventoryMenu.EMPTY_ARMOR_SLOT_HELMET;
            case CHEST -> net.minecraft.world.inventory.InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE;
            case LEGS -> net.minecraft.world.inventory.InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS;
            default -> net.minecraft.world.inventory.InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS;
        };
    }

    public ItemStack get(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> head;
            case CHEST -> chest;
            case LEGS -> legs;
            case FEET -> feet;
            default -> ItemStack.EMPTY;
        };
    }

    /** A copy with {@code slot} replaced (a copy of the stack too: the attachment never shares a stack with a menu or an inventory). Non-armor slots change nothing. */
    public Cosmetics with(EquipmentSlot slot, ItemStack stack) {
        ItemStack own = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        return switch (slot) {
            case HEAD -> new Cosmetics(own, chest, legs, feet, hidden);
            case CHEST -> new Cosmetics(head, own, legs, feet, hidden);
            case LEGS -> new Cosmetics(head, chest, own, feet, hidden);
            case FEET -> new Cosmetics(head, chest, legs, own, hidden);
            default -> this;
        };
    }

    /** A copy with the hide switch of {@code slot} set. Non-armor slots change nothing. */
    public Cosmetics withHidden(EquipmentSlot slot, boolean hide) {
        int index = indexOf(slot);
        if (index < 0) return this;
        return new Cosmetics(head, chest, legs, feet, hide ? hidden | (1 << index) : hidden & ~(1 << index));
    }

    public boolean isHidden(EquipmentSlot slot) {
        int index = indexOf(slot);
        return index >= 0 && (hidden & (1 << index)) != 0;
    }

    /** No cosmetic piece in any slot (the hide switches are separate: see {@link #hidden()}). */
    public boolean isEmpty() {
        return head.isEmpty() && chest.isEmpty() && legs.isEmpty() && feet.isEmpty();
    }

    /** Index of {@code slot} in {@link #SLOTS}, -1 for a slot that has no cosmetic. */
    public static int indexOf(EquipmentSlot slot) {
        return SLOTS.indexOf(slot);
    }
}
