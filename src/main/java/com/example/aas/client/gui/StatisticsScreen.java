package com.example.aas.client.gui;

import com.example.aas.client.ClientData;
import com.example.aas.network.PlayerStatInfo;
import com.example.aas.world.AASWorldData;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.*;
import java.util.stream.Collectors;

public class StatisticsScreen extends Screen {

    private static final ResourceLocation CIRCLE_BADGE = new ResourceLocation("aas", "textures/gui/map_icons/player_circle.png");

    private static final ResourceLocation FLAG_UKRAINE = new ResourceLocation("aas", "textures/gui/flags/ukraine.png");
    private static final ResourceLocation FLAG_RUSSIA = new ResourceLocation("aas", "textures/gui/flags/russia.png");
    private static final ResourceLocation FLAG_USA = new ResourceLocation("aas", "textures/gui/flags/usa.png");
    private static final ResourceLocation FLAG_NATO = new ResourceLocation("aas", "textures/gui/flags/nato.png");
    private static final ResourceLocation FLAG_BLUEFOR = new ResourceLocation("aas", "textures/gui/flags/bluefor.png");
    private static final ResourceLocation FLAG_REDFOR = new ResourceLocation("aas", "textures/gui/flags/redfor.png");
    private static final ResourceLocation FLAG_INSURGENCY = new ResourceLocation("aas", "textures/gui/flags/insurgency.png");
    private static final ResourceLocation FLAG_PMC = new ResourceLocation("aas", "textures/gui/flags/pmc.png");
    private static final ResourceLocation FLAG_GERMANY = new ResourceLocation("aas", "textures/gui/flags/germany.png");
    private static final ResourceLocation FLAG_MILITIA = new ResourceLocation("aas", "textures/gui/flags/militia.png");

    // ИКОНКИ КИЛЛОВ/СМЕРТЕЙ — положите PNG 10x10 по этим путям (можно поменять)
    private static final ResourceLocation KILL_ICON = new ResourceLocation("aas", "textures/gui/stats/kills.png");
    private static final ResourceLocation DEATH_ICON = new ResourceLocation("aas", "textures/gui/stats/deaths.png");

    private static final int ROW_HEIGHT = 12;
    private static final int TOP_MARGIN = 34;   // отступ под заголовок экрана
    private static final int BOTTOM_MARGIN = 8;

    private int scrollOffset = 0;
    private int maxScroll = 0;

    public StatisticsScreen() {
        super(Component.literal("Statistics"));
    }

    @Override
    protected void init() {
        // управляющих виджетов пока нет — экран информационный
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTicks) {
        gui.fill(0, 0, this.width, this.height, 0xCC0B0B0B);
        gui.drawCenteredString(this.font, Component.translatable("aas.gui.stats.title"), this.width / 2, 8, 0xFFFFFFFF);

        int margin = 20;
        int colGap = 24;
        int colWidth = (this.width - margin * 2 - colGap) / 2;
        int leftX = margin;
        int rightX = margin + colWidth + colGap;

        int contentTop = TOP_MARGIN;
        int contentBottom = this.height - BOTTOM_MARGIN;

        String myTeam = getMyTeamKey();
        // Пункт 2: если игрок не в команде или не BLUE/RED — слева BLUE, справа RED
        String leftKey = myTeam.equals("RED") ? "RED" : "BLUE";
        String rightKey = leftKey.equals("BLUE") ? "RED" : "BLUE";

        gui.enableScissor(0, contentTop, this.width, contentBottom);
        PoseStack pose = gui.pose();
        pose.pushPose();
        pose.translate(0, -scrollOffset, 0);

        int leftBottom = drawTeamColumn(gui, leftX, colWidth, leftKey, contentTop);
        int rightBottom = drawTeamColumn(gui, rightX, colWidth, rightKey, contentTop);
        int columnsBottom = Math.max(leftBottom, rightBottom) + 10;

        int finalBottom = drawNoTeamSection(gui, margin, this.width - margin * 2, columnsBottom);

        pose.popPose();
        gui.disableScissor();

        int visibleHeight = contentBottom - contentTop;
        int totalHeight = finalBottom - contentTop;
        maxScroll = Math.max(0, totalHeight - visibleHeight);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;

        super.render(gui, mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scrollOffset -= (int) (delta * 16);
        if (scrollOffset < 0) scrollOffset = 0;
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;
        return true;
    }

    // ================= КОЛОНКА КОМАНДЫ =================

    private int drawTeamColumn(GuiGraphics gui, int x, int width, String teamKey, int startY) {
        List<PlayerStatInfo> teamPlayers = ClientData.playerStats.stream()
                .filter(p -> p.team.equalsIgnoreCase(teamKey))
                .collect(Collectors.toList());

        int y = drawTeamHeader(gui, x, width, teamKey, teamPlayers.size(), startY);

        boolean isMine = !getMyTeamKey().isEmpty() && teamKey.equalsIgnoreCase(getMyTeamKey());
        boolean revealed = isTeamRevealed(isMine);

        if (!revealed) {
            // Минимальный режим для врагов: ник + пинг. Статистика (K/D) рисуется только если сервер её прислал
            for (PlayerStatInfo p : teamPlayers) {
                int nameX = drawPingBars(gui, x, y + 2, p.ping);
                gui.drawString(this.font, p.name, nameX, y + 2, 0xFFDDDDDD, false);
                drawKD(gui, x + width, y + 2, p.kills, p.deaths); // <-- Добавлена отрисовка K/D
                y += ROW_HEIGHT;
            }
            return y;
        }

        // Полный режим — группировка по отрядам (как в SquadSelectionScreen)
        int teamCMDId = teamKey.equalsIgnoreCase("BLUE") ? ClientData.blueCMDId : ClientData.redCMDId;

        List<AASWorldData.Squad> teamSquads = ClientData.clientSquads.stream()
                .filter(s -> s.team.equalsIgnoreCase(teamKey))
                .sorted((s1, s2) -> {
                    if (s1.id == teamCMDId && teamCMDId != -1) return -1;
                    if (s2.id == teamCMDId && teamCMDId != -1) return 1;
                    return Integer.compare(s1.id, s2.id);
                })
                .collect(Collectors.toList());

        Map<String, PlayerStatInfo> byName = new HashMap<>();
        for (PlayerStatInfo p : teamPlayers) byName.put(p.name, p);

        String myName = (this.minecraft.player != null) ? this.minecraft.player.getScoreboardName() : "";

        int index = 1;
        for (AASWorldData.Squad squad : teamSquads) {
            boolean isMySquad = squad.members.contains(myName);
            boolean isCMD = (squad.id == teamCMDId && teamCMDId != -1);

            int badgeColor = isCMD ? 0xFF4CD964 : (isMySquad ? 0xFF00FF00 : 0xFF3399FF);
            drawBadgeCircle(gui, x, y - 2, 12, badgeColor, String.valueOf(index));

            String squadLabel = (isCMD ? "[CMD] " : "") + squad.name;
            gui.drawString(this.font, squadLabel, x + 16, y, isCMD ? 0xFF55FF55 : 0xFFFFD700, false);

            String countText = squad.members.size() + "/9";
            gui.drawString(this.font, countText, x + width - this.font.width(countText) - 2, y, 0xFFAAAAAA, false);
            y += ROW_HEIGHT;

            // Лидер — первым, дальше остальные (пункт 6: не сортируем по статам)
            List<String> ordered = new ArrayList<>();
            if (!squad.leader.isEmpty()) ordered.add(squad.leader);
            for (String m : squad.members) if (!m.equals(squad.leader)) ordered.add(m);

            for (String member : ordered) {
                PlayerStatInfo stat = byName.get(member);
                boolean isLeaderRow = member.equals(squad.leader);

                int nameX = drawPingBars(gui, x + 6, y + 2, stat != null ? stat.ping : -1);

                int nameColor = isLeaderRow ? 0xFFFFD700 : 0xFFFFFFFF;
                gui.drawString(this.font, (isLeaderRow ? "SL " : "   ") + member, nameX, y + 2, nameColor, false);

                if (stat != null) {
                    drawKD(gui, x + width, y + 2, stat.kills, stat.deaths);
                }
                y += ROW_HEIGHT;
            }
            y += 4;
            index++;
        }

        // Игроки этой команды без отряда
        List<PlayerStatInfo> noSquad = teamPlayers.stream().filter(p -> p.squadId == -1).collect(Collectors.toList());
        if (!noSquad.isEmpty()) {
            gui.drawString(this.font, Component.translatable("aas.gui.stats.unassigned_players").getString(), x, y, 0xFF888888, false);
            y += ROW_HEIGHT;
            for (PlayerStatInfo p : noSquad) {
                int nameX = drawPingBars(gui, x + 6, y + 2, p.ping);
                gui.drawString(this.font, p.name, nameX, y + 2, 0xFFFFFFFF, false);
                drawKD(gui, x + width, y + 2, p.kills, p.deaths);
                y += ROW_HEIGHT;
            }
        }

        return y;
    }

    private int drawTeamHeader(GuiGraphics gui, int x, int width, String teamKey, int playerCount, int y) {
        boolean isBlue = teamKey.equalsIgnoreCase("BLUE");
        ResourceLocation flag = getFlagTexture(isBlue ? ClientData.BLUE_FACTION : ClientData.RED_FACTION);

        String teamName = isBlue ? ClientData.customBlueName : ClientData.customRedName;
        String faction = isBlue ? ClientData.BLUE_FACTION : ClientData.RED_FACTION;
        if (faction != null && !faction.equals("none") && !faction.equals("bluefor") && !faction.equals("redfor")) {
            teamName = faction.replace("_", " ").toUpperCase();
        }

        int flagW = 48, flagH = 28;
        int flagX = x + width / 2 - flagW / 2;

        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        if (flag != null) {
            gui.blit(flag, flagX, y, 0, 0, flagW, flagH, flagW, flagH);
        } else {
            gui.fill(flagX, y, flagX + flagW, y + flagH, isBlue ? 0xFF2255CC : 0xFFCC2222);
        }

        int textColor = isBlue ? 0xFF55AAFF : 0xFFFF5555;
        gui.drawCenteredString(this.font, teamName, x + width / 2, y + flagH + 4, textColor);
        gui.drawCenteredString(this.font, Component.translatable("aas.gui.stats.players_count", playerCount).getString(), x + width / 2, y + flagH + 15, 0xFFAAAAAA);

        return y + flagH + 30;
    }

    // ================= ИГРОКИ БЕЗ КОМАНДЫ (пункт 7) =================

    private int drawNoTeamSection(GuiGraphics gui, int x, int width, int y) {
        List<PlayerStatInfo> noTeam = ClientData.playerStats.stream()
                .filter(p -> p.team == null || p.team.isEmpty())
                .collect(Collectors.toList());

        if (noTeam.isEmpty()) return y;

        y += 6;
        gui.drawString(this.font, Component.translatable("aas.gui.stats.no_team_players").getString(), x, y, 0xFFAAAAAA, false);
        y += ROW_HEIGHT;

        for (PlayerStatInfo p : noTeam) {
            int nameX = drawPingBars(gui, x, y + 2, p.ping);
            gui.drawString(this.font, p.name, nameX, y + 2, 0xFFFFFFFF, false);
            drawKD(gui, x + width, y + 2, p.kills, p.deaths);
            y += ROW_HEIGHT;
        }
        return y;
    }

    // ================= МЕЛКИЕ ХЕЛПЕРЫ =================

    private String getMyTeamKey() {
        if (this.minecraft.player == null || this.minecraft.player.getTeam() == null) return "";
        return this.minecraft.player.getTeam().getName().toUpperCase();
    }

    private boolean isTeamRevealed(boolean isMine) {
        if (isMine) return true;
        if (this.minecraft.player == null) return false;
        boolean isAdminObserver = this.minecraft.player.hasPermissions(2)
                && (this.minecraft.player.isSpectator() || this.minecraft.player.isCreative());
        // Вражеские отряды всегда скрыты (рендерятся сплошным списком), если это не админ
        return isAdminObserver;
    }

    private void drawBadgeCircle(GuiGraphics gui, int x, int y, int size, int colorARGB, String label) {
        float a = ((colorARGB >> 24) & 0xFF) / 255f;
        float r = ((colorARGB >> 16) & 0xFF) / 255f;
        float g = ((colorARGB >> 8) & 0xFF) / 255f;
        float b = (colorARGB & 0xFF) / 255f;

        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(r, g, b, a);
        gui.blit(CIRCLE_BADGE, x, y, 0, 0, size, size, size, size);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        if (label != null && !label.isEmpty()) {
            gui.drawCenteredString(this.font, label, x + size / 2, y + (size - 8) / 2, 0xFFFFFFFF);
        }
    }

    // было void, стало int — возвращает X, откуда рисовать имя дальше
    private int drawPingBars(GuiGraphics gui, int x, int y, int ping) {
        int bars;
        int color;
        if (ping < 0) { bars = 1; color = 0xFF888888; }
        else if (ping <= 60) { bars = 4; color = 0xFF55FF55; }
        else if (ping <= 120) { bars = 3; color = 0xFFAAFF55; }
        else if (ping <= 250) { bars = 2; color = 0xFFFFAA00; }
        else { bars = 1; color = 0xFFFF5555; }

        for (int i = 0; i < 4; i++) {
            int barH = 2 + i * 2;
            int barX = x + i * 4;
            int barY = y + (8 - barH);
            int col = (i < bars) ? color : 0xFF3A3A3A;
            gui.fill(barX, barY, barX + 3, y + 8, col);
        }

        String pingText = (ping < 0) ? "-" : String.valueOf(ping);
        int textX = x + 18; // сразу после 4 палочек (последняя начинается на x+12, ширина 3)
        gui.drawString(this.font, pingText, textX, y, color, false);

        return textX + this.font.width(pingText) + 4; // отступ под следующий элемент (ник)
    }

    // Иконки киллов/смертей + числа, выровненные по правому краю (rightEdgeX)
    private void drawKD(GuiGraphics gui, int rightEdgeX, int y, int kills, int deaths) {
        if (kills < 0 || deaths < 0) return; // скрытая статистика врага

        String killsText = String.valueOf(kills);
        String deathsText = String.valueOf(deaths);

        int cursorX = rightEdgeX - this.font.width(deathsText);
        gui.drawString(this.font, deathsText, cursorX, y, 0xFFFF6666, false);
        cursorX -= 12;
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        gui.blit(DEATH_ICON, cursorX, y - 1, 0, 0, 10, 10, 10, 10);

        cursorX -= 6 + this.font.width(killsText);
        gui.drawString(this.font, killsText, cursorX, y, 0xFF66FF66, false);
        cursorX -= 12;
        gui.blit(KILL_ICON, cursorX, y - 1, 0, 0, 10, 10, 10, 10);
    }

    private ResourceLocation getFlagTexture(String faction) {
        if (faction == null || faction.equalsIgnoreCase("none")) return null;
        switch (faction.toLowerCase()) {
            case "ukraine": return FLAG_UKRAINE;
            case "russia": return FLAG_RUSSIA;
            case "usa": return FLAG_USA;
            case "nato": return FLAG_NATO;
            case "bluefor": return FLAG_BLUEFOR;
            case "redfor": return FLAG_REDFOR;
            case "insurgency": return FLAG_INSURGENCY;
            case "pmc": return FLAG_PMC;
            case "germany": return FLAG_GERMANY;
            case "militia": return FLAG_MILITIA;
            default: return null;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}