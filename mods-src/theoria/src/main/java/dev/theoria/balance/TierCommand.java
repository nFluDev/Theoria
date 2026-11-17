package dev.theoria.balance;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /theoria tier <targets> <tier>} (game masters): puts living non-player entities in a mob tier, 0 = back to vanilla.
 * Until the spawn-zone rule exists (Phase 8/9) this is the only way a mob gets a tier — for testing and map making.
 */
public final class TierCommand {
    private static final Dynamic2CommandExceptionType UNKNOWN_TIER =
            new Dynamic2CommandExceptionType((tier, last) -> Component.translatable("commands.theoria.tier.unknown", tier, last));

    private TierCommand() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> event.getDispatcher().register(Commands.literal("theoria")
                .then(Commands.literal("tier")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .then(Commands.argument("tier", IntegerArgumentType.integer(0))
                                        .executes(TierCommand::run))))));
    }

    private static int run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int tier = IntegerArgumentType.getInteger(context, "tier");
        if (!TierRegistry.loaded() || tier >= TierRegistry.tierCount()) {
            throw UNKNOWN_TIER.create(tier, TierRegistry.loaded() ? TierRegistry.tierCount() - 1 : "?");
        }
        int changed = 0;
        for (Entity entity : EntityArgument.getEntities(context, "targets")) {
            if (entity instanceof LivingEntity mob && !(mob instanceof Player)) {
                MobScaling.assign(mob, tier);
                changed++;
            }
        }
        int count = changed;
        if (count == 0) {
            context.getSource().sendFailure(Component.translatable("commands.theoria.tier.none"));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable("commands.theoria.tier.set", count, tier), true);
        return count;
    }
}
