package com.example.aas.mixin;

import com.example.aas.client.ClientData;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.scores.Team;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public class PlayerModelMixin {

    @Inject(method = "setModelProperties", at = @At("RETURN"))
    private void aas$forceModelParts(AbstractClientPlayer player, CallbackInfo ci) {
        PlayerRenderer renderer = (PlayerRenderer) (Object) this;
        PlayerModel<AbstractClientPlayer> model = renderer.getModel();

        Team team = player.getTeam();
        if (team == null) return;

        String faction = "none";
        if (team.getName().equalsIgnoreCase("Blue")) {
            faction = ClientData.BLUE_FACTION;
        } else if (team.getName().equalsIgnoreCase("Red")) {
            faction = ClientData.RED_FACTION;
        }

        // Если игрок во фракции, ПРИНУДИТЕЛЬНО включаем все части модели.
        // Это нужно, чтобы скин фракции отрисовался целиком (включая шлем/каску на слое hat).
        if (faction != null && !faction.equals("none")) {
            model.head.visible = true;
            model.body.visible = true;
            model.leftArm.visible = true;
            model.rightArm.visible = true;
            model.leftLeg.visible = true;
            model.rightLeg.visible = true;

            // Включаем "второй слой" скина (Overlay)
            model.hat.visible = true;
            model.jacket.visible = true;
            model.leftSleeve.visible = true;
            model.rightSleeve.visible = true;
            model.leftPants.visible = true;
            model.rightPants.visible = true;
        }
    }
}