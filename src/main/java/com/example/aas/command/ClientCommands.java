package com.example.aas.command;

import com.example.aas.config.AASConfig;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "aas", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientCommands {

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("compass")
                .then(net.minecraft.commands.Commands.literal("scale")
                        .then(net.minecraft.commands.Commands.argument("value", IntegerArgumentType.integer(1, 3))
                                .executes(context -> {
                                    int val = IntegerArgumentType.getInteger(context, "value");

                                    // Сохраняем значение в конфиг
                                    AASConfig.COMPASS_SCALE.set(val);
                                    AASConfig.CLIENT_SPEC.save(); // Записываем файл на диск

                                    String sizeText = (val == 1) ? "Small" : (val == 3) ? "Large" : "Normal";

                                    // ИСПРАВЛЕНО: используем sendSuccess вместо sendFeedback
                                    context.getSource().sendSuccess(() -> Component.literal("Compass scale saved: " + sizeText)
                                            .withStyle(ChatFormatting.GREEN), false);

                                    return 1;
                                })
                        )
                )
        );
    }
}