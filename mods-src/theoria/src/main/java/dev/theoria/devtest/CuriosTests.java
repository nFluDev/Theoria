package dev.theoria.devtest;

import com.mojang.serialization.JsonOps;
import dev.theoria.cosmetics.CosmeticRules;
import dev.theoria.cosmetics.CosmeticSlot;
import dev.theoria.cosmetics.Cosmetics;
import dev.theoria.cosmetics.CosmeticsModule;
import dev.theoria.curios.CuriosChecks;
import dev.theoria.curios.CuriosMaxLayout;
import dev.theoria.curios.CuriosMaxMenu;
import dev.theoria.curios.CuriosOpen;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.CuriosSlotTypes;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.ISlotType;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.common.inventory.CurioSlot;
import top.theillusivec4.curios.common.inventory.container.CuriosMenu;
import top.theillusivec4.curios.common.inventory.container.CuriosMenuProvider;

/**
 * Phase 11 GameTests, headless: the cosmetic attachment (codec, stats-neutrality, death), the Curios Max menu (Curios' classic menu + the cosmetic row: slot order, positions, placing and moving items through the real
 * container protocol, the cosmetic slots, the hide switches) and the open path (Curios' own menu is replaced by ours). What a headless test cannot see — the screen, the render modifier on real avatars — is the
 * unattended client scenario ({@code runDevClient -PdevClient=curios}); how it LOOKS is the user's approval.
 *
 * Curios only accepts items tagged {@code #curios:<slot>}; Phase 12 ships those. Until then the tests lend the {@code curios:tag} validator a stand-in (gold nuggets) and always put it back.
 */
final class CuriosTests {
    private static final Identifier TAG = Identifier.fromNamespaceAndPath("curios", "tag");

    private CuriosTests() {}

    static void register(BiConsumer<String, Consumer<GameTestHelper>> add) {
        add.accept("cosmetics_checks", h -> checks(h, "cosmetics", 5));
        add.accept("curios_checks", h -> checks(h, "curios", 3));
        add.accept("cosmetics_codecs", CuriosTests::codecs);
        add.accept("cosmetics_stat_neutral", CuriosTests::statNeutral);
        add.accept("cosmetics_survive_death", CuriosTests::surviveDeath);
        add.accept("cosmetics_sync_to_viewers", CuriosTests::syncToViewers);
        add.accept("cosmetics_hide_switches", CuriosTests::hideSwitches);
        add.accept("curios_menu_layout", CuriosTests::menuLayout);
        add.accept("curios_menu_moves", CuriosTests::menuMoves);
        add.accept("curios_menu_cosmetic_slots", CuriosTests::menuCosmeticSlots);
        add.accept("curios_open_redirect", CuriosTests::openRedirect);
    }

    // ------------------------------------------------------------------------------------------------ helpers
    private static void checks(GameTestHelper h, String area, int atLeast) {
        Dev.Report r = new Dev.Report(area + "_checks");
        ServerPlayer player = FakePlayers.survival(h, "dev-" + area + "-checks");
        List<String> out = new ArrayList<>();
        int passed = DevGameTests.runCommand(h.getLevel().getServer(), player, "theoria test " + area, out);
        FakePlayers.remove(player);
        out.forEach(line -> r.line("out> %s", line));
        r.line("/theoria test %s -> %d checks passed", area, passed);
        r.write();
        h.assertTrue(passed >= atLeast, "/theoria test " + area + " should pass >= " + atLeast + " checks, got " + passed + " " + out);
        h.succeed();
    }

    /** Runs {@code body} while the {@code curios:tag} validator also accepts gold nuggets in every slot, then puts the real validator back. */
    private static void withStandIn(Runnable body) {
        BiPredicate<SlotContext, ItemStack> original = CuriosSlotTypes.getPredicate(TAG);
        CuriosSlotTypes.registerPredicate(TAG, (ctx, stack) -> stack.is(Items.GOLD_NUGGET) || original.test(ctx, stack));
        try {
            body.run();
        } finally {
            CuriosSlotTypes.registerPredicate(TAG, original);
        }
    }

    private static CuriosMaxMenu open(ServerPlayer player) {
        player.openMenu(CuriosMaxMenu.provider());
        if (!(player.containerMenu instanceof CuriosMaxMenu menu)) throw new IllegalStateException("the Max menu did not open: " + player.containerMenu);
        return menu;
    }

    /** Curios' own slot {@code index} of slot type {@code type} in the menu. */
    private static CurioSlot curioSlot(CuriosMaxMenu menu, String type, int index) {
        for (Slot s : menu.slots) {
            if (s instanceof CurioSlot c && c.getId().equals(type) && c.getSlotIndex() == index) return c;
        }
        throw new IllegalStateException("no " + type + " slot " + index);
    }

    private static Slot inventorySlot(CuriosMaxMenu menu, int inventoryIndex) {
        return menu.slots.get(CuriosMaxMenu.INVENTORY_START + inventoryIndex);
    }

    private static void click(CuriosMaxMenu menu, ServerPlayer p, Slot slot, ContainerInput input) {
        menu.clicked(slot.index, 0, input, p);
    }

    private static int count(ServerPlayer p, net.minecraft.world.item.Item item) {
        int n = 0;
        for (ItemStack s : p.getInventory().getNonEquipmentItems()) if (s.is(item)) n += s.getCount();
        return n;
    }

    // ------------------------------------------------------------------------------------------------ cosmetics
    private static void codecs(GameTestHelper h) {
        Dev.Report r = new Dev.Report("cosmetics_codecs");
        RegistryAccess access = h.getLevel().registryAccess();
        Cosmetics c = Cosmetics.NONE.with(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET)).with(EquipmentSlot.FEET, new ItemStack(Items.NETHERITE_BOOTS)).withHidden(EquipmentSlot.LEGS, true);
        var json = Cosmetics.CODEC.codec().encodeStart(RegistryOps.create(JsonOps.INSTANCE, access), c).getOrThrow();
        Cosmetics fromJson = Cosmetics.CODEC.codec().parse(RegistryOps.create(JsonOps.INSTANCE, access), json).getOrThrow();
        h.assertTrue(same(c, fromJson), "JSON round trip: " + json);
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), access);
        Cosmetics.STREAM_CODEC.encode(buf, c);
        h.assertTrue(same(c, Cosmetics.STREAM_CODEC.decode(buf)), "stream round trip");
        h.assertTrue(same(Cosmetics.NONE, Cosmetics.CODEC.codec().parse(RegistryOps.create(JsonOps.INSTANCE, access), new com.google.gson.JsonObject()).getOrThrow()), "an empty save is no cosmetics");
        h.assertTrue(c.with(EquipmentSlot.MAINHAND, new ItemStack(Items.STICK)) == c, "a non-armor slot changes nothing");
        h.assertTrue(fromJson.isHidden(EquipmentSlot.LEGS) && !fromJson.isHidden(EquipmentSlot.HEAD), "the hide switch survives the JSON round trip");
        ItemStack kept = new ItemStack(Items.DIAMOND_HELMET);
        Cosmetics withCopy = Cosmetics.NONE.with(EquipmentSlot.HEAD, kept);
        kept.setCount(7);
        h.assertTrue(withCopy.head().getCount() == 1, "the attachment never shares a stack with the caller");
        r.line("json=%s", json);
        r.write();
        h.succeed();
    }

    private static boolean same(Cosmetics a, Cosmetics b) {
        for (EquipmentSlot slot : Cosmetics.SLOTS) if (!ItemStack.matches(a.get(slot), b.get(slot))) return false;
        return a.hidden() == b.hidden();
    }

    private static void statNeutral(GameTestHelper h) {
        Dev.Report r = new Dev.Report("cosmetics_stat_neutral");
        ServerPlayer p = FakePlayers.survival(h, "dev-cosmetic-stats");
        try {
            // A mock player is not equipment-ticked by the harness (its connection is not in the server's connection list, so detectEquipmentUpdates never runs): apply the worn iron
            // chestplate's modifiers the way vanilla does (the item's own modifier list, transient) and judge the cosmetics against that.
            ItemStack iron = new ItemStack(Items.IRON_CHESTPLATE);
            p.setItemSlot(EquipmentSlot.CHEST, iron);
            iron.forEachModifier(EquipmentSlot.CHEST, (attribute, modifier) -> p.getAttribute(attribute).addTransientModifier(modifier));
            double armor = p.getAttributeValue(Attributes.ARMOR);
            double toughness = p.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
            double health = p.getMaxHealth();
            int armorValue = p.getArmorValue();
            var armorModifiers = new ArrayList<>(p.getAttribute(Attributes.ARMOR).getModifiers());
            h.assertTrue(armor > 0 && armorValue > 0, "the iron chestplate's modifiers must give armor, got " + armor);
            for (EquipmentSlot slot : Cosmetics.SLOTS) {
                ItemStack diamond = switch (slot) {
                    case HEAD -> new ItemStack(Items.DIAMOND_HELMET);
                    case CHEST -> new ItemStack(Items.DIAMOND_CHESTPLATE);
                    case LEGS -> new ItemStack(Items.DIAMOND_LEGGINGS);
                    default -> new ItemStack(Items.DIAMOND_BOOTS);
                };
                h.assertTrue(CosmeticsModule.set(p, slot, diamond), "a diamond piece must be accepted in " + slot);
            }
            r.line("armor %.4f / toughness %.4f / max health %.4f / armor value %d — before cosmetics; after four diamond cosmetics: %.4f / %.4f / %.4f / %d", armor, toughness, health,
                    armorValue, p.getAttributeValue(Attributes.ARMOR), p.getAttributeValue(Attributes.ARMOR_TOUGHNESS), (double) p.getMaxHealth(), p.getArmorValue());
            h.assertTrue(p.getAttributeValue(Attributes.ARMOR) == armor && p.getAttributeValue(Attributes.ARMOR_TOUGHNESS) == toughness, "cosmetics must not change armor or toughness");
            h.assertTrue(p.getMaxHealth() == health && p.getArmorValue() == armorValue, "cosmetics must not change max health or the armor value");
            h.assertTrue(new ArrayList<>(p.getAttribute(Attributes.ARMOR).getModifiers()).equals(armorModifiers), "not a single armor modifier was added or removed");
            h.assertTrue(p.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE) && p.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "the worn equipment stays exactly what it was");
            h.assertTrue(CosmeticsModule.of(p).chest().is(Items.DIAMOND_CHESTPLATE), "the attachment holds the cosmetic");
            h.assertTrue(!CosmeticsModule.set(p, EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_CHESTPLATE)) && CosmeticsModule.of(p).head().is(Items.DIAMOND_HELMET),
                    "a chestplate is refused as a helmet look and changes nothing");
            h.assertTrue(CosmeticsModule.set(p, EquipmentSlot.HEAD, ItemStack.EMPTY) && CosmeticsModule.of(p).head().isEmpty(), "an empty stack clears the slot");
            // the cosmetic is not equipment: with no real armor and no modifiers, whatever the cosmetics are, there is no armor
            p.getAttribute(Attributes.ARMOR).removeModifiers();
            p.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
            h.assertTrue(p.getAttributeValue(Attributes.ARMOR) == 0.0 && p.getArmorValue() == 0, "no real armor, no armor — whatever the cosmetics are: " + p.getAttributeValue(Attributes.ARMOR));
            r.line("real armor taken off, cosmetics still on: armor %.4f", p.getAttributeValue(Attributes.ARMOR));
        } finally {
            FakePlayers.remove(p);
        }
        r.write();
        h.succeed();
    }

    /**
     * Multiplayer visibility (docs/04 §6, ADR-007 K3): a second player who starts seeing the owner is sent the owner's cosmetics — the very packet NeoForge builds when one player
     * begins tracking another ({@code AttachmentSync.syncInitialEntityAttachments}), captured and decoded here instead of guessed from the attachment's configuration. (Later
     * changes travel by {@code syncEntityUpdate}, to everyone watching the owner's chunk plus the owner, through the same {@code sendToPlayer = true} handler [source]; a mock player
     * is never in a chunk's watcher list, so that half cannot be exercised headlessly.)
     */
    private static void syncToViewers(GameTestHelper h) {
        Dev.Report r = new Dev.Report("cosmetics_sync_to_viewers");
        ServerPlayer owner = FakePlayers.survival(h, "dev-cosmetic-owner");
        ServerPlayer viewer = FakePlayers.survival(h, "dev-cosmetic-viewer");
        try {
            CosmeticsModule.set(owner, EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
            List<net.minecraft.network.protocol.Packet<? super net.minecraft.network.protocol.game.ClientGamePacketListener>> sent = new ArrayList<>();
            net.neoforged.neoforge.attachment.AttachmentSync.syncInitialEntityAttachments(owner, viewer, sent::add);
            net.neoforged.neoforge.network.payload.SyncAttachmentsPayload payload = null;
            for (Object packet : sent) {
                if (packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket custom && custom.payload() instanceof net.neoforged.neoforge.network.payload.SyncAttachmentsPayload sync
                        && sync.target() instanceof net.neoforged.neoforge.network.payload.SyncAttachmentsPayload.EntityTarget(int id) && id == owner.getId()) payload = sync;
            }
            r.line("owner id %d, viewer id %d: %d packet(s) sent to the viewer when it starts seeing the owner; the owner's sync carries %s", owner.getId(), viewer.getId(), sent.size(),
                    payload == null ? "nothing" : payload.types().stream().map(t -> net.neoforged.neoforge.registries.NeoForgeRegistries.ATTACHMENT_TYPES.getKey(t).toString()).toList());
            h.assertTrue(payload != null, "a player who starts seeing the owner must be sent the owner's synced attachments");
            h.assertTrue(payload.types().contains(CosmeticsModule.COSMETICS.get()), "…and the cosmetics are among them — otherwise nobody else could ever see them");
            r.line("payload bytes %d, %d synced types", payload.syncPayload().length, payload.types().size());
            r.write();
        } finally {
            FakePlayers.remove(owner);
            FakePlayers.remove(viewer);
        }
        h.succeed();
    }

    /** The hide dot's server side: only the sender's own slot 0..3 flips; stray values are ignored; a switch and a piece are independent. */
    private static void hideSwitches(GameTestHelper h) {
        Dev.Report r = new Dev.Report("cosmetics_hide_switches");
        ServerPlayer p = FakePlayers.survival(h, "dev-cosmetic-hide");
        try {
            h.assertTrue(CosmeticsModule.of(p).hidden() == 0, "nothing is hidden at first");
            CosmeticsModule.onToggleRequest(p, 2); // legs
            h.assertTrue(CosmeticsModule.of(p).isHidden(EquipmentSlot.LEGS) && CosmeticsModule.of(p).hidden() == 0b0100, "slot 2 is the legs: bit " + CosmeticsModule.of(p).hidden());
            for (int stray : new int[] {-1, 4, 5, 63, Integer.MAX_VALUE, Integer.MIN_VALUE}) CosmeticsModule.onToggleRequest(p, stray);
            h.assertTrue(CosmeticsModule.of(p).hidden() == 0b0100, "a request outside 0..3 changes nothing: " + CosmeticsModule.of(p).hidden());
            CosmeticsModule.set(p, EquipmentSlot.LEGS, new ItemStack(Items.GOLDEN_LEGGINGS));
            h.assertTrue(CosmeticsModule.of(p).isHidden(EquipmentSlot.LEGS) && CosmeticsModule.of(p).legs().is(Items.GOLDEN_LEGGINGS), "a piece and its hide switch are independent");
            CosmeticsModule.onToggleRequest(p, 2);
            h.assertTrue(CosmeticsModule.of(p).hidden() == 0 && CosmeticsModule.of(p).legs().is(Items.GOLDEN_LEGGINGS), "a second request switches it back; the piece stays");
            for (int i = 0; i < 4; i++) CosmeticsModule.onToggleRequest(p, i);
            h.assertTrue(CosmeticsModule.of(p).hidden() == 0b1111, "all four can be hidden");
            // the payload's codec carries the index and nothing else
            io.netty.buffer.ByteBuf buf = Unpooled.buffer();
            dev.theoria.cosmetics.ToggleCosmeticHiddenPayload.STREAM_CODEC.encode(buf, new dev.theoria.cosmetics.ToggleCosmeticHiddenPayload(3));
            h.assertTrue(dev.theoria.cosmetics.ToggleCosmeticHiddenPayload.STREAM_CODEC.decode(buf).slot() == 3, "payload round trip");
            r.line("switch bits: 0 → 0b0100 (legs) → stray requests ignored → piece independent → 0 → 0b1111");
            r.write();
        } finally {
            FakePlayers.remove(p);
        }
        h.succeed();
    }

    private static void surviveDeath(GameTestHelper h) {
        Dev.Report r = new Dev.Report("cosmetics_survive_death");
        MinecraftServer server = h.getLevel().getServer();
        ServerPlayer p = FakePlayers.survival(h, "dev-cosmetic-death");
        try {
            CosmeticsModule.set(p, EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
            CosmeticsModule.set(p, EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
            CosmeticsModule.toggleHidden(p, EquipmentSlot.LEGS);
            ServerPlayer afterDeath = server.getPlayerList().respawn(p, false, Entity.RemovalReason.KILLED);
            r.line("after death + respawn: head %s, feet %s", CosmeticsModule.of(afterDeath).head(), CosmeticsModule.of(afterDeath).feet());
            h.assertTrue(CosmeticsModule.of(afterDeath).head().is(Items.DIAMOND_HELMET) && CosmeticsModule.of(afterDeath).feet().is(Items.IRON_BOOTS), "cosmetics are for life: they survive death");
            h.assertTrue(CosmeticsModule.of(afterDeath).chest().isEmpty() && CosmeticsModule.of(afterDeath).legs().isEmpty(), "empty slots stay empty");
            h.assertTrue(CosmeticsModule.of(afterDeath).isHidden(EquipmentSlot.LEGS) && !CosmeticsModule.of(afterDeath).isHidden(EquipmentSlot.HEAD), "the hide switches survive death too");
            ServerPlayer afterReturn = server.getPlayerList().respawn(afterDeath, true, Entity.RemovalReason.CHANGED_DIMENSION);
            h.assertTrue(CosmeticsModule.of(afterReturn).head().is(Items.DIAMOND_HELMET), "and survive an End-style return");
            FakePlayers.remove(afterReturn);
        } finally {
            if (p.isAlive()) FakePlayers.remove(p);
        }
        r.write();
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ the Max menu
    private static void menuLayout(GameTestHelper h) {
        Dev.Report r = new Dev.Report("curios_menu_layout");
        ServerPlayer p = FakePlayers.survival(h, "dev-curios-layout");
        try {
            ICuriosItemHandler handler = CuriosApi.getCuriosInventoryOrNull(p);
            h.assertTrue(handler != null, "a player has a Curios inventory");
            CuriosMaxMenu menu = open(p);
            h.assertTrue(menu instanceof CuriosMenu && ((Object) menu).getClass() != CuriosMenu.class, "the Max menu IS a Curios menu (Curios' own screen, packets and quick-move code work on it) but not the plain class");
            int slots = menu.curioSlotCount();
            h.assertTrue(slots == handler.getVisibleSlots() && slots >= CuriosChecks.MIN_SLOTS, "Curios Max shows every Curios slot (" + slots + " of " + handler.getVisibleSlots() + "), at least " + CuriosChecks.MIN_SLOTS);
            // Curios' slot order, which its own quick-move code hard-codes: 0 result · 1-4 crafting · 5-8 armor · 9-44 inventory · 45 offhand · 46… Curios · cosmetic last
            h.assertTrue(menu.slots.size() == CuriosMaxMenu.CURIO_START + slots + 4, "46 fixed + " + slots + " Curios + 4 cosmetic slots, got " + menu.slots.size());
            h.assertTrue(menu.slots.get(0) == menu.getResultSlot() && menu.slots.get(5) instanceof net.minecraft.world.inventory.ArmorSlot && menu.slots.get(8) instanceof net.minecraft.world.inventory.ArmorSlot, "result, crafting, armor");
            for (int i = CuriosMaxMenu.CURIO_START; i < CuriosMaxMenu.CURIO_START + slots; i++) h.assertTrue(menu.slots.get(i) instanceof CurioSlot, "slot " + i + " must be a Curios slot");
            for (int i = 0; i < 4; i++) {
                Slot c = menu.slots.get(CuriosMaxMenu.CURIO_START + slots + i);
                h.assertTrue(c instanceof CosmeticSlot cs && cs.equipment() == Cosmetics.SLOTS.get(i), "the last four slots are the cosmetic head/chest/legs/feet slots in order");
            }
            // the panel: classic columns rule (≥ 5 here), ONE page, the cosmetic row on top
            int columns = CuriosMaxLayout.columns(slots);
            h.assertTrue(menu.grid.size() == columns && menu.panelWidth == CuriosMaxLayout.panelWidth(columns), "panel: " + menu.grid.size() + " columns / width " + menu.panelWidth + ", expected " + columns + " / " + CuriosMaxLayout.panelWidth(columns));
            h.assertTrue(menu.totalPages == 1 && !menu.hasCosmetics, "one page, no Curios cosmetic stacks");
            h.assertTrue(menu.grid.get(0) == CuriosMaxLayout.rows(slots), "the first column is as tall as the panel: " + menu.grid.get(0) + " rows");
            java.util.Set<Long> cells = new java.util.HashSet<>();
            for (int i = 0; i < slots; i++) {
                Slot s = menu.slots.get(CuriosMaxMenu.CURIO_START + i);
                h.assertTrue(s.x == CuriosMaxLayout.cellX(i % columns, menu.panelWidth) && s.y == CuriosMaxLayout.rowY(1 + i / columns), "Curios slot " + i + " sits at the layout cell: " + s.x + "," + s.y);
                h.assertTrue(cells.add(((long) s.x << 32) | s.y), "two Curios slots share a cell");
            }
            for (int i = 0; i < 4; i++) {
                Slot c = menu.slots.get(CuriosMaxMenu.CURIO_START + slots + i);
                h.assertTrue(c.x == CuriosMaxLayout.cellX(i, menu.panelWidth) && c.y == CuriosMaxLayout.rowY(0), "cosmetic slot " + i + " is in the top row: " + c.x + "," + c.y);
                h.assertTrue(cells.add(((long) c.x << 32) | c.y), "a cosmetic slot shares a cell with a Curios slot");
            }
            h.assertTrue(cells.add(((long) CuriosMaxLayout.cellX(CuriosMaxLayout.WINGS_CELL, menu.panelWidth) << 32) | CuriosMaxLayout.rowY(0)), "the Wings cell (5th of the top row) must be free of slots");
            r.line("%d Curios slots → %d columns × %d rows incl. the cosmetic row; panel width %d; slots 46..%d Curios, last 4 cosmetic", slots, columns, CuriosMaxLayout.rows(slots), menu.panelWidth, 45 + slots);
        } finally {
            FakePlayers.remove(p);
        }
        r.write();
        h.succeed();
    }

    private static void menuMoves(GameTestHelper h) {
        Dev.Report r = new Dev.Report("curios_menu_moves");
        ServerPlayer p = FakePlayers.survival(h, "dev-curios-moves");
        try {
            withStandIn(() -> {
                CuriosMaxMenu menu = open(p);
                ICuriosItemHandler handler = CuriosApi.getCuriosInventoryOrNull(p);
                CurioSlot ring0 = curioSlot(menu, "ring", 0);
                CurioSlot ring1 = curioSlot(menu, "ring", 1);
                // 1. carry a valid item to a ring slot: it lands in the REAL Curios inventory
                menu.setCarried(new ItemStack(Items.GOLD_NUGGET));
                click(menu, p, ring0, ContainerInput.PICKUP);
                ItemStack real = handler.getStacksHandler("ring").orElseThrow().getStacks().getStackInSlot(0);
                h.assertTrue(real.is(Items.GOLD_NUGGET) && menu.getCarried().isEmpty(), "the nugget is in Curios' ring slot 0 and the cursor is empty: " + real + " / " + menu.getCarried());
                // 2. an item that is not a ring is refused and stays on the cursor
                menu.setCarried(new ItemStack(Items.STICK));
                click(menu, p, ring1, ContainerInput.PICKUP);
                h.assertTrue(ring1.getItem().isEmpty() && menu.getCarried().is(Items.STICK), "a stick is not a ring: refused, still on the cursor");
                menu.setCarried(ItemStack.EMPTY);
                // 3. shift-click from the inventory goes to Curios (Curios' own quick-move code), never into a cosmetic slot
                p.getInventory().setItem(9, new ItemStack(Items.GOLD_NUGGET, 3));
                click(menu, p, inventorySlot(menu, 0), ContainerInput.QUICK_MOVE);
                int inCurios = 0;
                for (var stacks : handler.getCurios().values()) for (int i = 0; i < stacks.getSlots(); i++) inCurios += stacks.getStacks().getStackInSlot(i).is(Items.GOLD_NUGGET) ? stacks.getStacks().getStackInSlot(i).getCount() : 0;
                h.assertTrue(inCurios == 4 && count(p, Items.GOLD_NUGGET) == 0, "shift-click moved all 3 nuggets into Curios (4 in total, 0 left in the inventory): " + inCurios + " / " + count(p, Items.GOLD_NUGGET));
                h.assertTrue(CosmeticsModule.of(p).isEmpty(), "…and none of them into a cosmetic slot");
                // 4. shift-click from a Curios slot goes back to the inventory
                click(menu, p, ring0, ContainerInput.QUICK_MOVE);
                h.assertTrue(ring0.getItem().isEmpty() && count(p, Items.GOLD_NUGGET) >= 1, "shift-click out of a ring slot returns the stack to the inventory");
                r.line("placed, refused, shift-in (%d in Curios), shift-out (%d back in the inventory)", inCurios, count(p, Items.GOLD_NUGGET));
            });
        } finally {
            FakePlayers.remove(p);
        }
        r.write();
        h.succeed();
    }

    private static void menuCosmeticSlots(GameTestHelper h) {
        Dev.Report r = new Dev.Report("curios_menu_cosmetic_slots");
        ServerPlayer p = FakePlayers.survival(h, "dev-curios-cosmetic");
        try {
            CuriosMaxMenu menu = open(p);
            Slot chest = menu.cosmeticSlot(EquipmentSlot.CHEST);
            Slot head = menu.cosmeticSlot(EquipmentSlot.HEAD);
            double armor = p.getAttributeValue(Attributes.ARMOR);
            menu.setCarried(new ItemStack(Items.DIAMOND_CHESTPLATE));
            click(menu, p, chest, ContainerInput.PICKUP);
            h.assertTrue(CosmeticsModule.of(p).chest().is(Items.DIAMOND_CHESTPLATE) && menu.getCarried().isEmpty(), "the chestplate went into the cosmetic slot");
            h.assertTrue(p.getItemBySlot(EquipmentSlot.CHEST).isEmpty() && p.getAttributeValue(Attributes.ARMOR) == armor, "…and NOT onto the body: no equipment, no armor");
            menu.setCarried(new ItemStack(Items.DIAMOND_HELMET));
            click(menu, p, chest, ContainerInput.PICKUP);
            h.assertTrue(CosmeticsModule.of(p).chest().is(Items.DIAMOND_CHESTPLATE) && menu.getCarried().is(Items.DIAMOND_HELMET), "a helmet does not fit the chest look: refused, nothing swapped");
            menu.setCarried(new ItemStack(Items.IRON_CHESTPLATE));
            click(menu, p, chest, ContainerInput.PICKUP);
            h.assertTrue(CosmeticsModule.of(p).chest().is(Items.IRON_CHESTPLATE) && menu.getCarried().is(Items.DIAMOND_CHESTPLATE), "clicking an occupied slot swaps with the cursor");
            menu.setCarried(new ItemStack(Items.STICK));
            click(menu, p, head, ContainerInput.PICKUP);
            h.assertTrue(CosmeticsModule.of(p).head().isEmpty() && menu.getCarried().is(Items.STICK), "a stick is no helmet look");
            menu.setCarried(ItemStack.EMPTY);
            click(menu, p, chest, ContainerInput.QUICK_MOVE);
            h.assertTrue(CosmeticsModule.of(p).chest().isEmpty() && count(p, Items.IRON_CHESTPLATE) == 1, "shift-click takes the piece out into the inventory");
            // from the inventory a shift-click puts armor on the BODY (vanilla habit), never into the cosmetic slot — the cosmetic move is the screen's Ctrl + click
            click(menu, p, menu.slots.stream().filter(s -> s.index >= CuriosMaxMenu.INVENTORY_START && s.index < CuriosMaxMenu.INVENTORY_END && s.getItem().is(Items.IRON_CHESTPLATE)).findFirst().orElseThrow(), ContainerInput.QUICK_MOVE);
            h.assertTrue(p.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE) && CosmeticsModule.of(p).chest().isEmpty(), "shift-click from the inventory wears armor for real; the look is a deliberate Ctrl + click / drag");
            // the screen's Ctrl + click is three vanilla clicks: pick up, put into the cosmetic slot, put the swapped-out piece back — the same on the server
            p.getInventory().setItem(10, new ItemStack(Items.GOLDEN_CHESTPLATE));
            Slot source = inventorySlot(menu, 1);
            click(menu, p, source, ContainerInput.PICKUP);
            click(menu, p, chest, ContainerInput.PICKUP);
            h.assertTrue(CosmeticsModule.of(p).chest().is(Items.GOLDEN_CHESTPLATE) && menu.getCarried().isEmpty() && source.getItem().isEmpty(), "Ctrl + click, empty cosmetic slot: pick up, drop in");
            p.getInventory().setItem(10, new ItemStack(Items.DIAMOND_CHESTPLATE));
            click(menu, p, source, ContainerInput.PICKUP);
            click(menu, p, chest, ContainerInput.PICKUP);
            click(menu, p, source, ContainerInput.PICKUP);
            h.assertTrue(CosmeticsModule.of(p).chest().is(Items.DIAMOND_CHESTPLATE) && source.getItem().is(Items.GOLDEN_CHESTPLATE) && menu.getCarried().isEmpty(), "Ctrl + click, occupied: the old piece comes back to the clicked slot");
            h.assertTrue(p.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE), "the worn iron chestplate was never touched");
            r.line("cosmetic slots: place, wrong piece refused, swap, stick refused, shift-out, shift-in wears for real, Ctrl+click sequences");
        } finally {
            FakePlayers.remove(p);
        }
        r.write();
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ opening
    private static void openRedirect(GameTestHelper h) {
        Dev.Report r = new Dev.Report("curios_open_redirect");
        ServerPlayer p = FakePlayers.survival(h, "dev-curios-open");
        try {
            // the exact steps of Curios' own "open" handler (key / inventory button): carry aside, open ITS menu, carry back into whatever menu is open now
            p.inventoryMenu.setCarried(new ItemStack(Items.GOLD_NUGGET, 2));
            ItemStack carried = p.containerMenu.getCarried();
            p.containerMenu.setCarried(ItemStack.EMPTY);
            p.openMenu(new CuriosMenuProvider());
            h.assertTrue(p.containerMenu instanceof CuriosMaxMenu, "Curios' menu must be replaced by Curios Max, got " + p.containerMenu.getClass().getSimpleName());
            h.assertTrue(p.containerMenu.getType() == CuriosMaxMenu.MENU.get(), "…and it reports OUR menu type to the client (so the client builds our menu and screen), not Curios'");
            p.containerMenu.setCarried(carried);
            h.assertTrue(p.containerMenu.getCarried().is(Items.GOLD_NUGGET) && p.containerMenu.getCarried().getCount() == 2, "the cursor stack arrives in the new menu");
            int id = p.containerMenu.containerId;
            h.assertTrue(id > 0, "a real container id: " + id);
            CuriosOpen.open(p);
            h.assertTrue(p.containerMenu.containerId == id, "opening Curios Max while it is open does nothing");
            p.closeContainer();
            h.assertTrue(p.containerMenu == p.inventoryMenu && count(p, Items.GOLD_NUGGET) == 2, "closing hands the cursor stack back: nothing is lost (" + count(p, Items.GOLD_NUGGET) + ")");
            // the direct request (the open payload's handler)
            p.inventoryMenu.setCarried(new ItemStack(Items.GOLD_NUGGET, 5));
            CuriosOpen.open(p);
            h.assertTrue(p.containerMenu instanceof CuriosMaxMenu && p.containerMenu.getCarried().getCount() == 5, "the direct open carries the cursor stack over too");
            p.closeContainer();
            h.assertTrue(count(p, Items.GOLD_NUGGET) == 7, "all 7 nuggets are back in the inventory: " + count(p, Items.GOLD_NUGGET));
            h.assertTrue(CosmeticRules.isWings(new ItemStack(Items.ELYTRA)), "sanity: the elytra rule the screen uses");
            r.line("Curios' menu → Curios Max (container id %d), cursor stack carried both ways, closing loses nothing", id);
        } finally {
            FakePlayers.remove(p);
        }
        r.write();
        h.succeed();
    }
}
