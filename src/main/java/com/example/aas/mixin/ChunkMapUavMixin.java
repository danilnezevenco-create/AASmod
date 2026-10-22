package com.example.aas.mixin;

import com.example.aas.uav.UavViewManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * While a player looks through the UAV, chunk tracking (and the chunk-cache centre sent to the client) is computed
 * from the UAV position instead of the body position. The body itself never moves.
 * Lives in aas.uav.mixins.json (required=false): if a target does not match, the feature degrades, the game still starts.
 */
@Mixin(ChunkMap.class)
public abstract class ChunkMapUavMixin {

    @Redirect(method = "move", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;getBlockX()I"), require = 0)
    private int aas$blockX(ServerPlayer p) {
        Vec3 v = UavViewManager.viewPos(p);
        return v != null ? Mth.floor(v.x) : p.getBlockX();
    }

    @Redirect(method = "move", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;getBlockZ()I"), require = 0)
    private int aas$blockZ(ServerPlayer p) {
        Vec3 v = UavViewManager.viewPos(p);
        return v != null ? Mth.floor(v.z) : p.getBlockZ();
    }

    @Redirect(method = "move", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/core/SectionPos;of(Lnet/minecraft/world/level/entity/EntityAccess;)Lnet/minecraft/core/SectionPos;"), require = 0)
    private SectionPos aas$sectionOfMove(EntityAccess e) {
        return aas$section(e);
    }

    @Redirect(method = "updatePlayerPos", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/core/SectionPos;of(Lnet/minecraft/world/level/entity/EntityAccess;)Lnet/minecraft/core/SectionPos;"), require = 0)
    private SectionPos aas$sectionOfUpdate(EntityAccess e) {
        return aas$section(e);
    }

    private static SectionPos aas$section(EntityAccess e) {
        if (e instanceof ServerPlayer p) {
            Vec3 v = UavViewManager.viewPos(p);
            if (v != null) return SectionPos.of(BlockPos.containing(v));
        }
        return SectionPos.of(e);
    }
}
