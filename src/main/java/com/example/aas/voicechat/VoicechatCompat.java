package com.example.aas.voicechat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;

// Безопасный мост, чтобы игра не крашилась без мода Simple Voice Chat,
// а также чтобы любая несовместимость версии API (NoClassDefFoundError /
// LinkageError при первом обращении к AASVoicechatPlugin) не ломала squad-логику.
//
// ВАЖНО: NoClassDefFoundError/LinkageError наследуются от Error, а не Exception.
// Обычный catch(Exception e) их НЕ перехватит. Поэтому здесь используется
// catch(Throwable t) - это покрывает оба случая.
public class VoicechatCompat {
    private static volatile boolean broken = false;

    public static boolean isLoaded() {
        if (broken) return false;
        try {
            return ModList.get().isLoaded("voicechat");
        } catch (Throwable t) {
            System.err.println("[AAS Voicechat] Failed to check ModList: " + t);
            broken = true;
            return false;
        }
    }

    public static void createAndJoinGroup(ServerPlayer player, int squadId, String squadName) {
        if (!isLoaded()) return;
        try {
            AASVoicechatPlugin.createAndJoinGroup(player, squadId, squadName);
        } catch (Throwable t) {
            // Несовместимая версия voicechat-api/jar или другая внутренняя ошибка -
            // squad должен продолжить жить независимо от этого.
            System.err.println("[AAS Voicechat] createAndJoinGroup crashed, disabling voicechat integration: " + t);
            broken = true;
        }
    }

    public static void joinGroup(ServerPlayer player, int squadId) {
        if (!isLoaded()) return;
        try {
            AASVoicechatPlugin.joinGroup(player, squadId);
        } catch (Throwable t) {
            System.err.println("[AAS Voicechat] joinGroup crashed, disabling voicechat integration: " + t);
            broken = true;
        }
    }

    public static void leaveGroup(ServerPlayer player) {
        if (!isLoaded()) return;
        try {
            AASVoicechatPlugin.leaveGroup(player);
        } catch (Throwable t) {
            System.err.println("[AAS Voicechat] leaveGroup crashed, disabling voicechat integration: " + t);
            broken = true;
        }
    }
}