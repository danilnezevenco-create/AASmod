package com.example.aas.client.gui;

import com.example.aas.network.PacketHandler;
import com.example.aas.network.PacketOpenPlayerKitMenu;
import com.example.aas.network.PacketSelectKit;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PlayerKitSelectScreen extends Screen {
    private final List<PacketOpenPlayerKitMenu.KitDTO> kits;

    // === СЕТКА КИТОВ (внутри одной категории) ===
    private static final int COLUMNS = 4;
    private static final int TILE_WIDTH = 100;
    private static final int BUTTON_WIDTH = 90;
    private static final int ROW_HEIGHT = 60; // высота ряда (иконка + кнопка)
    private static final int GRID_WIDTH = COLUMNS * TILE_WIDTH; // 400

    // === КОЛОНКА ИКОНКИ РАЗДЕЛА (слева) ===
    private static final int ICON_COL_WIDTH = 78;
    private static final int ICON_SIZE = 32;
    private static final int DIVIDER_GAP = 14; // отступ между иконкой-раздела и сеткой китов (включая линию)
    private static final int SECTION_GAP = 18; // отступ между разделами

    private static final int TOTAL_WIDTH = ICON_COL_WIDTH + DIVIDER_GAP + GRID_WIDTH;
    private static final int TOP_Y = 40;

    private static final Component EYE_ICON = Component.literal("\uD83D\uDC41");

    // === Цветовая схема кнопок в стиле "APPLY FOR CMD" (лайм-зелёный) ===
    private static final int BTN_BG = 0xCC111111;
    private static final int BTN_BORDER_IDLE = 0xFF4E7A16;   // приглушённый лайм в покое
    private static final int BTN_BORDER_HOVER = 0xFF9AFF00;  // яркий лайм при наведении
    private static final int BTN_BORDER_DISABLED = 0xFF3A3A3A;

    // === Описание 5 разделов ===
    // kitOrder == null  =>  раздел собирается динамически из всех китов с isOfficer == true
    private static final class CategoryDef {
        final String titleKey;
        final String iconFile;
        final List<String> kitOrder;

        CategoryDef(String titleKey, String iconFile, List<String> kitOrder) {
            this.titleKey = titleKey;
            this.iconFile = iconFile;
            this.kitOrder = kitOrder;
        }
    }

    private static final List<CategoryDef> CATEGORY_DEFS = List.of(
            new CategoryDef("aas.gui.kit.category.officer", "category_officer", null),
            new CategoryDef("aas.gui.kit.category.rifle", "category_rifle",
                    List.of("Rifleman", "Medic", "LMG", "HMG", "Marksman")),
            new CategoryDef("aas.gui.kit.category.explosive", "category_explosive",
                    List.of("LAT", "Grenadier", "Sapper")),
            new CategoryDef("aas.gui.kit.category.special", "category_special",
                    List.of("HAT", "Assault", "Scout", "Drone Operator", "Sniper", "Anti_air")),
            new CategoryDef("aas.gui.kit.category.support", "category_support",
                    List.of("Pilot", "Mechanic"))
    );
    // Страховочный раздел — сюда попадут киты, не привязанные ни к одной из категорий выше
    // (например если в будущем добавят новый кит и забудут обновить этот экран).
    private static final CategoryDef OTHER_CATEGORY =
            new CategoryDef("aas.gui.kit.category.other", "category_other", List.of());

    // Секция, посчитанная на текущий набор китов
    private static final class Section {
        final CategoryDef def;
        final List<PacketOpenPlayerKitMenu.KitDTO> kits = new ArrayList<>();
        int y;
        int height;

        Section(CategoryDef def) {
            this.def = def;
        }
    }

    private final List<Section> sections = new ArrayList<>();
    private final Map<String, int[]> kitPos = new HashMap<>(); // kitName -> {x, y} БАЗОВОГО (не прокрученного) угла тайла
    private int startX;
    private int gridX;
    private int dividerX;

    // === Скролл колёсиком, если контент не влезает по высоте ===
    private static final int VIEWPORT_TOP = 26;    // где начинается прокручиваемая область (под заголовком)
    private static final int VIEWPORT_BOTTOM_PAD = 6; // отступ снизу экрана
    private static final int SCROLL_STEP = 22;     // px за один "щелчок" колеса
    private int scrollOffset = 0;      // сколько px уже прокручено вниз
    private int contentHeight = 0;     // полная высота контента (все секции), считается в computeLayout()

    private int viewportBottom() { return this.height - VIEWPORT_BOTTOM_PAD; }
    private int maxScroll() { return Math.max(0, contentHeight - (viewportBottom() - VIEWPORT_TOP)); }

    // === Выбор варианта (стандарт/альт) ===
    // Имя кита, для которого сейчас показан выбор STANDARD/ALTERNATIVE. null если нет.
    private String variantPopupKitName = null;
    private boolean variantPopupIsPreview = false; // открыт ли попап для превью (глаз) или выбора кита

    public PlayerKitSelectScreen(List<PacketOpenPlayerKitMenu.KitDTO> kits) {
        super(Component.translatable("aas.gui.kit.select.title"));
        this.kits = new java.util.ArrayList<>(kits);
    }

    // === Кнопка в стиле "APPLY FOR CMD" (тёмная плашка + лайм-зелёная рамка), текст не меняется ===
    private static class AasFlatButton extends Button {
        AasFlatButton(int x, int y, int width, int height, Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        }

        @Override
        protected void renderWidget(GuiGraphics gui, int mouseX, int mouseY, float partialTicks) {
            if (!this.visible) return;

            int borderColor = this.isHovered() ? BTN_BORDER_HOVER : BTN_BORDER_IDLE;
            if (!this.active) borderColor = BTN_BORDER_DISABLED;

            gui.fill(getX(), getY(), getX() + width, getY() + height, BTN_BG);
            gui.renderOutline(getX(), getY(), width, height, borderColor);

            int textColor = this.active ? 0xFFFFFFFF : 0xFF777777;
            gui.drawCenteredString(Minecraft.getInstance().font, this.getMessage(), getX() + width / 2, getY() + (height - 8) / 2, textColor);

            if (this.active && this.isHovered()) {
                gui.fill(getX(), getY() + height - 2, getX() + 2, getY() + height, BTN_BORDER_HOVER);
            }
        }
    }

    // === Сборка секций и расчёт координат (вызывается из init(), т.к. ширина экрана может поменяться) ===
    private void computeLayout() {
        sections.clear();
        kitPos.clear();

        Set<String> used = new HashSet<>();

        for (CategoryDef def : CATEGORY_DEFS) {
            Section section = new Section(def);
            if (def.kitOrder == null) {
                // Динамическая категория "Офицеры" — берём все киты с галочкой isOfficer.
                // Игрокам, не являющимся лидерами отряда, сервер уже не отправляет такие киты,
                // поэтому раздел сам скроется, если он им не доступен.
                for (PacketOpenPlayerKitMenu.KitDTO kit : kits) {
                    if (kit.isOfficer) {
                        section.kits.add(kit);
                        used.add(kit.name);
                    }
                }
            } else {
                for (String name : def.kitOrder) {
                    for (PacketOpenPlayerKitMenu.KitDTO kit : kits) {
                        if (kit.name.equals(name) && !kit.isOfficer) {
                            section.kits.add(kit);
                            used.add(kit.name);
                            break;
                        }
                    }
                }
            }
            if (!section.kits.isEmpty()) sections.add(section);
        }

        // Страховочный раздел для не распознанных китов
        Section other = new Section(OTHER_CATEGORY);
        for (PacketOpenPlayerKitMenu.KitDTO kit : kits) {
            if (!used.contains(kit.name)) other.kits.add(kit);
        }
        if (!other.kits.isEmpty()) sections.add(other);

        startX = (width - TOTAL_WIDTH) / 2;
        dividerX = startX + ICON_COL_WIDTH + DIVIDER_GAP / 2;
        gridX = startX + ICON_COL_WIDTH + DIVIDER_GAP;

        int y = TOP_Y;
        for (Section section : sections) {
            int rows = (section.kits.size() + COLUMNS - 1) / COLUMNS;
            int h = rows * ROW_HEIGHT;
            section.y = y;
            section.height = h;

            for (int i = 0; i < section.kits.size(); i++) {
                int row = i / COLUMNS;
                int col = i % COLUMNS;
                int x = gridX + col * TILE_WIDTH;
                int ky = y + row * ROW_HEIGHT + 26;
                kitPos.put(section.kits.get(i).name, new int[]{x, ky});
            }

            y += h + SECTION_GAP;
        }
        contentHeight = y - SECTION_GAP - TOP_Y; // высота всего контента без верхнего отступа и лишнего хвостового gap
    }

    @Override
    protected void init() {
        computeLayout();
        // Ширина/высота экрана только что посчитаны заново — прокрутка могла стать невалидной (например
        // окно увеличили и всё влезло) — подрезаем на всякий случай.
        scrollOffset = Math.min(scrollOffset, maxScroll());
        rebuildKitWidgets();
    }

    // Пересобирает все кнопки с учётом текущего scrollOffset. Вызывается из init() и из mouseScrolled(),
    // т.к. позиции виджетов Minecraft-кнопок нельзя просто "сдвинуть" через GuiGraphics — их нужно
    // на самом деле переместить.
    private void rebuildKitWidgets() {
        this.clearWidgets();

        for (Section section : sections) {
            for (PacketOpenPlayerKitMenu.KitDTO kit : section.kits) {
                int[] pos = kitPos.get(kit.name);
                int x = pos[0];
                int y = pos[1] - scrollOffset;

                // Основная кнопка выбора
                AasFlatButton btn = new AasFlatButton(x, y, BUTTON_WIDTH - 20, 20, Component.literal(kit.name), b -> {
                    if (kit.hasAlt) {
                        // Есть альтернативная версия — показываем выбор вместо немедленного выбора
                        variantPopupKitName = (kit.name.equals(variantPopupKitName) && !variantPopupIsPreview) ? null : kit.name;
                        variantPopupIsPreview = false;
                        rebuildKitWidgets(); // показать/скрыть попап без потери scrollOffset
                    } else {
                        PacketHandler.INSTANCE.sendToServer(new PacketSelectKit(kit.name, false));
                        this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(com.example.aas.sound.ModSounds.KIT_SELECT.get(), 1.0F));
                        this.onClose();
                    }
                });

                btn.active = kit.available;
                this.addRenderableWidget(btn);

                // КНОПКА ГЛАЗИК (превью)
                this.addRenderableWidget(new AasFlatButton(x + BUTTON_WIDTH - 18, y, 20, 20, EYE_ICON, b -> {
                    if (kit.hasAlt) {
                        variantPopupKitName = (kit.name.equals(variantPopupKitName) && variantPopupIsPreview) ? null : kit.name;
                        variantPopupIsPreview = true;
                        rebuildKitWidgets();
                    } else {
                        this.minecraft.setScreen(new KitPreviewScreen(this, kit, false));
                    }
                }));
            }
        }

        rebuildVariantPopup();
    }

    private void rebuildVariantPopup() {
        if (variantPopupKitName == null) return;
        PacketOpenPlayerKitMenu.KitDTO kit = null;
        for (PacketOpenPlayerKitMenu.KitDTO k : kits) {
            if (k.name.equals(variantPopupKitName)) { kit = k; break; }
        }
        if (kit == null || !kit.hasAlt) return;
        int[] pos = kitPos.get(kit.name);
        if (pos == null) return;

        int x = pos[0];
        int y = pos[1] - scrollOffset + 22; // прямо под кнопкой кита
        final PacketOpenPlayerKitMenu.KitDTO fKit = kit;

        if (variantPopupIsPreview) {
            this.addRenderableWidget(new AasFlatButton(x, y, (BUTTON_WIDTH - 20) / 2, 16, Component.literal("Stnd"), b -> {
                variantPopupKitName = null;
                this.minecraft.setScreen(new KitPreviewScreen(this, fKit, false));
            }));

            this.addRenderableWidget(new AasFlatButton(x + (BUTTON_WIDTH - 20) / 2, y, (BUTTON_WIDTH - 20) / 2, 16, Component.literal("Alt"), b -> {
                variantPopupKitName = null;
                this.minecraft.setScreen(new KitPreviewScreen(this, fKit, true));
            }));
        } else {
            this.addRenderableWidget(new AasFlatButton(x, y, (BUTTON_WIDTH - 20) / 2, 16, Component.literal("Stnd"), b -> {
                PacketHandler.INSTANCE.sendToServer(new PacketSelectKit(fKit.name, false));
                this.onClose();
            }));

            this.addRenderableWidget(new AasFlatButton(x + (BUTTON_WIDTH - 20) / 2, y, (BUTTON_WIDTH - 20) / 2, 16, Component.literal("Alt"), b -> {
                PacketHandler.INSTANCE.sendToServer(new PacketSelectKit(fKit.name, true));
                this.onClose();
            }));
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int max = maxScroll();
        if (max <= 0) return super.mouseScrolled(mouseX, mouseY, delta);

        int newOffset = scrollOffset - (int) Math.signum(delta) * SCROLL_STEP;
        newOffset = Math.max(0, Math.min(max, newOffset));
        if (newOffset != scrollOffset) {
            scrollOffset = newOffset;
            rebuildKitWidgets();
        }
        return true;
    }

    @Override
    public void onClose() {
        if (this.minecraft.player != null && !this.minecraft.player.isAlive()) {
            // Если игрок мёртв, при закрытии выбора кита возвращаем его в AASDeathScreen
            this.minecraft.setScreen(new com.example.aas.client.AASDeathScreen(null, false));
        } else {
            super.onClose();
        }
    }

    public void updateKits(List<PacketOpenPlayerKitMenu.KitDTO> newKits) {
        this.kits.clear();
        this.kits.addAll(newKits);

        // Обновляем доступность кнопок без перерисовки всего экрана
        for (net.minecraft.client.gui.components.Renderable widget : this.renderables) {
            if (widget instanceof Button btn) {
                String btnText = btn.getMessage().getString();
                for (PacketOpenPlayerKitMenu.KitDTO kit : this.kits) {
                    if (kit.name.equals(btnText)) {
                        btn.active = kit.available;
                        break;
                    }
                }
            }
        }
    }

    @Override
    public void render(GuiGraphics gui, int mx, int my, float pt) {
        if (!this.minecraft.player.isAlive()) {
            gui.fill(0, 0, this.width, this.height, 0xFF000000);
        } else {
            this.renderBackground(gui);
        }
        gui.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);

        int viewportBottom = viewportBottom();
        // Обрезаем всё, что рисуем ниже, по прокручиваемой области — контент не будет наезжать
        // на заголовок сверху и вылезать за нижний край экрана.
        gui.enableScissor(0, VIEWPORT_TOP, this.width, viewportBottom);

        // === Отрисовка разделов: подложка под иконку + иконка + подпись + вертикальная линия ===
        for (Section section : sections) {
            int sectionY = section.y - scrollOffset;
            int iconX = startX + (ICON_COL_WIDTH - ICON_SIZE) / 2;
            int iconY = sectionY + Math.max(0, (section.height - ICON_SIZE - 12) / 2);

            // Подложка под иконку раздела — в стиле оригинального "APPLY FOR CMD" (тёмная плашка + серая рамка)
            int badgePad = 6;
            int badgeX = iconX - badgePad;
            int badgeY = iconY - badgePad;
            int badgeSize = ICON_SIZE + badgePad * 2;
            gui.fill(badgeX, badgeY, badgeX + badgeSize, badgeY + badgeSize, 0xCC111111);
            gui.renderOutline(badgeX, badgeY, badgeSize, badgeSize, 0xFF999999);

            ResourceLocation iconLoc = new ResourceLocation("aas", "textures/gui/kits/" + section.def.iconFile + ".png");
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            gui.blit(iconLoc, iconX, iconY, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);

            gui.drawCenteredString(font, Component.translatable(section.def.titleKey),
                    startX + ICON_COL_WIDTH / 2, badgeY + badgeSize + 6, 0xFFD9D9D9);

            // Вертикальная разделительная полоска
            gui.fill(dividerX, sectionY, dividerX + 1, sectionY + section.height, 0x55FFFFFF);
        }

        // === Отрисовка иконок китов и индикатора альт-версии ===
        for (Section section : sections) {
            for (PacketOpenPlayerKitMenu.KitDTO kit : section.kits) {
                int[] pos = kitPos.get(kit.name);
                if (pos == null) continue;
                int x = pos[0];
                int y = pos[1] - scrollOffset;

                String iconName = kit.name.toLowerCase().replace(" ", "_").replace("-", "_");
                ResourceLocation iconLoc = new ResourceLocation("aas", "textures/gui/kits/" + iconName + ".png");

                int iconX = x + (BUTTON_WIDTH / 2) - 12 - 5; // -5, т.к. кнопка теперь уже на 20px, центруем по тайлу
                int iconY = y - 26;

                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, kit.available ? 1.0f : 0.4f);
                gui.blit(iconLoc, iconX, iconY, 0, 0, 24, 24, 24, 24);
            }
        }
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        super.render(gui, mx, my, pt);

        gui.disableScissor();

        // Простой индикатор прокрутки справа от сетки (виден только если контент реально не влезает)
        int max = maxScroll();
        if (max > 0) {
            int trackX = Math.min(this.width - 6, gridX + GRID_WIDTH + 6);
            int trackTop = VIEWPORT_TOP;
            int trackHeight = viewportBottom - VIEWPORT_TOP;
            gui.fill(trackX, trackTop, trackX + 3, trackTop + trackHeight, 0x33FFFFFF);

            int thumbHeight = Math.max(12, trackHeight * trackHeight / (trackHeight + max));
            int thumbY = trackTop + (int) ((trackHeight - thumbHeight) * (scrollOffset / (float) max));
            gui.fill(trackX, thumbY, trackX + 3, thumbY + thumbHeight, 0xAAFFFFFF);
        }

        // Тултип причины блокировки
        for (net.minecraft.client.gui.components.Renderable widget : this.renderables) {
            if (widget instanceof Button btn && btn.isHovered() && !btn.active) {
                for (PacketOpenPlayerKitMenu.KitDTO kit : kits) {
                    if (btn.getMessage().getString().equals(kit.name)) {
                        gui.renderTooltip(font, Component.literal(kit.reason), mx, my);
                        break;
                    }
                }
            }
        }
    }
}