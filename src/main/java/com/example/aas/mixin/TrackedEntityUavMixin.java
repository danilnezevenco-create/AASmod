package com.example.aas.mixin;

import com.example.aas.uav.UavViewManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Entity tracking (who sees which entity) uses the UAV position for a player who is looking through it. */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class TrackedEntityUavMixin {

    @Redirect(method = "updatePlayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;position()Lnet/minecraft/world/phys/Vec3;"), require = 0)
    private Vec3 aas$viewerPosition(ServerPlayer p) {
        Vec3 v = UavViewManager.viewPos(p);
        return v != null ? v : p.position();
    }
}
