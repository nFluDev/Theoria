package dev.theoria.cosmetics;

import dev.theoria.core.check.Checks;
import dev.theoria.cosmetics.CosmeticLook.Viewer;
import java.util.List;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Area "cosmetics" of {@code /theoria test}: the look rule against the sentences of docs/04 §6 (a hand-written table) and against its own promises swept over
 * every input — "settings off = vanilla", "wings are never touched", "a hidden slot draws nothing", "a cosmetic never leaks". Pure: no player, no world. The attachment on a real player,
 * death, menu and stats are the {@code cosmetics_*} / {@code curios_*} GameTests.
 */
public final class CosmeticsChecks {
    private CosmeticsChecks() {}

    private static final List<EquipmentSlot> SLOTS = Cosmetics.SLOTS;

    private static ItemStack piece(EquipmentSlot slot, boolean diamond) {
        return new ItemStack(switch (slot) {
            case HEAD -> diamond ? Items.DIAMOND_HELMET : Items.IRON_HELMET;
            case CHEST -> diamond ? Items.DIAMOND_CHESTPLATE : Items.IRON_CHESTPLATE;
            case LEGS -> diamond ? Items.DIAMOND_LEGGINGS : Items.IRON_LEGGINGS;
            default -> diamond ? Items.DIAMOND_BOOTS : Items.IRON_BOOTS;
        });
    }

    public static void init() {
        Checks.register("cosmetics", "accepts", source -> {
            for (EquipmentSlot slot : SLOTS) {
                Checks.require(CosmeticRules.accepts(slot, piece(slot, true)), "a diamond piece must fit its own slot " + slot);
                for (EquipmentSlot other : SLOTS) {
                    if (other != slot) Checks.require(!CosmeticRules.accepts(other, piece(slot, true)), slot + " armor must not fit the " + other + " cosmetic slot");
                }
                Checks.require(!CosmeticRules.accepts(slot, new ItemStack(Items.STICK)), "a stick is not armor");
                Checks.require(!CosmeticRules.accepts(slot, ItemStack.EMPTY), "an empty stack is not a cosmetic (it clears the slot)");
            }
            Checks.require(!CosmeticRules.accepts(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD)), "there is no cosmetic main hand");
            Checks.require(CosmeticRules.accepts(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA)), "elytra may be a chest cosmetic (it is an equippable chest asset)");
            Checks.require(CosmeticRules.isWings(new ItemStack(Items.ELYTRA)) && !CosmeticRules.isWings(piece(EquipmentSlot.CHEST, true)), "only the glider counts as wings");
            return "armor fits only its own slot; sticks and the empty stack are refused; elytra is wings";
        });
        Checks.register("cosmetics", "look_table", source -> {
            ItemStack ironChest = piece(EquipmentSlot.CHEST, false);
            ItemStack diamondChest = piece(EquipmentSlot.CHEST, true);
            ItemStack ironLegs = piece(EquipmentSlot.LEGS, false);
            ItemStack elytra = new ItemStack(Items.ELYTRA);
            Viewer self = new Viewer(true, true, true);
            // "Cosmetic slot boşsa vanilla armorlar takılıysa gözükecek."
            expect(ironChest, EquipmentSlot.CHEST, ironChest, ItemStack.EMPTY, false, self, "empty cosmetic slot: the real armor shows");
            // "Cosmetic slot doluysa vanillayı override edecek."
            expect(diamondChest, EquipmentSlot.CHEST, ironChest, diamondChest, false, self, "cosmetic over real: the cosmetic shows");
            expect(diamondChest, EquipmentSlot.CHEST, ItemStack.EMPTY, diamondChest, false, self, "cosmetic over nothing: the cosmetic shows");
            // "Full diamond olsan da pantolonu gold taksan da sadece pantolon gold, diğerleri diamond gözükecek."
            ItemStack goldLegs = new ItemStack(Items.GOLDEN_LEGGINGS);
            expect(goldLegs, EquipmentSlot.LEGS, piece(EquipmentSlot.LEGS, true), goldLegs, false, self, "a gold-leggings cosmetic over a full diamond set changes only the leggings");
            expect(diamondChest, EquipmentSlot.CHEST, diamondChest, ItemStack.EMPTY, false, self, "…the chest stays the real diamond one");
            // "Leggings'i hide yaptım: cosmetic boş da olsa vanilla leggings gözükmemeli, cosmetic doluysa gene gözükmemeli."
            expect(ItemStack.EMPTY, EquipmentSlot.LEGS, ironLegs, ItemStack.EMPTY, true, self, "hidden slot, empty cosmetic: the real leggings are not drawn");
            expect(ItemStack.EMPTY, EquipmentSlot.LEGS, ironLegs, goldLegs, true, self, "hidden slot, cosmetic set: nothing is drawn either");
            expect(ironChest, EquipmentSlot.CHEST, ironChest, ItemStack.EMPTY, false, self, "…the other slots are not affected by one slot's switch");
            // Ayarlar: izleyici göstermiyorsa vanilla (gizleme bayrakları da görünümün parçası).
            expect(ironChest, EquipmentSlot.CHEST, ironChest, diamondChest, false, new Viewer(false, true, false), "others' cosmetics hidden: another player looks vanilla");
            expect(diamondChest, EquipmentSlot.CHEST, ironChest, diamondChest, false, new Viewer(true, true, false), "own cosmetics stay shown when only the others' are off");
            expect(ironChest, EquipmentSlot.CHEST, ironChest, diamondChest, false, new Viewer(true, false, true), "own cosmetics off: the real armor shows on me");
            expect(ironLegs, EquipmentSlot.LEGS, ironLegs, goldLegs, true, new Viewer(true, false, true), "own cosmetics off: my hide switch is ignored too (vanilla)");
            expect(ironLegs, EquipmentSlot.LEGS, ironLegs, ItemStack.EMPTY, true, new Viewer(false, true, false), "another player's hide switch is ignored when I do not show others' cosmetics");
            expect(ItemStack.EMPTY, EquipmentSlot.LEGS, ironLegs, ItemStack.EMPTY, true, new Viewer(false, true, true), "another player's hide switch is honoured when I show others' cosmetics");
            // D15: elytra gizlenmez.
            expect(elytra, EquipmentSlot.CHEST, elytra, diamondChest, false, self, "elytra is never replaced by a chest cosmetic");
            expect(elytra, EquipmentSlot.CHEST, elytra, ItemStack.EMPTY, true, self, "elytra is never hidden, not even by the chest slot's hide switch");
            return "16 documented cases hold";
        });
        Checks.register("cosmetics", "vanilla_when_off", source -> {
            int cases = 0;
            for (EquipmentSlot slot : SLOTS) {
                for (ItemStack worn : wornPieces(slot)) {
                    for (ItemStack cosmetic : List.of(ItemStack.EMPTY, piece(slot, true))) {
                        for (boolean hidden : new boolean[] {false, true}) {
                            for (boolean self : new boolean[] {true, false}) {
                                // both cosmetic switches off = what vanilla draws, for everyone, whatever the cosmetic slots and hide switches hold
                                ItemStack got = CosmeticLook.resolve(slot, worn, cosmetic, hidden, new Viewer(self, false, false));
                                Checks.require(ItemStack.matches(got, worn), "settings off must be vanilla on " + slot + (self ? " (self)" : " (other)"));
                                cases++;
                            }
                        }
                    }
                }
            }
            return cases + " inputs: with the cosmetic switches off the look is the worn armor, untouched";
        });
        Checks.register("cosmetics", "never_leaks", source -> {
            int cases = 0;
            for (EquipmentSlot slot : SLOTS) {
                for (ItemStack worn : wornPieces(slot)) {
                    ItemStack cosmetic = piece(slot, true);
                    for (int bits = 0; bits < 16; bits++) {
                        Viewer v = new Viewer((bits & 1) != 0, (bits & 2) != 0, (bits & 4) != 0);
                        boolean hidden = (bits & 8) != 0;
                        ItemStack got = CosmeticLook.resolve(slot, worn, cosmetic, hidden, v);
                        boolean shown = v.self() ? v.showOwn() : v.showOthers();
                        boolean wings = slot == EquipmentSlot.CHEST && CosmeticRules.isWings(worn);
                        boolean cosmeticDrawn = ItemStack.matches(got, cosmetic);
                        Checks.require(cosmeticDrawn == (shown && !hidden && !wings), "cosmetic drawn=" + cosmeticDrawn + " but shown=" + shown + ", hidden=" + hidden + ", wings=" + wings + " (" + slot + ", " + v + ")");
                        if (wings) Checks.require(ItemStack.matches(got, worn), "wings must be drawn as worn");
                        if (!shown) Checks.require(ItemStack.matches(got, worn), "a viewer that does not show cosmetics sees the worn armor (" + v + ")");
                        if (shown && hidden && !wings) Checks.require(got.isEmpty(), "a hidden slot draws nothing");
                        cases++;
                    }
                }
            }
            return cases + " inputs: a cosmetic shows only to a viewer that asked for it, never when hidden, never over wings; hidden = nothing; no switch leaks to a viewer that did not ask";
        });
        Checks.register("cosmetics", "hide_bits", source -> {
            Cosmetics c = Cosmetics.NONE;
            for (EquipmentSlot slot : SLOTS) Checks.require(!c.isHidden(slot), slot + " starts visible");
            c = c.withHidden(EquipmentSlot.LEGS, true);
            Checks.require(c.isHidden(EquipmentSlot.LEGS) && !c.isHidden(EquipmentSlot.HEAD) && !c.isHidden(EquipmentSlot.CHEST) && !c.isHidden(EquipmentSlot.FEET), "only the legs are hidden");
            c = c.with(EquipmentSlot.LEGS, piece(EquipmentSlot.LEGS, true));
            Checks.require(c.isHidden(EquipmentSlot.LEGS) && c.legs().is(Items.DIAMOND_LEGGINGS), "putting a piece in does not touch the switch");
            c = c.withHidden(EquipmentSlot.LEGS, false);
            Checks.require(!c.isHidden(EquipmentSlot.LEGS) && c.hidden() == 0, "switching it back clears the bit");
            Checks.require(c.withHidden(EquipmentSlot.MAINHAND, true) == c && !c.isHidden(EquipmentSlot.MAINHAND), "a non-armor slot has no switch");
            Checks.require(new Cosmetics(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, -1).hidden() == 15, "a save or packet cannot set bits past the four slots");
            return "4 independent hide bits; a piece and its switch are separate; stray bits are masked off";
        });
    }

    private static List<ItemStack> wornPieces(EquipmentSlot slot) {
        return slot == EquipmentSlot.CHEST
                ? List.of(ItemStack.EMPTY, piece(slot, false), new ItemStack(Items.ELYTRA))
                : List.of(ItemStack.EMPTY, piece(slot, false));
    }

    private static void expect(ItemStack want, EquipmentSlot slot, ItemStack worn, ItemStack cosmetic, boolean hidden, Viewer viewer, String why) {
        ItemStack got = CosmeticLook.resolve(slot, worn, cosmetic, hidden, viewer);
        Checks.require(ItemStack.matches(got, want), why + ": drawn " + got + ", expected " + want);
    }
}
