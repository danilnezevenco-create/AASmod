package com.example.aas.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public class FirstPersonArmFixMixin {

    @Inject(method = "renderArmWithItem", at = @At("HEAD"))
    private void aas$forceShowArm(AbstractClientPlayer player, float partialTicks, float pitch, InteractionHand hand, float swingProgress, ItemStack stack, float equipProgress, PoseStack poseStack, MultiBufferSource buffer, int combinedLight, CallbackInfo ci) {

        // Если рука пустая (значит, игра пытается нарисовать голую руку)
        if (stack.isEmpty()) {
            EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
            PlayerRenderer renderer = (PlayerRenderer) dispatcher.getRenderer(player);
            PlayerModel<AbstractClientPlayer> model = renderer.getModel();

            // Определяем, какую руку рисуем
            HumanoidArm mainArm = player.getMainArm();
            boolean isRightArm = (hand == InteractionHand.MAIN_HAND) ? (mainArm == HumanoidArm.RIGHT) : (mainArm == HumanoidArm.LEFT);

            // Принудительно включаем видимость руки в модели, чтобы она не исчезала из-за других миксинов
            if (isRightArm) {
                model.rightArm.visible = true;
                model.rightSleeve.visible = true;
            } else {
                model.leftArm.visible = true;
                model.leftSleeve.visible = true;
            }
        }
    }
}