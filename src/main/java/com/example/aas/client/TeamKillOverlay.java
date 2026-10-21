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
 * Плашки слева сверху: тимкилл + личные уведомления фаертима (Bravo/Charlie).
 */
@Mod.EventBusSubscriber(modid = "aas", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TeamKillOverlay {

    private static final ResourceLocation ICON = new ResourceLocation("aas", "textures/gui/teamkill_icon.png");

    // Иконки фаертима (белый силуэт 16x16 на прозрачном фоне, красится в цвет группы).
    // Индекс = тип уведомления: 0 добавили, 1 вы лидер, 2 лидер сменился, 3 ваш лидер.
    // Сейчас везде один шеврон; чтобы сделать разные - просто поменяй пути.
    private static final ResourceLocation FT_CHEVRON = new ResourceLocation("aas", "textures/gui/fireteam_chevron.png");
    private static final ResourceLocation[] FT_ICONS = { FT_CHEVRON, FT_CHEVRON, FT_CHEVRON, FT_CHEVRON };

    private static final int COLOR_BRAVO = 0xFFAA33FF;
    private static final int COLOR_CHARLIE = 0xFF00E5CC;

    private static final int HEIGHT = 26;
    private static final int BASE_Y = 72;
    private static final int STACK_GAP = 4;
    private static final int MAX_VISIBLE = 4;
    private static final int STRIPE_COLOR = 0xFFE03B3B;

    private static final long SLIDE_MS = 400;
    private static final long HOLD_MS = 5000;
    private static final long TOTAL_MS = SLIDE_MS + HOLD_MS + SLIDE_MS;

    private static class Entry {
        final Component text;
        final ResourceLocation icon;
        final int stripe;
        final boolean tintIcon;
        final long start = System.currentTimeMillis();

        Entry(Component text, ResourceLocation icon, int stripe, boolean tintIcon) {
            this.text = text;
            this.icon = icon;
            this.stripe = stripe;
            this.tintIcon = tintIcon;
        }
    }

    private static final List<Entry> ENTRIES = new CopyOnWriteArrayList<>();

    private static void add(Entry e) {
        ENTRIES.add(e);
        while (ENTRIES.size() > MAX_VISIBLE) {
            ENTRIES.remove(0);
        }
    }

    /** Вызывается из PacketTeamKillNotification на клиенте. */
    public static void push(String otherName, boolean youAreKiller, boolean knocked) {
        String key = "aas.hud.teamkill." + (knocked ? "knock." : "") + (youAreKiller ? "killer" : "victim");
        add(new Entry(Component.translatable(key, otherName), ICON, STRIPE_COLOR, false));
    }

    /** Вызывается из PacketFireteamNotification на клиенте. */
    public static void pushFireteam(int type, boolean bravo, String name) {
        String group = bravo ? "Bravo" : "Charlie";
        Component text;
        switch (type) {
            case 0: text = Component.translatable("aas.hud.fireteam.added", group); break;
            case 1: text = Component.translatable("aas.hud.fireteam.you_leader", group); break;
            case 2: text = Component.translatable("aas.hud.fireteam.leader_changed", group, name); break;
            case 3: text = Component.translatable("aas.hud.fireteam.your_leader", group, name); break;
            default: return;
        }
        ResourceLocation icon = FT_ICONS[Math.max(0, Math.min(type, FT_ICONS.length - 1))];
        add(new Entry(text, icon, bravo ? COLOR_BRAVO : COLOR_CHARLIE, true));
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
            g.fill(x, y, x + 3, y + HEIGHT, e.stripe);

            if (e.tintIcon) {
                float r = ((e.stripe >> 16) & 0xFF) / 255f;
                float gr = ((e.stripe >> 8) & 0xFF) / 255f;
                float b = (e.stripe & 0xFF) / 255f;
                RenderSystem.setShaderColor(r, gr, b, 1.0f);
            } else {
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            }
            g.blit(e.icon, x + 8, y + 5, 0, 0, 16, 16, 16, 16);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

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