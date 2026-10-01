package com.example.aas.client.gui;

import com.example.aas.client.ClientData;
import com.example.aas.network.PlayerStatInfo;
import com.example.aas.util.VehicleCatalog;
import com.example.aas.world.AASWorldData;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
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

    // ИКОНКИ ОЧКОВ И ТЕХНИКИ — PNG 10x10 (assets/aas/textures/gui/stats/). Если файла нет, рисуется цветной квадрат
    private static final ResourceLocation TP_ICON = new ResourceLocation("aas", "textures/gui/stats/team_points.png");
    private static final ResourceLocation SP_ICON = new ResourceLocation("aas", "textures/gui/stats/squad_points.png");
    private static final ResourceLocation VEH_ICON = new ResourceLocation("aas", "textures/gui/stats/vehicle_kills.png");
    private static final ResourceLocation REV_ICON = new ResourceLocation("aas", "textures/gui/stats/revives.png"); // иконку рисует автор мода

    // Иконки категорий техники для подсказки - те же, что в панели списка техники (textures/gui/map_icons/*.png)
    private static final ResourceLocation[] VEH_CATEGORY_ICONS;
    static {
        VehicleCatalog.Category[] cats = VehicleCatalog.Category.values();
        VEH_CATEGORY_ICONS = new ResourceLocation[cats.length];
        for (int i = 0; i < cats.length; i++) {
            String file = switch (cats[i]) {
                case LOGISTICS -> "supply_truck";
                case TRANSPORT -> "infantry_vehicle";
                case APC -> "apc";
                case IFV -> "combat_vehicle";
                case TANK -> "tank";
                case SPG -> "spg";
                case AIR_DEFENSE -> "mobile_zu";
                case HELICOPTER -> "helicopter";
                case AIRCRAFT -> "cas_fighter";
                default -> "default";
            };
            VEH_CATEGORY_ICONS[i] = new ResourceLocation("aas", "textures/gui/map_icons/" + file + ".png");
        }
    }
    private static final int VEH_TIP_ICON = 12;   // размер иконки техники в подсказке
    // Размер текстуры (для масштабирования иконок разного размера), кэш
    private static final Map<ResourceLocation, int[]> TEX_SIZE = new HashMap<>();
    private static final int[] TEX_MISSING = new int[0];
    // Кэш "есть ли такая текстура" (чтобы не рисовать фиолетово-чёрную заглушку и не падать)
    private static final Map<ResourceLocation, Boolean> ICON_PRESENT = new HashMap<>();

    private static final int ROW_HEIGHT = 12;
    private static final int TOP_MARGIN = 34;   // отступ под заголовок экрана
    private static final int BOTTOM_MARGIN = 8;

    // ================= СТИЛЬ ТАБЛИЦЫ (все цвета и размеры здесь, правятся в одном месте) =================
    // Колонки слева направо: TP | SP | поднято | техника | киллы | смерти
    private static final int COL_TP = 0;
    private static final int COL_SP = 1;
    private static final int COL_REV = 2;
    private static final int COL_VEH = 3;
    private static final int COL_KILLS = 4;
    private static final int COL_DEATHS = 5;
    private static final int COL_COUNT = 6;
    // Сколько цифр резервируем под число в колонке (фиксированная ширина, TP и SP до 5 цифр)
    private static final int[] COL_DIGITS = {5, 5, 3, 3, 4, 4};
    private static final int[] COL_CAP = {99999, 99999, 999, 999, 9999, 9999};
    private static final int ICON_SIZE = 10;
    private static final int COL_GAP = 8;       // между колонками

    private static final int COLOR_TP = 0xFFFFFFFF;
    private static final int COLOR_SP = 0xFFFFFFFF;
    private static final int COLOR_VEH = 0xFFFFFFFF;
    private static final int COLOR_KILLS = 0xFFFFFFFF;
    private static final int COLOR_DEATHS = 0xFFFFFFFF;
    private static final int COLOR_REV = 0xFFFFFFFF;

    // Иконки и цвета колонок по индексу (иконки рисуются только в шапке таблицы, в строках игроков - только числа)
    private static final ResourceLocation[] COL_ICONS = {TP_ICON, SP_ICON, REV_ICON, VEH_ICON, KILL_ICON, DEATH_ICON};
    private static final int[] COL_COLORS = {COLOR_TP, COLOR_SP, COLOR_REV, COLOR_VEH, COLOR_KILLS, COLOR_DEATHS};

    // Шапка с иконками колонок
    private static final int HEADER_BG = 0x33000000;
    private static final int HEADER_LIFT = 4;   // на сколько пикселей шапка поднята вверх

    private static final int ROW_BG_EVEN = 0x22FFFFFF;      // зебра: чётные строки
    private static final int ROW_BG_ODD = 0x11000000;       // зебра: нечётные строки
    private static final int ROW_SEPARATOR = 0x15FFFFFF;    // тонкая линия между строками (1px)
    private static final int ROW_HOVER = 0x33FFFFFF;        // строка под курсором
    private static final int ROW_SELF_BG_BLUE = 0x593B82F6; // своя строка, синяя команда (~35%)
    private static final int ROW_SELF_BG_RED = 0x59E0383E;  // своя строка, красная команда (~35%)
    private static final int ROW_SELF_BG_NEUTRAL = 0x59AAAAAA;
    private static final int ROW_SELF_STRIPE_BLUE = 0xFF3B82F6;
    private static final int ROW_SELF_STRIPE_RED = 0xFFE0383E;
    private static final int ROW_SELF_STRIPE_NEUTRAL = 0xFFCCCCCC;
    private static final int ROW_SELF_STRIPE_WIDTH = 2;
    private static final int GUIDE_COLOR = 0x40FFFFFF;      // вертикальные направляющие между колонками (1px)
    private static final int COL_TINT = 0x1AFFFFFF;         // лёгкая заливка каждой второй колонки (выделение столбцов)

    private static final int TOOLTIP_BG = 0xF0100010;
    private static final int TOOLTIP_BORDER = 0xF05000FF;

    private static final String[] COL_LANG_KEYS = {
            "aas.gui.stats.col.tp", "aas.gui.stats.col.sp", "aas.gui.stats.col.revives", "aas.gui.stats.col.vehicles",
            "aas.gui.stats.col.kills", "aas.gui.stats.col.deaths"
    };

    private int scrollOffset = 0;
    private int maxScroll = 0;

    // Раскладка колонок (считается один раз в init, не в render)
    private final int[] colWidth = new int[COL_COUNT];
    private final int[] colLeftOffset = new int[COL_COUNT];   // расстояние от правого края до левого края колонки
    private final int[] guideOffset = new int[COL_COUNT - 1]; // расстояние от правого края до вертикальной линии
    private int statsTotalWidth = 0;

    // Состояние мыши/подсказки текущего кадра
    private int frameMouseX = 0;
    private int frameMouseContentY = 0;   // Y мыши в координатах контента (с учётом прокрутки)
    private boolean mouseInViewport = false;
    private int tipColumn = -1;
    private PlayerStatInfo tipStat = null;

    // Режим «Счёт» с экрана победы: показываем всё, как админу-наблюдателю (враги, их отряды и статистика),
    // а закрытие (ESC) возвращает на экран победы.
    private final boolean revealAll;
    private final Screen parent;

    public StatisticsScreen() {
        this(null, false);
    }

    public StatisticsScreen(Screen parent, boolean revealAll) {
        super(Component.literal("Statistics"));
        this.parent = parent;
        this.revealAll = revealAll;
    }

    /** true, если экран открыт кнопкой «Счёт» с экрана победы. */
    public boolean isOpenedFromVictory() {
        return parent instanceof VictoryScreen;
    }

    @Override
    public void onClose() {
        if (parent != null) {
            this.minecraft.setScreen(parent);
        } else {
            super.onClose();
        }
    }

    @Override
    protected void init() {
        // управляющих виджетов пока нет — экран информационный

        // Раскладка колонок статистики: справа налево, фиксированная ширина под максимум цифр
        ICON_PRESENT.clear();
        int offset = 0;
        for (int i = COL_COUNT - 1; i >= 0; i--) {
            int numW = this.font.width("9".repeat(COL_DIGITS[i]));
            colWidth[i] = Math.max(ICON_SIZE, numW); // иконка в шапке по центру колонки, числа в строках - по центру клетки
            offset += colWidth[i];
            colLeftOffset[i] = offset;
            offset += COL_GAP;
        }
        statsTotalWidth = offset - COL_GAP;
        for (int i = 0; i < COL_COUNT - 1; i++) {
            guideOffset[i] = colLeftOffset[i + 1] + COL_GAP / 2;
        }
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

        // Мышь в координатах контента (экран прокручивается translate'ом)
        frameMouseX = mouseX;
        frameMouseContentY = mouseY + scrollOffset;
        mouseInViewport = mouseY >= contentTop && mouseY < contentBottom;
        tipColumn = -1;
        tipStat = null;

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

        // Подсказка колонки рисуется поверх всего, вне прокрутки и scissor
        if (tipColumn >= 0) renderColumnTooltip(gui, mouseX, mouseY);
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

        // Шапка с иконками колонок - один раз для команды (если есть видимая статистика)
        if (teamPlayers.stream().anyMatch(pl -> !pl.isHidden())) {
            y = drawColumnHeader(gui, x, width, y);
        }

        boolean isMine = !getMyTeamKey().isEmpty() && teamKey.equalsIgnoreCase(getMyTeamKey());
        boolean revealed = isTeamRevealed(isMine);

        if (!revealed) {
            // Минимальный режим для врагов: ник + пинг. Статистика (K/D) рисуется только если сервер её прислал
            int enemyRow = 0;
            String enemyMyName = (this.minecraft.player != null) ? this.minecraft.player.getScoreboardName() : "";
            for (PlayerStatInfo p : teamPlayers) {
                boolean hasStats = !p.isHidden();
                // Порядок: фон строки -> разделители -> текст и иконки
                drawRowBackground(gui, x, y, width, ROW_HEIGHT, enemyRow, p.name.equals(enemyMyName), isRowHovered(x, y, width), teamKey, !hasStats);
                int nameX = drawPingBars(gui, x, y + 2, p.ping);
                drawNameTruncated(gui, p.name, nameX, y + 2, 0xFFDDDDDD, nameMaxRight(x, width, hasStats));
                drawStats(gui, x + width, y + 2, p); // <-- Добавлена отрисовка всех 6 колонок (скрыто, если сервер прислал -1)
                y += ROW_HEIGHT;
                enemyRow++;
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

            int memberRow = 0; // зебра начинается заново в каждом отряде
            for (String member : ordered) {
                PlayerStatInfo stat = byName.get(member);
                boolean isLeaderRow = member.equals(squad.leader);
                boolean hasStats = stat != null && !stat.isHidden();

                // Порядок: фон строки -> разделители -> текст и иконки
                drawRowBackground(gui, x, y, width, ROW_HEIGHT, memberRow, member.equals(myName), isRowHovered(x, y, width), teamKey, !hasStats);

                int nameX = drawPingBars(gui, x + 6, y + 2, stat != null ? stat.ping : -1);

                int nameColor = isLeaderRow ? 0xFFFFD700 : 0xFFFFFFFF;
                drawNameTruncated(gui, (isLeaderRow ? "SL " : "   ") + member, nameX, y + 2, nameColor, nameMaxRight(x, width, hasStats));

                if (stat != null) {
                    drawStats(gui, x + width, y + 2, stat);
                }
                y += ROW_HEIGHT;
                memberRow++;
            }
            y += 4;
            index++;
        }

        // Игроки этой команды без отряда
        List<PlayerStatInfo> noSquad = teamPlayers.stream().filter(p -> p.squadId == -1).collect(Collectors.toList());
        if (!noSquad.isEmpty()) {
            gui.drawString(this.font, Component.translatable("aas.gui.stats.unassigned_players").getString(), x, y, 0xFF888888, false);
            y += ROW_HEIGHT;
            int noSquadRow = 0; // зебра начинается заново в блоке "без отряда"
            for (PlayerStatInfo p : noSquad) {
                boolean hasStats = !p.isHidden();
                drawRowBackground(gui, x, y, width, ROW_HEIGHT, noSquadRow, p.name.equals(myName), isRowHovered(x, y, width), teamKey, !hasStats);
                int nameX = drawPingBars(gui, x + 6, y + 2, p.ping);
                drawNameTruncated(gui, p.name, nameX, y + 2, 0xFFFFFFFF, nameMaxRight(x, width, hasStats));
                drawStats(gui, x + width, y + 2, p);
                y += ROW_HEIGHT;
                noSquadRow++;
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
        if (noTeam.stream().anyMatch(pl -> !pl.isHidden())) {
            y = drawColumnHeader(gui, x, width, y);
        }

        String noTeamMyName = (this.minecraft.player != null) ? this.minecraft.player.getScoreboardName() : "";
        int noTeamRow = 0;
        for (PlayerStatInfo p : noTeam) {
            boolean hasStats = !p.isHidden();
            drawRowBackground(gui, x, y, width, ROW_HEIGHT, noTeamRow, p.name.equals(noTeamMyName), isRowHovered(x, y, width), "", !hasStats);
            int nameX = drawPingBars(gui, x, y + 2, p.ping);
            drawNameTruncated(gui, p.name, nameX, y + 2, 0xFFFFFFFF, nameMaxRight(x, width, hasStats));
            drawStats(gui, x + width, y + 2, p);
            y += ROW_HEIGHT;
            noTeamRow++;
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
        if (revealAll) return true;
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

    // ================= СТАТИСТИКА: 6 КОЛОНОК (TP | SP | поднято | техника | киллы | смерти) =================

    // Числа по центру клеток. Колонки считаются справа налево от rightEdgeX
    // с фиксированной шириной, поэтому выравниваются между строками. Иконки рисуются только в шапке.
    // y - координата текста (верх строки + 2). Скрытая статистика врага (значения -1) не рисуется совсем.
    private void drawStats(GuiGraphics gui, int rightEdgeX, int y, PlayerStatInfo p) {
        if (p == null || p.isHidden()) return; // скрытая статистика врага

        int rowTop = y - 2;
        boolean rowUnderMouse = mouseInViewport && frameMouseContentY >= rowTop && frameMouseContentY < rowTop + ROW_HEIGHT;

        for (int c = COL_COUNT - 1; c >= 0; c--) { // справа налево: смерти, киллы, техника, поднято, SP, TP
            int value;
            switch (c) {
                case COL_TP:     value = p.tp;            break;
                case COL_SP:     value = p.sp;            break;
                case COL_REV:    value = p.revives;       break;
                case COL_VEH:    value = p.vehKillsTotal; break;
                case COL_KILLS:  value = p.kills;         break;
                default:         value = p.deaths;        break;
            }
            int color = COL_COLORS[c];

            int colLeft = rightEdgeX - colLeftOffset[c];
            int colRight = colLeft + colWidth[c];

            // Только число по центру клетки (иконки теперь в шапке)
            String text = String.valueOf(Math.min(value, COL_CAP[c]));
            gui.drawString(this.font, text, colLeft + (colWidth[c] - this.font.width(text)) / 2, y, color, false);

            // Подсказка: наведение на число колонки (не зависит от подсветки строки, поэтому не мигает)
            if (rowUnderMouse && frameMouseX >= colLeft - COL_GAP / 2 && frameMouseX < colRight + COL_GAP / 2) {
                tipColumn = c;
                tipStat = p;
            }
        }
    }

    // Шапка таблицы: иконки колонок рисуются один раз над всеми строками команды.
    // Возвращает новый Y (после шапки). Наведение на иконку показывает название колонки.
    private int drawColumnHeader(GuiGraphics gui, int x, int width, int y) {
        int right = x + width;
        int h = ROW_HEIGHT;
        int returnY = y + h + 1;
        y -= HEADER_LIFT; // шапка чуть выше, чтобы не залезать на кружок отряда

        gui.fill(x, y, right, y + h, HEADER_BG);
        drawColumnTints(gui, right, y, h);
        drawColumnGuides(gui, right, y, h);

        boolean rowUnderMouse = mouseInViewport && frameMouseContentY >= y && frameMouseContentY < y + h;
        for (int c = 0; c < COL_COUNT; c++) {
            int colLeft = right - colLeftOffset[c];
            int iconX = colLeft + (colWidth[c] - ICON_SIZE) / 2;
            drawIcon(gui, COL_ICONS[c], iconX, y + 1, COL_COLORS[c]);

            if (rowUnderMouse && frameMouseX >= colLeft - COL_GAP / 2 && frameMouseX < colLeft + colWidth[c] + COL_GAP / 2) {
                tipColumn = c;
                tipStat = null; // шапка: только название колонки
            }
        }
        return returnY;
    }

    // Лёгкая заливка каждой второй колонки (чтобы столбцы читались как столбцы)
    private void drawColumnTints(GuiGraphics gui, int right, int y, int h) {
        for (int c = 1; c < COL_COUNT; c += 2) {
            int x0 = right - colLeftOffset[c] - COL_GAP / 2;
            int x1 = Math.min(right, right - colLeftOffset[c] + colWidth[c] + COL_GAP / 2);
            gui.fill(x0, y, x1, y + h, COL_TINT);
        }
    }

    // Вертикальные направляющие между колонками (1px)
    private void drawColumnGuides(GuiGraphics gui, int right, int y, int h) {
        for (int i = 0; i < guideOffset.length; i++) {
            int gx = right - guideOffset[i];
            gui.fill(gx, y, gx + 1, y + h, GUIDE_COLOR);
        }
    }

    // Иконка 10x10. Если текстуры нет в ресурсах, рисуем цветной квадрат вместо заглушки
    private void drawIcon(GuiGraphics gui, ResourceLocation icon, int x, int y, int fallbackColor) {
        if (iconExists(icon)) {
            RenderSystem.enableBlend();
            // Белая иконка окрашивается в цвет колонки
            RenderSystem.setShaderColor(((fallbackColor >> 16) & 0xFF) / 255f, ((fallbackColor >> 8) & 0xFF) / 255f, (fallbackColor & 0xFF) / 255f, 1f);
            gui.blit(icon, x, y, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        } else {
            gui.fill(x + 1, y + 1, x + ICON_SIZE - 1, y + ICON_SIZE - 1, fallbackColor);
        }
    }

    private boolean iconExists(ResourceLocation icon) {
        Boolean cached = ICON_PRESENT.get(icon);
        if (cached == null) {
            cached = this.minecraft.getResourceManager().getResource(icon).isPresent();
            ICON_PRESENT.put(icon, cached);
        }
        return cached;
    }

    // Обрезка длинного ника с "…", чтобы имя не залезало на колонки
    private void drawNameTruncated(GuiGraphics gui, String text, int x, int y, int color, int maxRight) {
        int avail = maxRight - x;
        if (avail <= 0) return;
        if (this.font.width(text) <= avail) {
            gui.drawString(this.font, text, x, y, color, false);
        } else {
            String cut = this.font.plainSubstrByWidth(text, Math.max(0, avail - this.font.width("\u2026"))) + "\u2026";
            gui.drawString(this.font, cut, x, y, color, false);
        }
    }

    // Правая граница области ника: слева от колонок статистики (если они рисуются)
    private int nameMaxRight(int x, int width, boolean hasStats) {
        return hasStats ? x + width - statsTotalWidth - 4 : x + width - 2;
    }

    private boolean isRowHovered(int x, int y, int width) {
        return mouseInViewport && frameMouseX >= x && frameMouseX < x + width
                && frameMouseContentY >= y && frameMouseContentY < y + ROW_HEIGHT;
    }

    // ================= ФОН СТРОКИ =================

    // Единый метод фона строки игрока. Вызывается ДО текста и иконок.
    // Порядок: зебра -> подсветка своей строки -> наведение -> разделитель -> вертикальные направляющие.
    // dimmed = true (скрытые данные врага / нет данных): только зебра и разделитель, без яркой подсветки и направляющих.
    private void drawRowBackground(GuiGraphics gui, int x, int y, int width, int rowHeight, int rowIndex,
                                   boolean isSelf, boolean isHovered, String teamKey, boolean dimmed) {
        int right = x + width;

        // 1. Зебра
        gui.fill(x, y, right, y + rowHeight, (rowIndex & 1) == 0 ? ROW_BG_EVEN : ROW_BG_ODD);
        // 1.1. Заливка каждой второй колонки (только для строк с данными)
        if (!dimmed) drawColumnTints(gui, right, y, rowHeight);

        if (!dimmed) {
            // 2. Своя строка: фон цвета команды (~35%) + полоска 2px слева
            if (isSelf) {
                int bg;
                int stripe;
                if ("BLUE".equalsIgnoreCase(teamKey)) { bg = ROW_SELF_BG_BLUE; stripe = ROW_SELF_STRIPE_BLUE; }
                else if ("RED".equalsIgnoreCase(teamKey)) { bg = ROW_SELF_BG_RED; stripe = ROW_SELF_STRIPE_RED; }
                else { bg = ROW_SELF_BG_NEUTRAL; stripe = ROW_SELF_STRIPE_NEUTRAL; }
                gui.fill(x, y, right, y + rowHeight, bg);
                gui.fill(x, y, x + ROW_SELF_STRIPE_WIDTH, y + rowHeight, stripe);
            }
            // 3. Наведение мыши
            if (isHovered) {
                gui.fill(x, y, right, y + rowHeight, ROW_HOVER);
            }
        }

        // 4. Тонкий разделитель между строками
        gui.fill(x, y + rowHeight - 1, right, y + rowHeight, ROW_SEPARATOR);

        // 5. Вертикальные направляющие между колонками (только в области колонок, на ник не заходят)
        if (!dimmed) {
            drawColumnGuides(gui, right, y, rowHeight);
        }
    }

    // ================= ПОДСКАЗКИ КОЛОНОК =================

    private void renderColumnTooltip(GuiGraphics gui, int mouseX, int mouseY) {
        Component title = Component.translatable(COL_LANG_KEYS[tipColumn]);

        if (tipColumn != COL_VEH || tipStat == null) {
            gui.renderTooltip(this.font, title, mouseX, mouseY);
            return;
        }

        // Колонка техники: название + разбивка по категориям: [иконка] [класс техники] [сколько уничтожено]
        VehicleCatalog.Category[] cats = VehicleCatalog.Category.values();
        int[] counts = tipStat.vehKillsByCategory;

        int rows = 0;
        int nameW = 0;
        int numW = 0;
        for (int i = 0; i < cats.length && i < counts.length; i++) {
            if (counts[i] <= 0) continue;
            rows++;
            nameW = Math.max(nameW, this.font.width(Component.translatable(cats[i].langKey)));
            numW = Math.max(numW, this.font.width(String.valueOf(counts[i])));
        }
        int rowH = VEH_TIP_ICON + 2;
        int textW = Math.max(this.font.width(title), rows > 0 ? VEH_TIP_ICON + 4 + nameW + 10 + numW : 0);

        int boxW = textW + 8;
        int boxH = 8 + 10 + (rows > 0 ? rows * rowH + 2 : 0);
        int bx = mouseX + 12;
        int by = mouseY - 12;
        if (bx + boxW > this.width) bx = mouseX - boxW - 12;
        if (bx < 0) bx = 0;
        if (by + boxH > this.height) by = this.height - boxH;
        if (by < 0) by = 0;

        gui.pose().pushPose();
        gui.pose().translate(0, 0, 400);
        gui.fill(bx - 1, by - 1, bx + boxW + 1, by + boxH + 1, TOOLTIP_BORDER);
        gui.fill(bx, by, bx + boxW, by + boxH, TOOLTIP_BG);
        gui.drawString(this.font, title, bx + 4, by + 4, 0xFFFFFFFF, false);

        int ry = by + 4 + 10 + 2;
        for (int i = 0; i < cats.length && i < counts.length; i++) {
            if (counts[i] <= 0) continue;
            drawScaledTexture(gui, VEH_CATEGORY_ICONS[i], bx + 4, ry, VEH_TIP_ICON);
            gui.drawString(this.font, Component.translatable(cats[i].langKey), bx + 4 + VEH_TIP_ICON + 4, ry + 2, 0xFFFFFFFF, false);
            String num = String.valueOf(counts[i]);
            gui.drawString(this.font, num, bx + boxW - 4 - this.font.width(num), ry + 2, 0xFFFFFFFF, false);
            ry += rowH;
        }
        gui.pose().popPose();
    }

    // Иконка любого размера, масштабируется до size x size (как в панели списка техники); если файла нет - серая заглушка
    private void drawScaledTexture(GuiGraphics gui, ResourceLocation tex, int x, int y, int size) {
        int[] info = texSize(tex);
        if (info != null) {
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            gui.blit(tex, x, y, size, size, 0f, 0f, info[0], info[1], info[0], info[1]);
        } else {
            gui.fill(x, y, x + size, y + size, 0xFF3A3A3A);
            gui.renderOutline(x, y, size, size, 0xFF888888);
        }
    }

    private int[] texSize(ResourceLocation tex) {
        int[] cached = TEX_SIZE.get(tex);
        if (cached != null) return cached == TEX_MISSING ? null : cached;
        int[] info = TEX_MISSING;
        var res = this.minecraft.getResourceManager().getResource(tex);
        if (res.isPresent()) {
            info = new int[]{16, 16};
            try (InputStream in = res.get().open(); NativeImage img = NativeImage.read(in)) {
                info = new int[]{img.getWidth(), img.getHeight()};
            } catch (IOException ignored) {
                // оставляем 16x16
            }
        }
        TEX_SIZE.put(tex, info);
        return info == TEX_MISSING ? null : info;
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