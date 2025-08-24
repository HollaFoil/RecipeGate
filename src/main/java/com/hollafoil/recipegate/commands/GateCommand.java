package com.hollafoil.recipegate.commands;

import com.hollafoil.recipegate.RecipeGate;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid = RecipeGate.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class GateCommand {

    @SubscribeEvent
    public static void registerCommands(final RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        CommandBuildContext buildContext = event.getBuildContext();

        dispatcher.register(
                Commands.literal("recipegate")
                        .then(Commands.literal("gate")
                                .then(Commands.argument("item", ItemArgument.item(buildContext))
                                        .executes(context -> {
                                            ItemInput itemInput = ItemArgument.getItem(context, "item");

                                            // Here's where your recipe gating logic will go
                                            RecipeGate.getLogger()
                                                    .info("Attempting to gate recipe for item: {}", itemInput.getItem()
                                                            .getDescription()
                                                            .getString());
                                            context.getSource().sendSuccess(
                                                    () -> Component.literal("Recipe gating initiated for: " + itemInput.getItem().getDescription().getString()),
                                                    false
                                            );

                                            RecipeGate.addRestriction(ForgeRegistries.ITEMS.getKey(itemInput.getItem()));

                                            context.getSource().sendSuccess(
                                                    () -> Component.literal("Recipe gating complete"),
                                                    false
                                            );
                                            return 1;
                                        })
                                )
                        )
        );

        dispatcher.register(
                Commands.literal("recipegate")
                        .then(Commands.literal("sync")
                                .then(Commands.literal("server").executes(context -> {
                                        Boolean result = RecipeGate.syncStagesServer();

                                        if (result) context.getSource().sendSuccess(
                                                () -> Component.literal("All stages were successfully synced"),
                                                false
                                        );
                                        else context.getSource().sendSuccess(
                                                () -> Component.literal("Something went wrong when syncing stages"),
                                                false
                                        );
                                        return 1;
                                    }))
                                .then(Commands.argument("player", EntityArgument.player()).executes(context -> {
                                    Player player = EntityArgument.getPlayer(context, "player");
                                    Boolean result = RecipeGate.syncStagesPlayer(player);

                                    if (result) context.getSource().sendSuccess(
                                            () -> Component.literal("All stages were successfully synced"),
                                            false
                                    );
                                    else context.getSource().sendSuccess(
                                            () -> Component.literal("Something went wrong when syncing stages"),
                                            false
                                    );
                                    return 1;
                                }))
                        )
        );
    }
}
