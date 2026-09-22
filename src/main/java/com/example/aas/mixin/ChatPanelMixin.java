// PATH: src\main\java\com\example\aas\mixin\ChatPanelMixin.java
package com.example.aas.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ChatComponent.class)
public abstract class ChatPanelMixin {

    // Подключаем нужные поля из ванильного чата для точных математических расчетов
    @Shadow @org.spongepowered.asm.mixin.Final private List<?> trimmedMessages;
    @Shadow public abstract int getLinesPerPage();
    @Shadow public abstract double getScale();
    @Shadow private int chatScrollbarPos;

    // Вычисляем, на сколько пикселей нужно поднять чат,
    // чтобы его верхняя граница упиралась ровно в потолок экрана (Y = 2)
    private int aas$getShiftY() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) return 0;

        // Сколько строк физически отображается сейчас на экране
        int visibleLines = Math.min(this.trimmedMessages.size() - this.chatScrollbarPos, this.getLinesPerPage());
        if (visibleLines <= 0) return 0;

        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int vanillaOriginY = screenHeight - 48; // Стандартный "низ" чата в ванилле

        float scale = (float) this.getScale();

        // Ванилла рисует сообщения снизу вверх:
        // Индекс 0 (самое новое) рисуется на Y = -8
        // Самое старое (самое верхнее) имеет индекс visibleLines - 1
        // Следовательно, его верхняя граница находится на Y = -(visibleLines - 1) * 9 - 8
        float topYRelative = (-(visibleLines - 1) * 9 - 8) * scale;

        // Абсолютная Y-координата самого верхнего пикселя чата на экране
        float absoluteTopY = vanillaOriginY + topYRelative;

        // Нам нужно сдвинуть весь блок вверх так, чтобы absoluteTopY стало равно 2 (отступ от верхнего края)
        int shiftY = (int) (absoluteTopY - 2);

        // Защита от ухода в минус
        return Math.max(0, shiftY);
    }

    // Сдвигаем матрицу отрисовки чата вверх
    @Inject(method = "render", at = @At("HEAD"))
    private void aas$beforeRenderChat(GuiGraphics guiGraphics, int tickCount, int mouseX, int mouseY, CallbackInfo ci) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, -aas$getShiftY(), 0);
    }

    // Возвращаем координаты на место после отрисовки
    @Inject(method = "render", at = @At("RETURN"))
    private void aas$afterRenderChat(GuiGraphics guiGraphics, int tickCount, int mouseX, int mouseY, CallbackInfo ci) {
        guiGraphics.pose().popPose();
    }

    // Корректируем Y-координату мыши, чтобы клики по чату (ссылки, координаты) работали в новом месте
    @ModifyVariable(method = "getClickedComponentStyleAt", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private double aas$modifyClickY(double mouseY) {
        return mouseY + aas$getShiftY();
    }
}