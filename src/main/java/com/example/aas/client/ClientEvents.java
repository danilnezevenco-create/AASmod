package com.example.aas.client;

import com.example.aas.client.gui.SquadSelectionScreen;
import com.example.aas.client.gui.TeamSelectionScreen;
import com.example.aas.entity.AGS30Entity;
import com.example.aas.entity.M2BrowningEntity;
import com.example.aas.item.EntrenchingToolItem;
import com.example.aas.item.ModItems;
import com.example.aas.item.SupplyTruckMarkerItem;
import com.example.aas.item.VehicleMarkerItem;
import com.example.aas.network.*;
import com.example.aas.client.gui.DownedScreen;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import com.example.aas.network.PacketHandler;
import com.example.aas.network.PacketVehicleShoot;

@Mod.EventBusSubscriber(modid = "aas", value = Dist.CLIENT)
public class ClientEvents {

    // Поля для отслеживания состояния клавиш рации
    private static boolean lastSquadState = false;
    private static boolean lastCommandState = false;

    @SubscribeEvent
    public static void onChatReceived(ClientChatReceivedEvent event) {
        ClientData.addChatMessage(event.getMessage());
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        if (player.isUsingItem() && !player.isScoping()) {
            ItemStack stack = event.getItemStack();
            if (stack.getItem() instanceof EntrenchingToolItem) {
                if (event.getHand() == player.getUsedItemHand()) {
                    PoseStack poseStack = event.getPoseStack();
                    float useTime = player.getTicksUsingItem() + event.getPartialTick();
                    float cycleSpeed = 10.0f;
                    float cycle = (useTime % cycleSpeed) / cycleSpeed;
                    float wave = Mth.sin(cycle * Mth.PI);
                    float depth = 2.5f;
                    poseStack.translate(0.0, wave * depth, 0.0);
                }
            }
        }
    }

    // ОБЪЕДИНЕННЫЙ МЕТОД ТИКА
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // 1. Отдача (Recoil)
        RecoilHandler.clientTick();


        // 3. ЛОГИКА СТРЕЛЬБЫ ИЗ ТЕХНИКИ
        Entity vehicle = mc.player.getVehicle();
        if (vehicle instanceof M2BrowningEntity || vehicle instanceof AGS30Entity) {
            long windowId = mc.getWindow().getWindow();
            boolean isLeftBtnDown = GLFW.glfwGetMouseButton(windowId, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

            if (isLeftBtnDown && mc.screen == null) {
                PacketHandler.INSTANCE.sendToServer(new PacketVehicleShoot());
                while (mc.options.keyAttack.consumeClick()) { }
            }
        }
        if (mc.player.getPersistentData().getBoolean("AAS_IsDowned")) {
            // Если данных еще нет (только упал), сохраняем текущий взгляд
            if (!mc.player.getPersistentData().contains("AAS_DownedYaw")) {
                mc.player.getPersistentData().putFloat("AAS_DownedYaw", mc.player.getYRot());
                mc.player.getPersistentData().putFloat("AAS_DownedPitch", mc.player.getXRot());
            }

            float yaw = mc.player.getPersistentData().getFloat("AAS_DownedYaw");
            float pitch = mc.player.getPersistentData().getFloat("AAS_DownedPitch");

            mc.player.setYRot(yaw);
            mc.player.setXRot(pitch);
            mc.player.yRotO = yaw;
            mc.player.xRotO = pitch;

            // Закрываем любые другие экраны кроме нашего
            if (mc.screen != null && !(mc.screen instanceof DownedScreen)) {
                mc.setScreen(new DownedScreen());
            }
        } else {
            if (mc.player.getPersistentData().contains("AAS_DownedYaw")) {
                mc.player.getPersistentData().remove("AAS_DownedYaw");
                mc.player.getPersistentData().remove("AAS_DownedPitch");
            }
        }
    }

    @SubscribeEvent
    public static void onMouseInput(InputEvent.MouseButton.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        Entity vehicle = mc.player.getVehicle();

        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (vehicle instanceof M2BrowningEntity || vehicle instanceof AGS30Entity) {
                event.setCanceled(true);
            }
        }

        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT && event.getAction() == GLFW.GLFW_PRESS) {
            if (vehicle instanceof M2BrowningEntity || vehicle instanceof AGS30Entity) {
                PacketHandler.INSTANCE.sendToServer(new PacketToggleAim());
                event.setCanceled(true);
                return;
            }
        }

        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && event.getAction() == GLFW.GLFW_PRESS) {
            ItemStack stack = mc.player.getMainHandItem();
            if (stack.getItem() instanceof VehicleMarkerItem || stack.getItem() instanceof SupplyTruckMarkerItem) {
                HitResult result = mc.hitResult;
                if (result != null && result.getType() == HitResult.Type.ENTITY) {
                    EntityHitResult entityResult = (EntityHitResult) result;
                    Entity target = entityResult.getEntity();
                    PacketHandler.INSTANCE.sendToServer(new PacketApplyMarker(target.getId()));
                }
            }
        }
    }

    @SubscribeEvent
    public static void onComputeFov(ComputeFovModifierEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            Entity vehicle = mc.player.getVehicle();
            if (vehicle instanceof M2BrowningEntity m2 && m2.isAiming()) {
                event.setNewFovModifier(event.getFovModifier() * (1.0f / 1.5f));
            } else if (vehicle instanceof AGS30Entity ags && ags.isAiming()) {
                event.setNewFovModifier(event.getFovModifier() * (1.0f / 3f));
            }
        }
    }

    @SubscribeEvent
    public static void onOpenGui(ScreenEvent.Opening event) {
        if (event.getScreen() instanceof DeathScreen && !(event.getScreen() instanceof AASDeathScreen)) {
            Component cause = null;
            if (Minecraft.getInstance().player != null) {
                cause = Minecraft.getInstance().player.getCombatTracker().getDeathMessage();
            }
            event.setNewScreen(new AASDeathScreen(cause, false));
        }
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (event.getAction() == GLFW.GLFW_PRESS && ModKeyBindings.DROP_SUPPLY_KEY.matches(event.getKey(), event.getScanCode())) {
            if (mc.player.getVehicle() != null) {
                PacketHandler.INSTANCE.sendToServer(new PacketDropCrate());
            }
        }

        if (event.getAction() == GLFW.GLFW_PRESS && ModKeyBindings.OPEN_SQUAD_MENU_KEY.matches(event.getKey(), event.getScanCode())) {
            if (mc.screen == null) {
                String teamName = (mc.player.getTeam() != null) ? mc.player.getTeam().getName() : "";
                boolean isValidTeam = teamName.equalsIgnoreCase("Blue") || teamName.equalsIgnoreCase("Red");

                if (!isValidTeam) {
                    mc.setScreen(new TeamSelectionScreen());
                } else {
                    mc.setScreen(new SquadSelectionScreen());
                }
            }
        }
        if (ModKeyBindings.SHOW_MAP_KEY.matches(event.getKey(), event.getScanCode())) {
            if (event.getAction() == GLFW.GLFW_PRESS) {
                ClientData.isMapOpen = true; // Нажал — открылась
            }
            else if (event.getAction() == GLFW.GLFW_RELEASE) {
                ClientData.isMapOpen = false; // Отпустил — закрылась
            }
        }

        if (event.getAction() == GLFW.GLFW_PRESS) {
            boolean isDebugKey = (event.getKey() == GLFW.GLFW_KEY_F9 ||
                    event.getKey() == GLFW.GLFW_KEY_F10 ||
                    event.getKey() == GLFW.GLFW_KEY_F8);

            if (isDebugKey && !mc.player.isCreative()) return;

            if (event.getKey() == GLFW.GLFW_KEY_F9) PacketHandler.INSTANCE.sendToServer(new PacketDebugFill());
            if (event.getKey() == GLFW.GLFW_KEY_F10) {
                PacketHandler.INSTANCE.sendToServer(new PacketDebugSpawnRally("BLUE"));
                mc.player.sendSystemMessage(Component.literal("Spawning BLUE Rally..."));
            }
            if (event.getKey() == GLFW.GLFW_KEY_F8) {
                PacketHandler.INSTANCE.sendToServer(new PacketDebugSpawnRally("RED"));
                mc.player.sendSystemMessage(Component.literal("Spawning RED Rally..."));
            }
        }
    }
}