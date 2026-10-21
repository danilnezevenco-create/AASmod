// PATH: src\main\java\com\example\aas\voicechat\VoiceHeldKeyIndicator.java
package com.example.aas.voicechat;

import com.example.aas.config.AASConfig;
import com.example.aas.network.PacketHandler;
import com.example.aas.network.PacketVoiceChannelActivity;
import com.example.aas.world.AASWorldData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * If a player HOLDS a voice channel key (V / B / *) but no microphone packets arrive
 * (silent mic, Simple Voice Chat in voice-activation mode, ...), the speaking indicator would never show.
 * This class fills that gap: while the key is held and no real mic packet arrived recently,
 * it reports "speaking" with the quietest level (0) every 150 ms, to the same recipients as real speech.
 *
 * Real mic packets are handled by AASVoicechatPlugin (with the measured level);
 * this class only steps in when they are missing. It does not use the Simple Voice Chat API.
 */
@Mod.EventBusSubscriber(modid = "aas")
public final class VoiceHeldKeyIndicator {

    private static final long REPORT_EVERY_MS = 150;
    /** if a real mic packet arrived within this time, the plugin already reports the activity */
    private static final long MIC_ACTIVE_MS = 300;

    private static final Map<UUID, Long> LAST_REPORT = new ConcurrentHashMap<>();

    private VoiceHeldKeyIndicator() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (server == null) return;

        long now = System.currentTimeMillis();
        for (ServerPlayer sender : server.getPlayerList().getPlayers()) {
            UUID id = sender.getUUID();
            int channel = VoiceChannelState.get(id);
            if (channel == VoiceChannelState.NONE) {
                LAST_REPORT.remove(id);
                continue;
            }
            if (now - VoiceActivityClock.lastMic(id) < MIC_ACTIVE_MS) continue;       // real speech: plugin reports it
            if (now - LAST_REPORT.getOrDefault(id, 0L) < REPORT_EVERY_MS) continue;   // throttle
            LAST_REPORT.put(id, now);

            try {
                report(server, sender, channel);
            } catch (Exception e) {
                System.err.println("[AAS Voicechat] held-key indicator failed: " + e);
            }
        }
    }

    // Same recipients as the real voice routing (see AASVoicechatPlugin.routeLocal / routeSquad / routeCommand).
    private static void report(MinecraftServer server, ServerPlayer sender, int channel) {
        String name = sender.getScoreboardName();
        PacketVoiceChannelActivity activity = new PacketVoiceChannelActivity(name, channel, VoiceLevelConstants.QUIET);

        switch (channel) {
            case VoiceChannelState.LOCAL -> {
                double range = getLocalRange();
                double r2 = range * range;
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    if (p.level() != sender.level()) continue;
                    if (p.distanceToSqr(sender) > r2) continue;
                    if (p == sender || isAlly(sender, p)) send(p, activity);
                }
            }
            case VoiceChannelState.SQUAD -> {
                AASWorldData data = AASWorldData.get(sender.serverLevel());
                AASWorldData.Squad squad = findSquad(data, name);
                if (squad == null) return;
                for (String member : new java.util.ArrayList<>(squad.members)) {
                    ServerPlayer p = server.getPlayerList().getPlayerByName(member);
                    if (p != null) send(p, activity);
                }
            }
            case VoiceChannelState.COMMAND -> {
                AASWorldData data = AASWorldData.get(sender.serverLevel());
                AASWorldData.Squad own = findSquad(data, name);
                if (own == null || !name.equals(own.leader)) return;   // only squad leaders can use the command channel
                for (AASWorldData.Squad s : new java.util.ArrayList<>(data.squads)) {
                    if (s.leader == null || s.leader.isEmpty()) continue;
                    ServerPlayer p = server.getPlayerList().getPlayerByName(s.leader);
                    if (p == null) continue;
                    if (p != sender && !isAlly(sender, p)) continue;
                    send(p, activity);
                }
            }
            default -> { }
        }
    }

    private static void send(ServerPlayer p, PacketVoiceChannelActivity activity) {
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> p), activity);
    }

    private static AASWorldData.Squad findSquad(AASWorldData data, String playerName) {
        for (AASWorldData.Squad s : data.squads) {
            if (s.members.contains(playerName)) return s;
        }
        return null;
    }

    private static boolean isAlly(ServerPlayer a, ServerPlayer b) {
        if (a.getTeam() == null || b.getTeam() == null) return false;
        return a.getTeam().getName().equals(b.getTeam().getName());
    }

    private static double getLocalRange() {
        try {
            return (double) (float) AASConfig.VOICE_LOCAL_RANGE.get();
        } catch (Throwable t) {
            return 30.0;
        }
    }

    /** quietest stage; same numbering as VoiceLevelMeter / VoiceIcons (kept here to avoid touching the SVC-dependent class) */
    private static final class VoiceLevelConstants {
        static final int QUIET = 0;
    }
}