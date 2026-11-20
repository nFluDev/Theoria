package dev.theoria.stats;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Orb of Oblivion (Q6, boss reward): right-click returns every allocated skill point; the level and lifetime XP stay. Not used up when there is nothing to return. */
public final class OblivionOrbItem extends Item {
    public OblivionOrbItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
        int returned = StatsServer.reset(serverPlayer);
        if (returned == 0) {
            serverPlayer.sendSystemMessage(Component.translatable("theoria.stats.orb.empty").withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.FAIL;
        }
        stack.consume(1, player);
        level.playSound(null, serverPlayer, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.6F);
        serverPlayer.sendSystemMessage(Component.translatable("theoria.stats.orb.used", returned).withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return InteractionResult.SUCCESS;
    }
}
