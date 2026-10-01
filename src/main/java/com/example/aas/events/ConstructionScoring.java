package com.example.aas.events;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Очки за строительство начисляются при ЗАВЕРШЕНИИ постройки (а не при установке призрака).
 * Вкладчики регистрируются при каждом ударе сапёрной лопатой (EntrenchingToolItem),
 * завершивший (последний вкладчик) получает 100% очков, остальные - BUILD_CONTRIBUTOR_PERCENT.
 * Составные постройки (стены, проволока, бункер из связанных блоков) имеют общий structureKey,
 * поэтому очки за одну постройку начисляются один раз.
 */
public final class ConstructionScoring {

    private ConstructionScoring() {}

    private record Key(ResourceLocation dimension, long structure) {}

    private static final class Entry {
        final Map<UUID, Long> contributors = new LinkedHashMap<>();
        UUID lastContributor = null;
        long lastActivityTick = 0;
        long completedTick = -1;
    }

    private static final Map<Key, Entry> ENTRIES = new HashMap<>();

    /** Ключ постройки для блок-сущности; null - этот тип не даёт очков за стройку. */
    public static Long keyOf(BlockEntity be) {
        if (be instanceof com.example.aas.block.WallBlockEntity w) return w.getStructureKey();
        if (be instanceof com.example.aas.block.BarbedWireBlockEntity b) return b.getStructureKey();
        if (be instanceof com.example.aas.block.BunkerBlockEntity
                || be instanceof com.example.aas.block.AGSConstructionBlockEntity
                || be instanceof com.example.aas.block.M2ConstructionBlockEntity
                || be instanceof com.example.aas.block.MortarConstructionBlockEntity
                || be instanceof com.example.aas.block.TOWConstructionBlockEntity) {
            return be.getBlockPos().asLong();
        }
        return null;
    }

    /** Ключ для одиночной постройки в позиции pos. */
    public static long keyOf(BlockPos pos) {
        return pos.asLong();
    }

    /** Игрок вложился в постройку (удар лопатой). */
    public static void contribute(ServerLevel level, long structureKey, ServerPlayer player) {
        if (player == null) return;
        long now = level.getGameTime();
        prune(now);
        Key key = new Key(level.dimension().location(), structureKey);
        Entry entry = ENTRIES.get(key);
        if (entry == null || entry.completedTick >= 0) {
            entry = new Entry();
            ENTRIES.put(key, entry);
        }
        entry.contributors.put(player.getUUID(), now);
        entry.lastContributor = player.getUUID();
        entry.lastActivityTick = now;
    }

    /** Постройка завершена: начисляем очки один раз на всю (возможно составную) постройку. */
    public static void complete(ServerLevel level, long structureKey, ScoreType type) {
        long now = level.getGameTime();
        Key key = new Key(level.dimension().location(), structureKey);
        Entry entry = ENTRIES.get(key);
        if (entry == null || entry.completedTick >= 0) return; // никто не строил или уже начислили
        entry.completedTick = now;

        for (Map.Entry<UUID, Long> c : entry.contributors.entrySet()) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(c.getKey());
            if (p == null) continue;
            if (c.getKey().equals(entry.lastContributor)) {
                StatsHandler.addScore(p, type);
            } else {
                StatsHandler.addScoreScaled(p, type, ScoreValues.BUILD_CONTRIBUTOR_PERCENT);
            }
        }
    }

    private static void prune(long now) {
        if (ENTRIES.size() < 64) return;
        ENTRIES.values().removeIf(e -> now < e.lastActivityTick
                || (e.completedTick >= 0 && now - e.completedTick > 200L)
                || now - e.lastActivityTick > ScoreValues.BUILD_ENTRY_TTL_TICKS);
    }
}
