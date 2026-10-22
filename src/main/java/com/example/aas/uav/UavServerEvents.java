package com.example.aas.uav;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "aas")
public class UavServerEvents {

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.level instanceof ServerLevel level)) return;
        UavManager.tickLevel(level);
        UavViewManager.tickLevel(level);
        UavEffects.tick(level);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) UavManager.syncPlayer(p);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) UavManager.syncPlayer(p);
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            UavViewManager.exit(p, UavExitReason.LOST);
            UavManager.syncPlayer(p);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        UavViewManager.forget(e.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) UavViewManager.exit(p, UavExitReason.DEAD);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent e) {
        UavViewManager.clearAll();
        UavEffects.clear();
    }

    // ---------------- server-side input lock for the body while looking through the UAV ----------------
    private static boolean locked(net.minecraft.world.entity.player.Player p) {
        return p instanceof ServerPlayer sp && UavViewManager.isViewing(sp);
    }

    @SubscribeEvent public static void onRightClickItem(PlayerInteractEvent.RightClickItem e) { if (locked(e.getEntity())) e.setCanceled(true); }
    @SubscribeEvent public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) { if (locked(e.getEntity())) e.setCanceled(true); }
    @SubscribeEvent public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock e) { if (locked(e.getEntity())) e.setCanceled(true); }
    @SubscribeEvent public static void onEntityInteract(PlayerInteractEvent.EntityInteract e) { if (locked(e.getEntity())) e.setCanceled(true); }
    @SubscribeEvent public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific e) { if (locked(e.getEntity())) e.setCanceled(true); }
    @SubscribeEvent public static void onAttack(AttackEntityEvent e) { if (locked(e.getEntity())) e.setCanceled(true); }
    @SubscribeEvent public static void onToss(ItemTossEvent e) { if (locked(e.getPlayer())) e.setCanceled(true); }
    @SubscribeEvent public static void onUseStart(LivingEntityUseItemEvent.Start e) {
        if (e.getEntity() instanceof net.minecraft.world.entity.player.Player p && locked(p)) e.setCanceled(true);
    }
}
