// PATH: src\main\java\com\example\aas\voicechat\VoiceActivityClock.java
package com.example.aas.voicechat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers when the last REAL microphone packet of each player arrived.
 * Has no Simple Voice Chat dependency, so it is safe to use from code that must work without the voicechat mod.
 */
public final class VoiceActivityClock {
    private static final Map<UUID, Long> LAST_MIC = new ConcurrentHashMap<>();

    private VoiceActivityClock() {}

    public static void markMic(UUID id) {
        LAST_MIC.put(id, System.currentTimeMillis());
    }

    /** timestamp (ms) of the last mic packet, 0 if none */
    public static long lastMic(UUID id) {
        return LAST_MIC.getOrDefault(id, 0L);
    }

    public static void remove(UUID id) {
        LAST_MIC.remove(id);
    }
}