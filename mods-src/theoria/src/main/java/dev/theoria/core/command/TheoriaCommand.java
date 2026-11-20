package dev.theoria.core.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import dev.theoria.Theoria;
import dev.theoria.core.TheoriaConfig;
import dev.theoria.core.check.Checks;
import dev.theoria.core.check.CoreChecks;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** {@code /theoria version} (everyone) and {@code /theoria test [area [check]]} (game masters). */
public final class TheoriaCommand {
    private static final DynamicCommandExceptionType UNKNOWN_AREA =
            new DynamicCommandExceptionType(area -> Component.translatable("commands.theoria.test.unknown_area", area));
    private static final Dynamic2CommandExceptionType UNKNOWN_CHECK =
            new Dynamic2CommandExceptionType((check, area) -> Component.translatable("commands.theoria.test.unknown_check", check, area));
    private static final Dynamic2CommandExceptionType FAILED =
            new Dynamic2CommandExceptionType((failed, total) -> Component.translatable("commands.theoria.test.failed", failed, total));

    private TheoriaCommand() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener(TheoriaCommand::register);
    }

    private static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("theoria")
                .then(Commands.literal("version").executes(TheoriaCommand::version))
                .then(Commands.literal("test")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(context -> test(context, null, null))
                        .then(Commands.argument("area", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(Checks.areas(), builder))
                                .executes(context -> test(context, StringArgumentType.getString(context, "area"), null))
                                .then(Commands.argument("check", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(Checks.names(StringArgumentType.getString(context, "area")), builder))
                                        .executes(context -> test(context, StringArgumentType.getString(context, "area"), StringArgumentType.getString(context, "check")))))));
    }

    private static int version(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.translatable("commands.theoria.version",
                Theoria.version(), CoreChecks.minecraftVersion(), CoreChecks.neoForgeVersion()), false);
        return 1;
    }

    /** Result = number of passed checks; fails the command if any check failed. */
    private static int test(CommandContext<CommandSourceStack> context, String area, String check) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        if (area != null && !Checks.areas().contains(area)) throw UNKNOWN_AREA.create(area);
        if (check != null && !Checks.names(area).contains(check)) throw UNKNOWN_CHECK.create(check, area);
        List<Checks.Result> results = Checks.run(area, check, source);
        int failed = 0;
        for (Checks.Result result : results) {
            if (!result.ok()) {
                failed++;
                source.sendSuccess(() -> Component.translatable("commands.theoria.test.fail", result.name(), result.detail())
                        .withStyle(ChatFormatting.RED), false);
            } else if (TheoriaConfig.verbose()) {
                source.sendSuccess(() -> Component.translatable("commands.theoria.test.pass", result.name(), result.detail())
                        .withStyle(ChatFormatting.GRAY), false);
            }
        }
        if (failed > 0) throw FAILED.create(failed, results.size());
        int passed = results.size();
        source.sendSuccess(() -> Component.translatable("commands.theoria.test.ok", passed), false);
        return passed;
    }
}
