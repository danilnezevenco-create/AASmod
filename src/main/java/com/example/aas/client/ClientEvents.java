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
import net.minecraftforge.client.event.InputEvent;
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
import com.example.aas.client.RecoilHandler;

@Mod.EventBusSubscriber(modid = "aas", value = Dist.CLIENT)
public class ClientEvents {

    // Поля для отслеживания состояния клавиш рации
    private static boolean lastSquadState = false;
    private static boolean lastCommandState = false;

    @SubscribeEvent
    public static void onChatReceived(ClientChatReceivedEvent event) {
        ClientData.addChatMessage(event.getMessage());
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
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();

        // Если карта открыта (например, на TAB)
        if (ClientData.isMapOpen && mc.screen == null) {
            double scrollDelta = event.getScrollDelta();

            if (scrollDelta != 0) {
                // Изменяем масштаб в ClientData
                // Если крутим вверх (delta > 0) - приближаем (уменьшаем число блоков на пиксель)
                if (scrollDelta > 0) {
                    ClientData.mapScale /= 1.2;
                } else {
                    ClientData.mapScale *= 1.2;
                }

                // Ограничиваем зум (от 0.5 до 25.0 блоков на пиксель)
                if (ClientData.mapScale < 0.5) ClientData.mapScale = 0.5;
                if (ClientData.mapScale > 25.0) ClientData.mapScale = 25.0;

                // ВАЖНО: Отменяем событие, чтобы хотбар не крутился
                event.setCanceled(true);
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

        // 1. ЛОГИКА ГОЛОСОВАНИЯ (F1/F2)
        if (ClientData.voteActive && mc.screen == null && event.getAction() == GLFW.GLFW_PRESS) {
            if (event.getKey() == GLFW.GLFW_KEY_F9) {
                PacketHandler.INSTANCE.sendToServer(new PacketVoteAction(true));
                // event.setCanceled(true); // УДАЛЕНО, так как вызывает краш
                return;
            } else if (event.getKey() == GLFW.GLFW_KEY_F10) {
                PacketHandler.INSTANCE.sendToServer(new PacketVoteAction(false));
                // event.setCanceled(true); // УДАЛЕНО, так как вызывает краш
                return;
            }
        }

        // 2. СБРОС ЯЩИКА (X)
        if (event.getAction() == GLFW.GLFW_PRESS && ModKeyBindings.DROP_SUPPLY_KEY.matches(event.getKey(), event.getScanCode())) {
            if (mc.player.getVehicle() != null) {
                PacketHandler.INSTANCE.sendToServer(new PacketDropCrate());
            }
        }

        // 3. МЕНЮ ОТРЯДОВ (K)
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

        // 4. КАРТА (TAB)
        if (ModKeyBindings.SHOW_MAP_KEY.matches(event.getKey(), event.getScanCode())) {
            if (event.getAction() == GLFW.GLFW_PRESS) {
                ClientData.isMapOpen = true;
            }
            else if (event.getAction() == GLFW.GLFW_RELEASE) {
                ClientData.isMapOpen = false;
            }
        }
    }
}