// PATH: src\main\java\com\example\aas\voicechat\VoiceLevelMeter.java
package com.example.aas.voicechat;

import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Measures how loud each player is speaking (server side, from the microphone packets).
 *
 * Every mic packet (20 ms of Opus audio) is decoded to PCM, its RMS level is converted to dBFS,
 * smoothed (fast attack, slow release) and the peak since the last report is quantized to 3 stages:
 *
 *   QUIET  (0) - barely audible
 *   MEDIUM (1) - medium volume
 *   NORMAL (2) - normal / loud speech
 *
 * Usage (see AASVoicechatPlugin):
 *   VoiceLevelMeter.update(serverApi, playerUuid, opus);   // for every mic packet
 *   int level = VoiceLevelMeter.takeLevel(playerUuid);     // when an activity packet is sent
 *   VoiceLevelMeter.remove(playerUuid);                    // on disconnect
 */
public final class VoiceLevelMeter {

    public static final int QUIET = 0;
    public static final int MEDIUM = 1;
    public static final int NORMAL = 2;

    // ---- thresholds (dBFS, 0 = full scale). Tune these if your players' mics are louder/quieter. ----
    /** at or above this level the voice is "medium" (below it: quiet) */
    static final float MEDIUM_DB = -40f;
    /** at or above this level the voice is "normal" */
    static final float NORMAL_DB = -28f;
    /** how fast the displayed level falls after a loud moment, in dB per 20 ms packet (1.0 = 50 dB/s) */
    static final float DECAY_DB_PER_PACKET = 1.0f;

    static final float SILENCE_DB = -90f;

    private static final class State {
        OpusDecoder decoder;
        float smoothed = SILENCE_DB;   // current smoothed level
        float peak = SILENCE_DB;       // highest smoothed level since the last takeLevel()
    }

    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    private VoiceLevelMeter() {}

    /** Call for every microphone packet of the player (in order). */
    public static void update(VoicechatServerApi api, UUID id, byte[] opus) {
        try {
            State st = STATES.computeIfAbsent(id, k -> {
                State s = new State();
                s.decoder = api.createDecoder();
                return s;
            });

            // an empty packet marks the end of a transmission
            if (opus == null || opus.length == 0) {
                st.decoder.resetState();
                st.smoothed = SILENCE_DB;
                st.peak = SILENCE_DB;
                return;
            }

            float db = dbfs(st.decoder.decode(opus));
            st.smoothed = db > st.smoothed ? db : Math.max(db, st.smoothed - DECAY_DB_PER_PACKET);
            if (st.smoothed > st.peak) st.peak = st.smoothed;
        } catch (Throwable t) {
            // decoding problem: don't break voice routing, just show the full icon
            State st = STATES.get(id);
            if (st != null) st.peak = Math.max(st.peak, NORMAL_DB);
        }
    }

    /** Level (0..2) of the loudest moment since the previous call; starts a new measuring window. */
    public static int takeLevel(UUID id) {
        State st = STATES.get(id);
        if (st == null) return NORMAL;
        int level = levelForDb(st.peak);
        st.peak = st.smoothed;
        return level;
    }

    public static void remove(UUID id) {
        State st = STATES.remove(id);
        if (st != null && st.decoder != null) {
            try { st.decoder.close(); } catch (Throwable ignored) {}
        }
    }

    // ------------------------------------------------------------------ helpers

    static int levelForDb(float db) {
        if (db >= NORMAL_DB) return NORMAL;
        if (db >= MEDIUM_DB) return MEDIUM;
        return QUIET;
    }

    /** RMS of 16-bit PCM in dBFS. */
    static float dbfs(short[] pcm) {
        if (pcm == null || pcm.length == 0) return SILENCE_DB;
        double sum = 0;
        for (short s : pcm) sum += (double) s * s;
        double rms = Math.sqrt(sum / pcm.length);
        if (rms < 1.0) return SILENCE_DB;
        return (float) (20.0 * Math.log10(rms / 32768.0));
    }
}