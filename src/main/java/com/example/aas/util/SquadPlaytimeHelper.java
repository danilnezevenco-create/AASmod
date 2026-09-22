package com.example.aas.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

/**
 * Читает squad_players.json из корня сервера (рядом с папками mods/config).
 * Файл пишет сторонний трекер статистики, этот класс его только читает.
 */
public class SquadPlaytimeHelper {

    private static final String FILE_NAME = "squad_players.json";

    /** Часы игрока на сервере, либо -1 если не удалось определить. */
    public static long getPlaytimeHoursByName(MinecraftServer server, String playerName) {
        if (server == null || playerName == null || playerName.isEmpty()) return -1;
        UUID uuid = resolveUuid(server, playerName);
        if (uuid == null) return -1;
        return getPlaytimeHoursByUuid(uuid);
    }

    private static UUID resolveUuid(MinecraftServer server, String playerName) {
        // Если игрок сейчас онлайн - берём UUID напрямую
        ServerPlayer online = server.getPlayerList().getPlayerByName(playerName);
        if (online != null) return online.getUUID();

        // Иначе пробуем из кэша профилей сервера (usercache.json)
        try {
            Optional<GameProfile> cached = server.getProfileCache() != null
                    ? server.getProfileCache().get(playerName)
                    : Optional.empty();
            return cached.map(GameProfile::getId).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private static long getPlaytimeHoursByUuid(UUID uuid) {
        Path path = FMLPaths.GAMEDIR.get().resolve(FILE_NAME);
        if (!Files.exists(path)) return -1;

        try (FileReader reader = new FileReader(path.toFile())) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject entry = root.getAsJsonObject(uuid.toString());
            if (entry == null || !entry.has("playtimeTicks")) return -1;

            long ticks = entry.get("playtimeTicks").getAsLong();
            return ticks / 20L / 3600L; // 20 тиков = 1 секунда
        } catch (Exception e) {
            return -1;
        }
    }
}