package com.example.aas.voicechat;

import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Какой голосовой канал сейчас зажал каждый игрок (его присылает клиент, см. PacketVoiceChannel).
 *
 * ВАЖНО: в этом классе НЕТ ссылок на Simple Voice Chat, поэтому его безопасно вызывать
 * из сетевых пакетов даже если мод voicechat не установлен.
 *
 * Состояние «протухает»: клиент шлёт «я всё ещё держу клавишу» каждые ~0.5 сек.
 * Если сообщения перестали приходить (краш клиента, потеря пакета отпускания) — через
 * STALE_MS канал считается отпущенным, чтобы микрофон не залипал.
 */
@Mod.EventBusSubscriber(modid = "aas")
public final class VoiceChannelState {
    public static final int NONE = 0;
    public static final int LOCAL = 1;     // V — локальный чат
    public static final int SQUAD = 2;     // B — чат отряда
    public static final int COMMAND = 3;   // * — командирский чат (только лидеры отрядов)

    private static final long STALE_MS = 3000L;
    private static final Map<UUID, long[]> STATE = new ConcurrentHashMap<>(); // {channel, timestampMs}

    private VoiceChannelState() {}

    public static void set(UUID id, int channel) {
        if (channel <= NONE || channel > COMMAND) {
            STATE.remove(id);
            return;
        }
        STATE.put(id, new long[]{channel, System.currentTimeMillis()});
    }

    public static int get(UUID id) {
        long[] s = STATE.get(id);
        if (s == null) return NONE;
        if (System.currentTimeMillis() - s[1] > STALE_MS) {
            STATE.remove(id);
            return NONE;
        }
        return (int) s[0];
    }

    public static void clear(UUID id) {
        STATE.remove(id);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() != null) clear(event.getEntity().getUUID());
    }
}