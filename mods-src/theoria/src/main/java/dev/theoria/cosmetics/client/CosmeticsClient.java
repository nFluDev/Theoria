package dev.theoria.cosmetics.client;

import dev.theoria.cosmetics.CosmeticLook;
import dev.theoria.cosmetics.Cosmetics;
import dev.theoria.cosmetics.CosmeticsModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.renderstate.AvatarRenderStateModifier;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;

/**
 * Client wiring of the cosmetic look (ADR-002 c, ADR-007): ONE {@link AvatarRenderStateModifier} that runs for every avatar the game draws — the local player (also in
 * the preview screens), other players, any {@code RemotePlayer} — after vanilla has filled the render state, and swaps the four armor stacks the armor layer draws for what
 * {@link CosmeticLook} says. Real inventory, attributes and enchantments are never touched. No mixin, no layer of our own.
 */
public final class CosmeticsClient {
    /** Dev scenario evidence: avatars handled / armor stacks changed, split by "is the viewer's own player". */
    public static long selfCalls, selfChanged, otherCalls, otherChanged;

    private CosmeticsClient() {}

    public static void init(IEventBus modBus, ModContainer container) {
        CosmeticsConfig.init(container);
        modBus.addListener(CosmeticsClient::register);
    }

    private static void register(RegisterRenderStateModifiersEvent event) {
        event.registerAvatarEntityModifier(new AvatarRenderStateModifier() {
            @Override
            public <T extends Avatar & ClientAvatarEntity> void accept(T avatar, AvatarRenderState state) {
                apply(avatar, state);
            }
        });
    }

    private static void apply(Avatar avatar, AvatarRenderState state) {
        if (!(avatar instanceof Player player)) return; // mannequins and the like have no cosmetic slots
        boolean self = player == Minecraft.getInstance().player;
        Cosmetics cosmetics = CosmeticsModule.of(player);
        CosmeticLook.Viewer viewer = CosmeticsConfig.viewer(self);
        int changed = 0;
        for (EquipmentSlot slot : Cosmetics.SLOTS) {
            var worn = slotStack(state, slot);
            var drawn = CosmeticLook.resolve(slot, worn, cosmetics.get(slot), cosmetics.isHidden(slot), viewer);
            if (drawn != worn) {
                setSlotStack(state, slot, drawn);
                changed++;
            }
        }
        if (self) {
            selfCalls++;
            selfChanged += changed;
        } else {
            otherCalls++;
            otherChanged += changed;
        }
    }

    private static net.minecraft.world.item.ItemStack slotStack(AvatarRenderState state, EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> state.headEquipment;
            case CHEST -> state.chestEquipment;
            case LEGS -> state.legsEquipment;
            default -> state.feetEquipment;
        };
    }

    private static void setSlotStack(AvatarRenderState state, EquipmentSlot slot, net.minecraft.world.item.ItemStack stack) {
        switch (slot) {
            case HEAD -> state.headEquipment = stack;
            case CHEST -> state.chestEquipment = stack;
            case LEGS -> state.legsEquipment = stack;
            default -> state.feetEquipment = stack;
        }
    }
}
