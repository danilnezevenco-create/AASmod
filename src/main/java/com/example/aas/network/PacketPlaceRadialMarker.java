// PATH: src/main/java/com/example/aas/network/PacketPlaceRadialMarker.java
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

/**
 * Р”РµРіР°СЂ РЅРѕРІРѕРіРѕ РІС‹Р±РѕСЂРѕС‡РЅРѕРіРѕ 3D-РІРµС‰Р°РЅРёСЏ РјРµС‚РѕРє (WorldMarkerRadialScreen).
 * Р’ РѕС‚Р»РёС‡РёРµ РѕС‚ PacketSquadMarker (Р±СЊС‘С‚ РїРѕ x/z СЃ РєР°СЂС‚С‹, Y РІСЃРµРіРґР° 64), СЌС‚РѕС‚ РїР°РєРµС‚
 * РЅРµ РЅРµСЃС‘С‚ РЅРёРєР°РєРёС… РєРѕРѕСЂРґРёРЅР°С‚ РІРѕРѕР±С‰Рµ вЂ” СЃРµСЂРІРµСЂ СЃР°Рј СЃС‡РёС‚Р°РµС‚ raycast РёР· РіР»Р°Р· РёРіСЂРѕРєР°
 * РїРѕ РµРіРѕ С‚РµРєСѓС‰РµРјСѓ РЅР°РїСЂР°РІР»РµРЅРёСЋ РІР·РіР»СЏРґР° (С‚РѕС‡РЅРѕ С‚Р°Рє Р¶Рµ, РєР°Рє СЌС‚Рѕ СѓР¶Рµ РґРµР»Р°РµС‚ PacketPlacePing).
 * Р­С‚Рѕ РєРѕСЂСЂРµРєС‚РЅРѕ, РїРѕС‚РѕРјСѓ С‡С‚Рѕ РїРѕРєР° РѕС‚РєСЂС‹С‚ WorldMarkerRadialScreen (Screen),
 * РІР°РЅРёР»СЊРЅС‹Р№ Р»РѕРѕРє Minecraft'Р° РЅРµ РІСЂР°С‰Р°РµС‚ РєР°РјРµСЂСѓ РёРіСЂРѕРєР° вЂ” РІР·РіР»СЏРґ Р·Р°РјРѕСЂРѕР¶РµРЅ РЅР° С‚РѕРј
 * РЅР°РїСЂР°РІР»РµРЅРёРё, РєРѕС‚РѕСЂРѕРµ Р±С‹Р»Рѕ РІ РјРѕРјРµРЅС‚ РѕС‚РєСЂС‹С‚РёСЏ РјРµРЅСЋ.
 *
 * type: 0-Move, 1-Attack, 2-Defend, 3-Build, 4-Eye (РєСЂР°С‚РєРѕРІСЂРµРјРµРЅРЅС‹Р№ РїРёРЅРі, РєР°Рє Сѓ PacketPlacePing).
 */
public class PacketPlaceRadialMarker {
    public static final int TYPE_MOVE = 0;
    public static final int TYPE_ATTACK = 1;
    public static final int TYPE_DEFEND = 2;
    public static final int TYPE_BUILD = 3;
    public static final int TYPE_EYE = 4;

    private final int type;

    public PacketPlaceRadialMarker(int type) {
        this.type = type;
    }

    public static void encode(PacketPlaceRadialMarker msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.type);
    }

    public static PacketPlaceRadialMarker decode(FriendlyByteBuf buf) {
        return new PacketPlaceRadialMarker(buf.readInt());
    }

    public static void handle(PacketPlaceRadialMarker msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (msg.type < TYPE_MOVE || msg.type > TYPE_EYE) return;

            AASWorldData data = AASWorldData.get(player.serverLevel());
            String pName = player.getScoreboardName();

            // РЎРѕС…СЂР°РЅСЏРµРј С‚Сѓ Р¶Рµ Р»РѕРіРёРєСѓ raycast'Р°, С‡С‚Рѕ Рё Сѓ PacketPlacePing
            Vec3 eyePos = player.getEyePosition();
            Vec3 reachVec = eyePos.add(player.getLookAngle().scale(300.0));
            BlockHitResult hit = player.level().clip(new ClipContext(eyePos, reachVec, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.BLOCK) return;

            BlockPos target = hit.getBlockPos().relative(hit.getDirection());
            long time = player.level().getGameTime();

            for (AASWorldData.Squad s : data.squads) {
                if (!s.members.contains(pName)) continue;

                boolean isSL = s.leader.equals(pName);
                boolean isBravoFTL = s.bravoLeader.equals(pName);
                boolean isCharlieFTL = s.charlieLeader.equals(pName);

                // РўРѕР»СЊРєРѕ SL Рё FTL РјРѕРіСѓС‚ СЃС‚Р°РІРёС‚СЊ СЌС‚Рё РјРµС‚РєРё (РєР°Рє Рё СЂР°РЅСЊС€Рµ Сѓ ping/squad markers)
                if (!isSL && !isBravoFTL && !isCharlieFTL) break;

                if (msg.type == TYPE_EYE) {
                    // РљРѕСЂРѕС‚РєРёР№ "РіР»Р°Р·-РїРёРЅРі" вЂ” РёРґРµРЅС‚РёС‡РЅРѕ PacketPlacePing(isMoveMarker=false)
                    if (isSL) {
                        s.pingPos = target;
                        s.pingExpiry = time + 400;
                    } else if (isBravoFTL) {
                        s.bravoPingPos = target;
                        s.bravoPingExpiry = time + 400;
                    } else {
                        s.charliePingPos = target;
                        s.charliePingExpiry = time + 400;
                    }
                } else {
                    // Move / Attack / Defend / Build вЂ” РґРѕР»РіРѕР¶РёРІСѓС‰Р°СЏ РјРµС‚РєР° СЃ СЂРµР°Р»СЊРЅС‹Рј Y РёР· raycast'Р°
                    AASWorldData.SquadMarker marker = new AASWorldData.SquadMarker(
                            target.getX(), target.getY(), target.getZ(), msg.type, time + 12000, true);

                    if (isSL) {
                        s.marker = marker;
                    } else if (isBravoFTL) {
                        s.bravoMarker = marker;
                    } else {
                        s.charlieMarker = marker;
                    }
                }

                data.setDirty();
                PacketHandler.INSTANCE.send(PacketDistributor.DIMENSION.with(player.level()::dimension),
                        new PacketSyncSquads(data.squads));
                break;
            }
        });
        ctx.get().setPacketHandled(true);
    }
}