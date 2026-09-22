package com.example.aas.network;

import com.example.aas.world.AASWorldData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

public class PacketDebugFill {
    // Пакет пустой, нам важен сам факт нажатия
    public PacketDebugFill() {}

    public static void encode(PacketDebugFill msg, FriendlyByteBuf buf) {}

    public static PacketDebugFill decode(FriendlyByteBuf buf) {
        return new PacketDebugFill();
    }

    public static void handle(PacketDebugFill msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.isCreative()) {
                ServerLevel level = player.serverLevel();
                AASWorldData data = AASWorldData.get(level);
                String pTeam = player.getTeam() != null ? player.getTeam().getName().toUpperCase() : "BLUE";

                // Чуть больше 1/4 (25%), чтобы 4 нажатия стабильно давали 100%
                float amount = 0.255f;

                for (AASWorldData.CapturePoint point : data.capturePoints) {
                    if (point.isInside(player.position())) {

                        // СЦЕНАРИЙ 1: Точка уже принадлежит нашей команде (просто чиним прогресс до 100%)
                        if (point.owner.equals(pTeam)) {
                            point.progress += amount;
                            if (point.progress > 1.0f) point.progress = 1.0f;
                            point.capturingTeam = "NONE";
                        }
                        // СЦЕНАРИЙ 2: Точка нейтральная
                        else if (point.owner.equals("NEUTRAL")) {
                            // Если её никто не захватывал или уже захватываем мы
                            if (point.capturingTeam.equals("NONE") || point.capturingTeam.equals(pTeam)) {
                                point.capturingTeam = pTeam;
                                point.progress += amount;

                                // Полный захват
                                if (point.progress >= 1.0f) {
                                    point.progress = 1.0f;
                                    point.owner = pTeam;
                                    point.capturingTeam = "NONE";

                                    // Снимаем тикеты с врага за захват (как в реальной игре)
                                    if (point.captureDeduction > 0) {
                                        if (pTeam.equals("BLUE")) data.redTickets = Math.max(0, data.redTickets - point.captureDeduction);
                                        else data.blueTickets = Math.max(0, data.blueTickets - point.captureDeduction);
                                        com.example.aas.events.GameLogicEvents.checkGameOver(level, data);
                                    }
                                    if (point.ticketGainCapture > 0) {
                                        if (pTeam.equals("BLUE")) data.blueTickets += point.ticketGainCapture;
                                        else data.redTickets += point.ticketGainCapture;
                                    }

                                    // Шлем уведомление о ЗАХВАТЕ
                                    PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new PacketCaptureNotification(point.name, pTeam, false));
                                }
                            }
                            // Если враг пытался захватить, а мы сбиваем его прогресс
                            else {
                                point.progress -= amount;
                                if (point.progress <= 0.0f) {
                                    point.progress = 0.0f;
                                    point.capturingTeam = "NONE"; // Сбросили врага, следующее нажатие начнет захват для нас
                                }
                            }
                        }
                        // СЦЕНАРИЙ 3: Точка принадлежит врагу (Нейтрализация)
                        else {
                            point.progress -= amount;
                            if (point.progress <= 0.0f) {
                                String oldOwner = point.owner;
                                point.progress = 0.0f;
                                point.owner = "NEUTRAL";
                                point.capturingTeam = "NONE";

                                // Снимаем тикеты за потерю точки
                                if (oldOwner.equals("BLUE")) data.blueTickets = Math.max(0, data.blueTickets - point.ticketPenalty);
                                else if (oldOwner.equals("RED")) data.redTickets = Math.max(0, data.redTickets - point.ticketPenalty);
                                com.example.aas.events.GameLogicEvents.checkGameOver(level, data);
                                if (point.ticketGainNeutralize > 0) {
                                    if (pTeam.equals("BLUE")) data.blueTickets += point.ticketGainNeutralize;
                                    else data.redTickets += point.ticketGainNeutralize;
                                }
                                // Шлем уведомление о НЕЙТРАЛИЗАЦИИ
                                PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new PacketCaptureNotification(point.name, pTeam, true));
                            }
                        }

                        // Сохраняем изменения и синхронизируем HUD всем игрокам
                        data.setDirty();
                        PacketHandler.sendToAllClients(level, data);
                        break;
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}