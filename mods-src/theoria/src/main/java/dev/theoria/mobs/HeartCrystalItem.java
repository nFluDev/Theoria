package dev.theoria.mobs;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Boss reward: right-click for a permanent max-health bonus ({@link HeartCrystals}). */
public final class HeartCrystalItem extends Item {
    public HeartCrystalItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
        if (!HeartCrystals.consume(serverPlayer)) {
            serverPlayer.sendSystemMessage(Component.translatable("theoria.mob.heart.full"), true);
            return InteractionResult.FAIL;
        }
        stack.consume(1, player);
        MobData.Balance b = MobData.balance();
        serverPlayer.sendSystemMessage(Component.translatable("theoria.mob.heart.used", b.heartHp(), HeartCrystals.bonus(serverPlayer)), true);
        return InteractionResult.SUCCESS;
    }
}
