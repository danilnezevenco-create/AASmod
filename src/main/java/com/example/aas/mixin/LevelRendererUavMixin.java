package com.example.aas.mixin;

import com.example.aas.client.uav.UavViewClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Vanilla LevelRenderer.setupRender() re-centres its ViewArea (the grid of renderable chunk sections)
 * on minecraft.player, NOT on the camera entity. While looking through the UAV the player body stays far away,
 * so the grid only covers the area around the body and everything around the UAV is never drawn
 * (black + clouds), even though the chunks are loaded on the client.
 * Fix: while viewing, centre the grid on the camera instead.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererUavMixin {

    @Redirect(method = "setupRender",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getX()D"))
    private double aas$viewX(LocalPlayer p) {
        Vec3 c = aas$camPos();
        return c != null ? c.x : p.getX();
    }

    @Redirect(method = "setupRender",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getY()D"))
    private double aas$viewY(LocalPlayer p) {
        Vec3 c = aas$camPos();
        return c != null ? c.y : p.getY();
    }

    @Redirect(method = "setupRender",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getZ()D"))
    private double aas$viewZ(LocalPlayer p) {
        Vec3 c = aas$camPos();
        return c != null ? c.z : p.getZ();
    }

    private static Vec3 aas$camPos() {
        if (!UavViewClient.isViewing()) return null;
        return Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
    }
}