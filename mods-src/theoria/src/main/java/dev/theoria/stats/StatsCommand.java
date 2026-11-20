package dev.theoria.stats;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /theoria stats} (everyone): your level, lifetime XP and points. Game masters can also set the lifetime XP or the level and reset the allocation —
 * for testing and map making, like {@code /theoria tier}. All of them act on the command's own player.
 */
public final class StatsCommand {
    private StatsCommand() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> event.getDispatcher().register(Commands.literal("theoria")
                .then(Commands.literal("stats")
                        .executes(StatsCommand::show)
                        .then(Commands.literal("xp").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .then(Commands.literal("set").then(Commands.argument("amount", LongArgumentType.longArg(0))
                                        .executes(context -> setXp(context, LongArgumentType.getLong(context, "amount"), false))))
                                .then(Commands.literal("add").then(Commands.argument("amount", LongArgumentType.longArg(0))
                                        .executes(context -> setXp(context, LongArgumentType.getLong(context, "amount"), true)))))
                        .then(Commands.literal("level").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .then(Commands.argument("level", IntegerArgumentType.integer(1))
                                        .executes(context -> setXp(context, LevelCurve.totalXp(IntegerArgumentType.getInteger(context, "level")), false))))
                        .then(Commands.literal("reset").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(StatsCommand::reset)))));
    }

    private static int show(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        PlayerStats stats = StatsModule.of(player);
        context.getSource().sendSuccess(() -> Component.translatable("commands.theoria.stats.show", stats.level(), stats.xp(), stats.available(), stats.spent()), false);
        return stats.level();
    }

    private static int setXp(CommandContext<CommandSourceStack> context, long amount, boolean add) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        StatsServer.setXp(player, add ? StatsModule.of(player).addXp(amount).xp() : amount);
        PlayerStats stats = StatsModule.of(player);
        context.getSource().sendSuccess(() -> Component.translatable("commands.theoria.stats.xp", stats.xp(), stats.level()), true);
        return stats.level();
    }

    private static int reset(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        int returned = StatsServer.reset(player);
        context.getSource().sendSuccess(() -> Component.translatable("commands.theoria.stats.reset", returned), true);
        return returned;
    }
}
