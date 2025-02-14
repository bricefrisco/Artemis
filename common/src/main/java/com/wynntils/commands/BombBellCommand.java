/*
 * Copyright © Wynntils 2022.
 * This file is released under AGPLv3. See LICENSE for full license details.
 */
package com.wynntils.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.wynntils.core.commands.CommandBase;
import com.wynntils.wynn.model.BombBellModel;
import com.wynntils.wynn.objects.BombInfo;
import com.wynntils.wynn.objects.BombType;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;

public class BombBellCommand extends CommandBase {
    private final SuggestionProvider<CommandSourceStack> bombTypeSuggestionProvider = (context, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(BombType.values()).map(Enum::name), builder);

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> getBaseCommandBuilder() {
        return Commands.literal("bombbell")
                .then(Commands.literal("list").executes(this::listBombs))
                .then(Commands.literal("get")
                        .then(Commands.argument("bombType", StringArgumentType.word())
                                .suggests(bombTypeSuggestionProvider)
                                .executes(this::getBombTypeList)));
    }

    private int getBombTypeList(CommandContext<CommandSourceStack> context) {
        BombType bombType;

        try {
            bombType = BombType.valueOf(context.getArgument("bombType", String.class));
        } catch (IllegalArgumentException e) {
            context.getSource().sendFailure(new TextComponent("Invalid bomb type").withStyle(ChatFormatting.RED));
            return 0;
        }

        Set<BombInfo> bombBells = BombBellModel.getBombBells().stream()
                .filter(bombInfo -> bombInfo.bomb() == bombType)
                .collect(Collectors.toSet());

        MutableComponent component = getBombListComponent(bombBells);

        context.getSource().sendSuccess(component, false);

        return 1;
    }

    private int listBombs(CommandContext<CommandSourceStack> context) {
        Set<BombInfo> bombBells = BombBellModel.getBombBells();

        MutableComponent component = getBombListComponent(bombBells);

        context.getSource().sendSuccess(component, false);

        return 1;
    }

    private static MutableComponent getBombListComponent(Set<BombInfo> bombBells) {
        MutableComponent response = new TextComponent("Bomb Bells: ").withStyle(ChatFormatting.GOLD);

        if (bombBells.isEmpty()) {
            response.append(new TextComponent(
                                    "There are no active bombs at the moment! This might be because you do not have the ")
                            .withStyle(ChatFormatting.RED))
                    .append(new TextComponent("CHAMPION").withStyle(ChatFormatting.YELLOW))
                    .append(new TextComponent(" rank on Wynncraft, which is necessary to use this feature.")
                            .withStyle(ChatFormatting.RED));
            return response;
        }

        for (BombInfo bomb : bombBells.stream()
                .sorted(Comparator.comparing(BombInfo::bomb)
                        .reversed()
                        .thenComparing(BombInfo::startTime)
                        .reversed())
                .toList()) {
            response.append(new TextComponent("\n" + bomb.bomb().getName())
                            .withStyle(ChatFormatting.WHITE)
                            .append(new TextComponent(" on ").withStyle(ChatFormatting.GRAY))
                            .append(new TextComponent(bomb.server()).withStyle(ChatFormatting.WHITE)))
                    .append(new TextComponent(" for: ")
                            .withStyle(ChatFormatting.GRAY)
                            .append(new TextComponent(bomb.getRemainingString()).withStyle(ChatFormatting.WHITE)));
        }

        return response;
    }
}
