// PATH: src\main\java\com\example\aas\client\VoiceIcons.java
package com.example.aas.client;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client side: remembers the voice level per speaker and picks the icon texture for it.
 * level: 0 = quiet, 1 = medium, 2 = normal (same numbering as VoiceLevelMeter).
 * The shown level changes only after the new level has been stable for a short time (no flicker).
 *
 * Optional textures (if a file is missing, the base voice_icon.png is used):
 *   textures/gui/voice_icon_quiet.png
 *   textures/gui/voice_icon_medium.png
 */
public final class VoiceIcons {

    /** how long a HIGHER level must persist before the icon switches up (ms) */
    private static final long HOLD_UP_MS = 120;
    /** how long a LOWER level must persist before the icon switches down (ms) */
    private static final long HOLD_DOWN_MS = 450;
    /** pause in speech after which the icon is reset instantly on the next word (ms) */
    private static final long RESET_AFTER_MS = 1000;

    private static final ResourceLocation ICON_NORMAL = new ResourceLocation("aas", "textures/gui/voice_icon.png");
    private static final ResourceLocation ICON_MEDIUM = new ResourceLocation("aas", "textures/gui/voice_icon_medium.png");
    private static final ResourceLocation ICON_QUIET  = new ResourceLocation("aas", "textures/gui/voice_icon_quiet.png");

    private static final class State {
        int shown;          // level currently displayed
        int pending;        // level we are considering switching to
        long pendingSince;  // when pending was first seen
        long lastUpdate;    // last packet time
    }

    private static final Map<String, State> STATES = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, Boolean> EXISTS = new ConcurrentHashMap<>();

    private VoiceIcons() {}

    /** Called from PacketVoiceChannelActivity when a player is speaking. */
    public static void put(String playerName, int level) {
        if (playerName == null) return;
        level = Math.max(0, Math.min(2, level));
        long now = System.currentTimeMillis();

        State s = STATES.get(playerName);
        if (s == null || now - s.lastUpdate > RESET_AFTER_MS) {
            s = new State();
            s.shown = level;
            s.pending = level;
            s.pendingSince = now;
            STATES.put(playerName, s);
        } else if (level != s.pending) {
            s.pending = level;
            s.pendingSince = now;
        }
        s.lastUpdate = now;
    }

    /** Texture for the player's current voice level. Never returns null. */
    public static ResourceLocation forPlayer(String playerName) {
        int level = 2;
        State s = playerName == null ? null : STATES.get(playerName);
        if (s != null) {
            if (s.pending != s.shown) {
                long hold = s.pending > s.shown ? HOLD_UP_MS : HOLD_DOWN_MS;
                if (System.currentTimeMillis() - s.pendingSince >= hold) {
                    s.shown = s.pending;
                }
            }
            level = s.shown;
        }
        ResourceLocation tex = switch (level) {
            case 0 -> ICON_QUIET;
            case 1 -> ICON_MEDIUM;
            default -> ICON_NORMAL;
        };
        return exists(tex) ? tex : ICON_NORMAL;
    }

    private static boolean exists(ResourceLocation rl) {
        if (rl == ICON_NORMAL) return true;
        return EXISTS.computeIfAbsent(rl,
                r -> Minecraft.getInstance().getResourceManager().getResource(r).isPresent());
    }

    public static void remove(String playerName) {
        if (playerName != null) STATES.remove(playerName);
    }

    public static void clear() {
        STATES.clear();
    }
}