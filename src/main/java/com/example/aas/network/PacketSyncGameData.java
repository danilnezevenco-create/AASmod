// PATH: src/main/java/com/example/aas/network/PacketSyncGameData.java
package com.example.aas.network;

import com.example.aas.client.ClientHooks;
import com.example.aas.world.AASWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import java.util.*;
import java.util.function.Supplier;
import net.minecraftforge.network.NetworkEvent;

public class PacketSyncGameData {

    public final int blueTickets, redTickets;
    public final boolean hasBlueRally, hasRedRally;
    public final boolean blueBleeding, redBleeding;
    public final int respawnTime;
    public final boolean blueBlocked, redBlocked;
    public final List<AASWorldData.HubInfo> hubs;
    public final String blueFaction, redFaction;
    public final List<AASWorldData.VehicleRecord> markedVehicles;
    public final String blueCustomName, redCustomName;
    public final int mapCenterX, mapCenterZ, mapSizeBlocks;
    public final boolean isGameStarted;
    public final List<AASWorldData.CapturePoint> capturePoints;
    public final Map<String, BlockPos> blueSpawns, redSpawns, neutralSpawns;
    public final Map<String, String> playerKits;
    public final List<AASWorldData.MapMarker> activeMarkers;
    public final boolean hubSpawnCosts;
    public final int hubSpawnCost;

    public PacketSyncGameData(int blue, int red, boolean hBR, boolean hRR, boolean bBl, boolean rBl, int rTime,
                              boolean bB, boolean rB, List<AASWorldData.HubInfo> hubs, String bF, String rF,
                              String bCN, String rCN, boolean started, int mCX, int mCZ, int mSB,
                              List<AASWorldData.CapturePoint> points, Map<String, BlockPos> bSp,
                              Map<String, BlockPos> rSp, Map<String, BlockPos> nSp,
                              Map<String, String> kits, List<AASWorldData.VehicleRecord> vehicles,
                              List<AASWorldData.MapMarker> activeMarkers,
                              boolean hsc, int hsca) {
        this.blueTickets = blue; this.redTickets = red;
        this.hasBlueRally = hBR; this.hasRedRally = hRR;
        this.blueBleeding = bBl; this.redBleeding = rBl;
        this.respawnTime = rTime;
        this.blueBlocked = bB; this.redBlocked = rB;
        this.hubs = hubs;
        this.blueFaction = bF; this.redFaction = rF;
        this.blueCustomName = bCN; this.redCustomName = rCN;
        this.isGameStarted = started;
        this.mapCenterX = mCX; this.mapCenterZ = mCZ; this.mapSizeBlocks = mSB;
        this.capturePoints = points;
        this.blueSpawns = bSp; this.redSpawns = rSp; this.neutralSpawns = nSp;
        this.playerKits = kits;
        this.markedVehicles = vehicles;
        this.activeMarkers = activeMarkers;
        this.hubSpawnCosts = hsc;
        this.hubSpawnCost = hsca;
    }

    public static void encode(PacketSyncGameData msg, FriendlyByteBuf buf) {
        // 1. Основные параметры игры
        buf.writeInt(msg.blueTickets);
        buf.writeInt(msg.redTickets);
        buf.writeBoolean(msg.hasBlueRally);
        buf.writeBoolean(msg.hasRedRally);
        buf.writeBoolean(msg.blueBleeding);
        buf.writeBoolean(msg.redBleeding);
        buf.writeInt(msg.respawnTime);
        buf.writeBoolean(msg.blueBlocked);
        buf.writeBoolean(msg.redBlocked);
        buf.writeBoolean(msg.hubSpawnCosts);
        buf.writeInt(msg.hubSpawnCost);
        // 2. Параметры карты (Центр и Размер)
        buf.writeInt(msg.mapCenterX);
        buf.writeInt(msg.mapCenterZ);
        buf.writeInt(msg.mapSizeBlocks);

        // 3. Коллекция техники (UUID + Команда + Тип + Координаты)
        buf.writeCollection(msg.markedVehicles, (b, v) -> {
            b.writeUUID(v.uuid);
            b.writeUtf(v.team);
            b.writeUtf(v.type);
            b.writeDouble(v.x);
            b.writeDouble(v.y);
            b.writeDouble(v.z);
            b.writeFloat(v.yaw);
        });

        // 4. Коллекция Хабов (Поз + Команда + Статус + Материалы)
        buf.writeCollection(msg.hubs, (b, h) -> {
            b.writeBlockPos(h.pos);
            b.writeUtf(h.team);
            b.writeBoolean(h.constructed);
            b.writeUtf(h.dimension);
            b.writeBoolean(h.isBlocked);
            b.writeInt(h.materials);
        });

        // 5. Строковые данные (Фракции и Кастомные имена)
        buf.writeUtf(msg.blueFaction);
        buf.writeUtf(msg.redFaction);
        buf.writeUtf(msg.blueCustomName);
        buf.writeUtf(msg.redCustomName);

        // 6. Статус старта
        buf.writeBoolean(msg.isGameStarted);

        // 7. Сложные данные (Точки захвата через NBT)
        CompoundTag pointsTag = new CompoundTag();
        ListTag list = new ListTag();
        for (AASWorldData.CapturePoint cp : msg.capturePoints) list.add(cp.save());
        pointsTag.put("Points", list);
        buf.writeNbt(pointsTag);

        // 8. Карты (Спавны и Киты игроков)
        buf.writeMap(msg.blueSpawns, FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeBlockPos);
        buf.writeMap(msg.redSpawns, FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeBlockPos);
        buf.writeMap(msg.neutralSpawns, FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeBlockPos);
        buf.writeMap(msg.playerKits, FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeUtf);

        buf.writeCollection(msg.activeMarkers, (b, m) -> {
            b.writeBlockPos(m.pos);
            b.writeUtf(m.type);
            b.writeUtf(m.team);
            b.writeLong(m.expiryTick);
        });
    }

    public static PacketSyncGameData decode(FriendlyByteBuf buf) {
        // 1. Читаем основные параметры
        int bT = buf.readInt();
        int rT = buf.readInt();
        boolean hBR = buf.readBoolean();
        boolean hRR = buf.readBoolean();
        boolean bBl = buf.readBoolean();
        boolean rBl = buf.readBoolean();
        int rTime = buf.readInt();
        boolean bB = buf.readBoolean();
        boolean rB = buf.readBoolean();
        boolean hsc = buf.readBoolean();
        int hsca = buf.readInt();
        // 2. Читаем параметры карты
        int mCX = buf.readInt();
        int mCZ = buf.readInt();
        int mSB = buf.readInt();

        // 3. Читаем список техники (Record)
        List<AASWorldData.VehicleRecord> vL = buf.readList(b -> new AASWorldData.VehicleRecord(
                b.readUUID(), b.readUtf(), b.readUtf(),
                b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat(), null
        ));

        // 4. Читаем список Хабов (HubInfo)
        List<AASWorldData.HubInfo> hL = buf.readList(b -> {
            AASWorldData.HubInfo h = new AASWorldData.HubInfo(b.readBlockPos(), b.readUtf(), b.readBoolean(), b.readUtf());
            h.isBlocked = b.readBoolean();
            h.materials = b.readInt();
            return h;
        });

        // 5. Читаем строки
        String bF = buf.readUtf();
        String rF = buf.readUtf();
        String bCN = buf.readUtf();
        String rCN = buf.readUtf();

        // 6. Статус игры
        boolean started = buf.readBoolean();

        // 7. Точки захвата
        List<AASWorldData.CapturePoint> pL = new ArrayList<>();
        CompoundTag pTag = buf.readNbt();
        if (pTag != null && pTag.contains("Points")) {
            ListTag list = pTag.getList("Points", 10);
            for (int i = 0; i < list.size(); i++) pL.add(AASWorldData.CapturePoint.load(list.getCompound(i)));
        }

        // 8. Карты спавнов и китов
        Map<String, BlockPos> bSp = buf.readMap(FriendlyByteBuf::readUtf, FriendlyByteBuf::readBlockPos);
        Map<String, BlockPos> rSp = buf.readMap(FriendlyByteBuf::readUtf, FriendlyByteBuf::readBlockPos);
        Map<String, BlockPos> nSp = buf.readMap(FriendlyByteBuf::readUtf, FriendlyByteBuf::readBlockPos);
        Map<String, String> pK = buf.readMap(FriendlyByteBuf::readUtf, FriendlyByteBuf::readUtf);
        List<AASWorldData.MapMarker> aM = buf.readList(b ->
                new AASWorldData.MapMarker(b.readBlockPos(), b.readUtf(), b.readUtf(), b.readLong()));

        // Возвращаем объект, строго соблюдая порядок аргументов вашего конструктора
        return new PacketSyncGameData(
                bT, rT, hBR, hRR, bBl, rBl, rTime, bB, rB, hL, bF, rF, bCN, rCN, started, mCX, mCZ, mSB, pL, bSp, rSp, nSp, pK, vL, aM, hsc, hsca
        );
    }

    public static void handle(PacketSyncGameData msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.handleSyncGameData(msg)));
        ctx.get().setPacketHandled(true);
    }
}