package com.example.aas.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Плашка "Вы убили союзника" / "Вас убил союзник".
 * Оформление один в один как плашка кровотечения из AAS Medicine
 * (чёрный фон 0xAA000000, цветная полоса слева, иконка 16x16, выезд слева),
 * но расположена НИЖЕ неё: у кровотечения y=40, высота 26 -> наша начинается с y=72.
 */
@Mod.EventBusSubscriber(modid = "aas", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TeamKillOverlay {

    private static final ResourceLocation ICON = new ResourceLocation("aas", "textures/gui/teamkill_icon.png");

    // --- геометрия (как у плашки кровотечения) ---
    private static final int HEIGHT = 26;
    private static final int BASE_Y = 72;        // кровотечение: y=40..66, зазор 6px
    private static final int STACK_GAP = 4;      // расстояние между несколькими плашками
    private static final int MAX_VISIBLE = 3;
    private static final int STRIPE_COLOR = 0xFFE03B3B; // красная полоса (у кровотечения золотая 0xFFFFD700)

    // --- тайминги ---
    private static final long SLIDE_MS = 400;
    private static final long HOLD_MS = 5000;
    private static final long TOTAL_MS = SLIDE_MS + HOLD_MS + SLIDE_MS;

    private static class Entry {
        final Component text;
        final long start = System.currentTimeMillis();

        Entry(Component text) {
            this.text = text;
        }
    }

    private static final List<Entry> ENTRIES = new CopyOnWriteArrayList<>();

    /** Вызывается из PacketTeamKillNotification на клиенте. */
    public static void push(String otherName, boolean youAreKiller, boolean knocked) {
        String key = "aas.hud.teamkill." + (knocked ? "knock." : "") + (youAreKiller ? "killer" : "victim");
        ENTRIES.add(new Entry(Component.translatable(key, otherName)));
        while (ENTRIES.size() > MAX_VISIBLE) {
            ENTRIES.remove(0);
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || ENTRIES.isEmpty()) return;

        GuiGraphics g = event.getGuiGraphics();
        long now = System.currentTimeMillis();
        int slot = 0;

        for (Entry e : ENTRIES) {
            long elapsed = now - e.start;
            if (elapsed >= TOTAL_MS) {
                ENTRIES.remove(e);
                continue;
            }

            float progress;
            if (elapsed < SLIDE_MS) {
                progress = (float) elapsed / SLIDE_MS;
            } else if (elapsed < SLIDE_MS + HOLD_MS) {
                progress = 1.0f;
            } else {
                progress = 1.0f - (float) (elapsed - SLIDE_MS - HOLD_MS) / SLIDE_MS;
            }

            int textWidth = mc.font.width(e.text);
            int width = textWidth + 46;
            int x = (int) (-width + (progress * (width + 10)));
            int y = BASE_Y + slot * (HEIGHT + STACK_GAP);

            RenderSystem.enableBlend();
            g.fill(x, y, x + width, y + HEIGHT, 0xAA000000);
            g.fill(x, y, x + 3, y + HEIGHT, STRIPE_COLOR);

            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            g.blit(ICON, x + 8, y + 5, 0, 0, 16, 16, 16, 16);

            g.drawString(mc.font, e.text, x + 32, y + 9, 0xFFFFFFFF, true);
            RenderSystem.disableBlend();

            slot++;
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ENTRIES.clear();
    }

    private TeamKillOverlay() {
    }
}
