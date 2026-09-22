package com.example.aas.network;

import com.example.aas.world.AASWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import java.util.function.Supplier;

public class PacketPlacePing {
    private final boolean isMoveMarker;

    public PacketPlacePing(boolean isMoveMarker) {
        this.isMoveMarker = isMoveMarker;
    }

    // Запись данных для отправки по сети
    public static void encode(PacketPlacePing msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.isMoveMarker);
    }

    // Чтение данных при получении
    public static PacketPlacePing decode(FriendlyByteBuf buf) {
        return new PacketPlacePing(buf.readBoolean());
    }

    public static void handle(PacketPlacePing msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            AASWorldData data = AASWorldData.get(player.serverLevel());
            String pName = player.getScoreboardName();

            // Находим, куда смотрит игрок
            Vec3 eyePos = player.getEyePosition();
            Vec3 reachVec = eyePos.add(player.getLookAngle().scale(300.0));
            BlockHitResult hit = player.level().clip(new ClipContext(eyePos, reachVec, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));

            if (hit.getType() == HitResult.Type.BLOCK) {
                BlockPos target = hit.getBlockPos().relative(hit.getDirection());

                for (AASWorldData.Squad s : data.squads) {
                    if (s.members.contains(pName)) {
                        long time = player.level().getGameTime();

                        // 1. Если это Сквад Лидер
                        if (s.leader.equals(pName)) {
                            if (msg.isMoveMarker) {
                                s.marker = new AASWorldData.SquadMarker(target.getX(), target.getY(), target.getZ(), 0, time + 12000, true);
                            } else {
                                s.pingPos = target;
                                s.pingExpiry = time + 400;
                            }
                        }
                        // 2. Если это Фаертим Лидер Браво
                        else if (s.bravoLeader.equals(pName)) {
                            if (msg.isMoveMarker) {
                                s.bravoMarker = new AASWorldData.SquadMarker(target.getX(), target.getY(), target.getZ(), 0, time + 12000, true);
                            } else {
                                s.bravoPingPos = target;
                                s.bravoPingExpiry = time + 400;
                            }
                        }
                        // 3. Если это Фаертим Лидер Чарли
                        else if (s.charlieLeader.equals(pName)) {
                            if (msg.isMoveMarker) {
                                s.charlieMarker = new AASWorldData.SquadMarker(target.getX(), target.getY(), target.getZ(), 0, time + 12000, true);
                            } else {
                                s.charliePingPos = target;
                                s.charliePingExpiry = time + 400;
                            }
                        }
                        // 4. Обычный игрок ничего не ставит
                        else {
                            break;
                        }

                        data.setDirty();

                        // Синхронизируем отряды (чтобы нужные люди увидели метку)
                        PacketHandler.INSTANCE.send(PacketDistributor.DIMENSION.with(player.level()::dimension),
                                new PacketSyncSquads(data.squads));
                        break;
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}