// PATH: src\main\java\com\example\aas\world\AASWorldData.java
package com.example.aas.world;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AASWorldData extends SavedData {
    public List<CapturePoint> capturePoints = new ArrayList<>();
    public List<BlockPos> blueRallies = new ArrayList<>();
    public List<BlockPos> redRallies = new ArrayList<>();
    public List<HubInfo> hubs = new ArrayList<>();
    public List<Squad> squads = new ArrayList<>();
    public String shapeType = "CUBE"; // CUBE или CYLINDER
    public int lockDurationMinutes = 0; // На сколько минут блокируется точка
    public long lockedUntilTick = 0; // Гейм-тик, до которого точка закрыта для врага
    public Map<String, BlockPos> blueSpawns = new HashMap<>();
    public Map<String, BlockPos> redSpawns = new HashMap<>();
    public Map<String, BlockPos> neutralSpawns = new HashMap<>();
    public List<VehicleRecord> markedVehicles = new ArrayList<>();
    public int blueTickets = 800;
    public int redTickets = 800;
    public int respawnTimer = 10;
    public int deathTicketCost = 2;
    public String blueFaction = "none";
    public String redFaction = "none";
    public boolean isGameStarted = false;
    public int countdownTicks = 0;
    public boolean countdownActive = false;
    public boolean playedBlueSiren = false;
    public boolean playedRedSiren = false;
    public Map<String, KitInfo> blueKits = new HashMap<>();
    public Map<String, KitInfo> redKits = new HashMap<>();

    public int mapCenterX = 0;
    public int mapCenterZ = 0;
    public int mapSizeBlocks = 2048;

    public static final String[] KIT_NAMES = {
            "Officer", "LAT", "HAT", "Sapper", "Sniper", "Marksman",
            "LMG", "HMG", "Rifleman", "Medic", "Grenadier", "Assault",
            "Pilot", "Mechanic", "Drone Operator", "Anti_air"
    };

    public AASWorldData() {
        // Инициализация пустых китов при создании мира
        for (String name : KIT_NAMES) {
            blueKits.put(name, new KitInfo(name));
            redKits.put(name, new KitInfo(name));
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("BlueTickets", blueTickets);
        tag.putInt("RedTickets", redTickets);
        tag.putInt("RespawnTimer", respawnTimer);
        tag.putInt("DeathTicketCost", deathTicketCost);
        tag.putString("BlueFaction", blueFaction);
        tag.putString("RedFaction", redFaction);
        tag.putBoolean("IsGameStarted", isGameStarted);
        tag.putInt("CountdownTicks", countdownTicks);
        tag.putBoolean("CountdownActive", countdownActive);
        tag.putBoolean("PlayedBlueSiren", playedBlueSiren);
        tag.putBoolean("PlayedRedSiren", playedRedSiren);
        tag.putInt("MapCenterX", mapCenterX);
        tag.putInt("MapCenterZ", mapCenterZ);
        tag.putInt("MapSizeBlocks", mapSizeBlocks);
        ListTag vehicleList = new ListTag();
        for (VehicleRecord v : markedVehicles) {
            vehicleList.add(v.save());
        }
        tag.put("MarkedVehicles", vehicleList);
        ListTag markerList = new ListTag();
        for (MapMarker m : activeMarkers) markerList.add(m.save());
        tag.put("TacticalMarkers", markerList);
        ListTag blueList = new ListTag();
        for (BlockPos pos : blueRallies) blueList.add(LongTag.valueOf(pos.asLong()));
        tag.put("BlueRallies", blueList);

        ListTag redList = new ListTag();
        for (BlockPos pos : redRallies) redList.add(LongTag.valueOf(pos.asLong()));
        tag.put("RedRallies", redList);

        ListTag hubList = new ListTag();
        for (HubInfo h : hubs) hubList.add(h.save());
        tag.put("HubsData", hubList);

        CompoundTag blueSpawnsTag = new CompoundTag();
        blueSpawns.forEach((dim, pos) -> blueSpawnsTag.putLong(dim, pos.asLong()));
        tag.put("BlueSpawnsMap", blueSpawnsTag);

        CompoundTag redSpawnsTag = new CompoundTag();
        redSpawns.forEach((dim, pos) -> redSpawnsTag.putLong(dim, pos.asLong()));
        tag.put("RedSpawnsMap", redSpawnsTag);

        CompoundTag neutralSpawnsTag = new CompoundTag();
        neutralSpawns.forEach((dim, pos) -> neutralSpawnsTag.putLong(dim, pos.asLong()));
        tag.put("NeutralSpawnsMap", neutralSpawnsTag);

        ListTag pointsList = new ListTag();
        for (CapturePoint point : capturePoints) pointsList.add(point.save());
        tag.put("CapturePoints", pointsList);

        ListTag squadList = new ListTag();
        for (Squad s : squads) squadList.add(s.save());
        tag.put("Squads", squadList);

        // === СОХРАНЕНИЕ КИТОВ ===
        CompoundTag bKitsTag = new CompoundTag();
        for (KitInfo k : blueKits.values()) bKitsTag.put(k.name, k.save());
        tag.put("BlueKits", bKitsTag);

        CompoundTag rKitsTag = new CompoundTag();
        for (KitInfo k : redKits.values()) rKitsTag.put(k.name, k.save());
        tag.put("RedKits", rKitsTag);

        return tag;
    }

    public static AASWorldData load(CompoundTag tag) {
        AASWorldData data = new AASWorldData();
        data.blueTickets = tag.getInt("BlueTickets");
        data.redTickets = tag.getInt("RedTickets");
        data.respawnTimer = tag.getInt("RespawnTimer");
        data.deathTicketCost = tag.getInt("DeathTicketCost");
        data.blueFaction = tag.getString("BlueFaction");
        data.redFaction = tag.getString("RedFaction");
        data.isGameStarted = tag.getBoolean("IsGameStarted");
        data.countdownTicks = tag.getInt("CountdownTicks");
        data.countdownActive = tag.getBoolean("CountdownActive");
        data.playedBlueSiren = tag.getBoolean("PlayedBlueSiren");
        data.playedRedSiren = tag.getBoolean("PlayedRedSiren");
        data.mapCenterX = tag.getInt("MapCenterX");
        data.mapCenterZ = tag.getInt("MapCenterZ");
        data.mapSizeBlocks = tag.contains("MapSizeBlocks") ? tag.getInt("MapSizeBlocks") : 2048;
        if (tag.contains("TacticalMarkers")) {
            ListTag list = tag.getList("TacticalMarkers", 10);
            for (int i = 0; i < list.size(); i++) data.activeMarkers.add(MapMarker.load(list.getCompound(i)));
        }
        if (tag.contains("MarkedVehicles")) {
            ListTag list = tag.getList("MarkedVehicles", 10);
            for (int i = 0; i < list.size(); i++) {
                data.markedVehicles.add(VehicleRecord.load(list.getCompound(i)));
            }
        }
        if (tag.contains("BlueRallies")) {
            ListTag list = tag.getList("BlueRallies", Tag.TAG_LONG);
            for (Tag t : list) data.blueRallies.add(BlockPos.of(((LongTag) t).getAsLong()));
        }
        if (tag.contains("RedRallies")) {
            ListTag list = tag.getList("RedRallies", Tag.TAG_LONG);
            for (Tag t : list) data.redRallies.add(BlockPos.of(((LongTag) t).getAsLong()));
        }

        if (tag.contains("HubsData")) {
            ListTag list = tag.getList("HubsData", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                data.hubs.add(HubInfo.load(list.getCompound(i)));
            }
        }

        if (tag.contains("BlueSpawnsMap")) {
            CompoundTag map = tag.getCompound("BlueSpawnsMap");
            for(String key : map.getAllKeys()) data.blueSpawns.put(key, BlockPos.of(map.getLong(key)));
        }
        if (tag.contains("RedSpawnsMap")) {
            CompoundTag map = tag.getCompound("RedSpawnsMap");
            for(String key : map.getAllKeys()) data.redSpawns.put(key, BlockPos.of(map.getLong(key)));
        }
        if (tag.contains("NeutralSpawnsMap")) {
            CompoundTag map = tag.getCompound("NeutralSpawnsMap");
            for(String key : map.getAllKeys()) data.neutralSpawns.put(key, BlockPos.of(map.getLong(key)));
        }
        if (tag.contains("CapturePoints")) {
            ListTag list = tag.getList("CapturePoints", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) data.capturePoints.add(CapturePoint.load(list.getCompound(i)));
        }
        if (tag.contains("Squads")) {
            ListTag list = tag.getList("Squads", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) data.squads.add(Squad.load(list.getCompound(i)));
        }

        // === ЗАГРУЗКА КИТОВ ===
        if (tag.contains("BlueKits")) {
            CompoundTag bk = tag.getCompound("BlueKits");
            for (String key : bk.getAllKeys()) data.blueKits.put(key, KitInfo.load(bk.getCompound(key)));
        }
        if (tag.contains("RedKits")) {
            CompoundTag rk = tag.getCompound("RedKits");
            for (String key : rk.getAllKeys()) data.redKits.put(key, KitInfo.load(rk.getCompound(key)));
        }

        return data;
    }

    public static AASWorldData get(ServerLevel level) {
        String dimId = level.dimension().location().toString().replace(":", "_");
        String dataName = "aas_data_" + dimId;

        return level.getDataStorage().computeIfAbsent(AASWorldData::load, AASWorldData::new, dataName);
    }

    public static class HubInfo {
        public BlockPos pos;
        public String team;
        public boolean constructed;
        public String dimension;
        public boolean isBlocked;
        public int materials; // <--- НОВОЕ ПОЛЕ

        public HubInfo(BlockPos pos, String team, boolean constructed, String dimension) {
            this.pos = pos;
            this.team = team;
            this.constructed = constructed;
            this.dimension = dimension;
            this.isBlocked = false;
            this.materials = 0; // <--- ИНИЦИАЛИЗАЦИЯ
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("Pos", pos.asLong());
            tag.putString("Team", team);
            tag.putBoolean("Constructed", constructed);
            tag.putString("Dimension", dimension != null ? dimension : "minecraft:overworld");
            tag.putInt("Materials", materials); // <--- СОХРАНЕНИЕ
            return tag;
        }

        public static HubInfo load(CompoundTag tag) {
            BlockPos p = BlockPos.of(tag.getLong("Pos"));
            String t = tag.getString("Team");
            boolean c = tag.getBoolean("Constructed");
            String d = tag.contains("Dimension") ? tag.getString("Dimension") : "minecraft:overworld";
            HubInfo h = new HubInfo(p, t, c, d);
            if (tag.contains("Materials")) h.materials = tag.getInt("Materials"); // <--- ЗАГРУЗКА
            return h;
        }
    }
    public static class SquadMarker {
        public int x, z;
        public int type; // 0-Move, 1-Attack, 2-Defend, 3-Build
        public long expiryTick;

        public SquadMarker(int x, int z, int type, long expiryTick) { // Обновить конструктор
            this.x = x; this.z = z; this.type = type;
            this.expiryTick = expiryTick;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("X", x);
            tag.putInt("Z", z);
            tag.putInt("Type", type);
            tag.putLong("Expiry", expiryTick);
            return tag;
        }

        public static SquadMarker load(CompoundTag tag) {
            return new SquadMarker(tag.getInt("X"), tag.getInt("Z"), tag.getInt("Type"), tag.getLong("Expiry"));
        }
    }
    public static class Squad {
        // Поля класса (объявляются здесь)
        public int id;
        public String name;
        public String team;
        public String leader;
        public boolean isLocked = false;
        public BlockPos rallyPos = null;
        public String dimension;
        public String rallyDimension;
        public List<String> members = new ArrayList<>();
        public SquadMarker marker = null; // Поле метки отряда
        public long rallyExpiryTick = -1;
        // Конструктор
        public Squad(int id, String name, String team, String leader, String dimension) {
            this.id = id;
            this.name = name;
            this.team = team;
            this.leader = leader;
            this.isLocked = false;
            this.rallyPos = null;
            this.dimension = dimension;
            this.rallyDimension = dimension;
            this.rallyExpiryTick = -1;
            this.marker = null;
        }

        // Метод сохранения
        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("ID", id);
            tag.putString("Name", name);
            tag.putString("Team", team);
            tag.putString("Leader", leader);
            tag.putBoolean("IsLocked", isLocked);
            tag.putString("Dimension", dimension != null ? dimension : "minecraft:overworld");
            tag.putLong("RallyExpiry", rallyExpiryTick);

            if (rallyPos != null) {
                tag.putLong("RallyPos", rallyPos.asLong());
                tag.putString("RallyDim", rallyDimension != null ? rallyDimension : "minecraft:overworld");
            }

            ListTag memList = new ListTag();
            for (String m : members) memList.add(StringTag.valueOf(m));
            tag.put("Members", memList);

            // Сохраняем метку ПЕРЕД тем как вернуть tag
            if (marker != null) {
                tag.put("SquadMarker", marker.save());
            }

            return tag;
        }

        // Метод загрузки
        public static Squad load(CompoundTag tag) {
            String l = tag.contains("Leader") ? tag.getString("Leader") : "";
            String dim = tag.contains("Dimension") ? tag.getString("Dimension") : "minecraft:overworld";

            Squad s = new Squad(tag.getInt("ID"), tag.getString("Name"), tag.getString("Team"), l, dim);
            s.rallyExpiryTick = tag.getLong("RallyExpiry");

            if (tag.contains("IsLocked")) s.isLocked = tag.getBoolean("IsLocked");

            if (tag.contains("RallyPos")) {
                s.rallyPos = BlockPos.of(tag.getLong("RallyPos"));
                s.rallyDimension = tag.contains("RallyDim") ? tag.getString("RallyDim") : dim;
            }

            if (tag.contains("Members")) {
                ListTag memList = tag.getList("Members", Tag.TAG_STRING);
                for (Tag t : memList) s.members.add(t.getAsString());
            }

            if (s.leader.isEmpty() && !s.members.isEmpty()) s.leader = s.members.get(0);

            // Загружаем метку ПЕРЕД тем как вернуть объект s
            if (tag.contains("SquadMarker")) {
                s.marker = SquadMarker.load(tag.getCompound("SquadMarker"));
            }

            return s;
        }
    }
    public List<MapMarker> activeMarkers = new ArrayList<>();

    public static class MapMarker {
        public BlockPos pos;
        public String type;
        public String team;
        public long expiryTick;

        public MapMarker(BlockPos pos, String type, String team, long expiryTick) {
            this.pos = pos;
            this.type = type;
            this.team = team;
            this.expiryTick = expiryTick;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("Pos", pos.asLong());
            tag.putString("Type", type);
            tag.putString("Team", team);
            tag.putLong("Expiry", expiryTick);
            return tag;
        }

        public static MapMarker load(CompoundTag tag) {
            return new MapMarker(BlockPos.of(tag.getLong("Pos")), tag.getString("Type"), tag.getString("Team"), tag.getLong("Expiry"));
        }
    }
    public static class CapturePoint {
        public String name; public AABB area; public int bluePriority; public int redPriority; public int captureTimeMinutes; public int ticketPenalty; public int captureDeduction;
        public String owner = "NEUTRAL"; public float progress = 0.0f; public String capturingTeam = "NONE";

        // НОВЫЕ ПОЛЯ
        public String shapeType = "CUBE";
        public int lockDurationMinutes = 0;
        public long lockedUntilTick = 0;

        public CapturePoint(String name, AABB area, int bp, int rp, int time, int penalty, int deduct, String shape, int lockMin) {
            this.name = name; this.area = area; this.bluePriority = bp; this.redPriority = rp; this.captureTimeMinutes = time; this.ticketPenalty = penalty; this.captureDeduction = deduct;
            this.shapeType = shape;
            this.lockDurationMinutes = lockMin;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Name", name);
            tag.putDouble("minX", area.minX); tag.putDouble("minY", area.minY); tag.putDouble("minZ", area.minZ);
            tag.putDouble("maxX", area.maxX); tag.putDouble("maxY", area.maxY); tag.putDouble("maxZ", area.maxZ);
            tag.putInt("BluePriority", bluePriority); tag.putInt("RedPriority", redPriority); tag.putInt("Time", captureTimeMinutes);
            tag.putInt("Penalty", ticketPenalty); tag.putInt("Deduct", captureDeduction);
            tag.putString("Owner", owner); tag.putFloat("Progress", progress); tag.putString("CapturingTeam", capturingTeam);
            // Новые
            tag.putString("Shape", shapeType);
            tag.putInt("LockMin", lockDurationMinutes);
            tag.putLong("LockedUntil", lockedUntilTick);
            return tag;
        }

        public static CapturePoint load(CompoundTag tag) {
            AABB area = new AABB(tag.getDouble("minX"), tag.getDouble("minY"), tag.getDouble("minZ"), tag.getDouble("maxX"), tag.getDouble("maxY"), tag.getDouble("maxZ"));
            CapturePoint point = new CapturePoint(
                    tag.getString("Name"), area,
                    tag.getInt("BluePriority"), tag.getInt("RedPriority"),
                    tag.getInt("Time"), tag.getInt("Penalty"),
                    tag.getInt("Deduct"),
                    tag.contains("Shape") ? tag.getString("Shape") : "CUBE",
                    tag.contains("LockMin") ? tag.getInt("LockMin") : 0
            );
            if (tag.contains("Owner")) point.owner = tag.getString("Owner");
            if (tag.contains("Progress")) point.progress = tag.getFloat("Progress");
            if (tag.contains("CapturingTeam")) point.capturingTeam = tag.getString("CapturingTeam");
            if (tag.contains("LockedUntil")) point.lockedUntilTick = tag.getLong("LockedUntil");
            return point;
        }

        // Проверка вхождения в зону с учетом формы
        public boolean isInside(net.minecraft.world.phys.Vec3 pos) {
            if ("CYLINDER".equalsIgnoreCase(shapeType)) {
                // Вычисляем центр круга (X и Z)
                double centerX = (area.minX + area.maxX) / 2.0;
                double centerZ = (area.minZ + area.maxZ) / 2.0;
                // Радиус — это половина ширины AABB
                double radius = (area.maxX - area.minX) / 2.0;

                double dx = pos.x - centerX;
                double dz = pos.z - centerZ;

                // 1. Проверка: находится ли игрок внутри круга по горизонтали
                boolean inCircle = (dx * dx + dz * dz) <= (radius * radius);
                // 2. Проверка: находится ли игрок в пределах высоты (Y)
                boolean inHeight = (pos.y >= area.minY && pos.y <= area.maxY);

                return inCircle && inHeight;
            }
            // Если КУБ, используем стандартный метод AABB
            return area.contains(pos);
        }
    }

    // === НОВЫЙ ВНУТРЕННИЙ КЛАСС ДЛЯ КИТОВ ===
    public static class KitInfo {
        public String name;
        public net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> inventory = net.minecraft.core.NonNullList.withSize(41, net.minecraft.world.item.ItemStack.EMPTY);
        public boolean[] resupplyFlags = new boolean[41];
        public boolean isLeaderOnly = false;
        public boolean[] saveNbtFlags = new boolean[41];
        public int maxPerTeam = -1; // -1 = бесконечно, 0 = выключен
        public int maxPerSquad = -1;
        public int minSquadPlayers = 0;

        public KitInfo(String name) { this.name = name; }

        public CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("Name", name);
            t.putBoolean("LeaderOnly", isLeaderOnly);
            t.putInt("MaxTeam", maxPerTeam);
            t.putInt("MaxSquad", maxPerSquad);
            t.putInt("MinSquadPlayers", minSquadPlayers);
            ListTag items = new ListTag();
            for (int i = 0; i < 41; i++) {
                if (!inventory.get(i).isEmpty()) {
                    CompoundTag itemTag = new CompoundTag();
                    itemTag.putByte("Slot", (byte) i);
                    itemTag.putBoolean("Resupply", resupplyFlags[i]);
                    itemTag.putBoolean("SaveNbt", saveNbtFlags[i]);
                    inventory.get(i).save(itemTag); // Сохраняет предмет вместе с NBT
                    items.add(itemTag);
                }
            }
            t.put("Items", items);
            return t;
        }

        public static KitInfo load(CompoundTag t) {
            KitInfo k = new KitInfo(t.getString("Name"));
            k.isLeaderOnly = t.getBoolean("LeaderOnly");
            k.maxPerTeam = t.getInt("MaxTeam");
            k.maxPerSquad = t.getInt("MaxSquad");
            if (t.contains("MinSquadPlayers")) {
                k.minSquadPlayers = t.getInt("MinSquadPlayers");
            }
            ListTag items = t.getList("Items", 10);
            for (int i = 0; i < items.size(); i++) {
                CompoundTag itemTag = items.getCompound(i);
                int slot = itemTag.getByte("Slot") & 255;
                if (slot >= 0 && slot < 41) {
                    k.inventory.set(slot, net.minecraft.world.item.ItemStack.of(itemTag));
                    k.resupplyFlags[slot] = itemTag.getBoolean("Resupply");
                    k.saveNbtFlags[slot] = itemTag.getBoolean("SaveNbt");
                }
            }
            return k;
        }
    }
    // Внутри AASWorldData.java
    public static class VehicleRecord {
        public UUID uuid;
        public String team;
        public String type;
        public double x, y, z;
        public float yaw;
        public BlockPos spawnerPos; // <--- 1. Поле должно быть тут

        // 2. Конструктор ДОЛЖЕН принимать 8 аргументов
        public VehicleRecord(UUID uuid, String team, String type, double x, double y, double z, float yaw, BlockPos spawnerPos) {
            this.uuid = uuid;
            this.team = team;
            this.type = type;
            this.x = x; this.y = y; this.z = z;
            this.yaw = yaw;
            this.spawnerPos = spawnerPos; // <--- 3. Присваиваем
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("UUID", uuid);
            tag.putString("Team", team);
            tag.putString("Type", type);
            tag.putDouble("X", x); tag.putDouble("Y", y); tag.putDouble("Z", z);
            tag.putFloat("Yaw", yaw);
            if (spawnerPos != null) tag.putLong("SpawnerPos", spawnerPos.asLong()); // Сохранение
            return tag;
        }

        public static VehicleRecord load(CompoundTag tag) {
            BlockPos sPos = tag.contains("SpawnerPos") ? BlockPos.of(tag.getLong("SpawnerPos")) : null;
            // 4. В методе load тоже передаем 8 аргументов
            return new VehicleRecord(tag.getUUID("UUID"), tag.getString("Team"), tag.getString("Type"),
                    tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"), tag.getFloat("Yaw"), sPos);
        }
    }
}