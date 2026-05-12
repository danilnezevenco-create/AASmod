// PATH: src\main\java\com\example\aas\network\PacketSaveKit.java
package com.example.aas.network;

import com.example.aas.menu.KitEditorMenu;
import com.example.aas.world.AASWorldData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class PacketSaveKit {
    private final String team;
    private final String kitName;
    private final boolean isLeader;
    private final int maxTeam;
    private final int maxSquad;
    private final int minSquadPlayers;
    private final boolean[] resupplyFlags; // Переименовал для ясности
    private final boolean[] nbtFlags;      // <--- 1. ДОБАВЛЕНО ПОЛЕ

    // 2. ОБНОВЛЕН КОНСТРУКТОР
    public PacketSaveKit(String team, String kitName, boolean isLeader, int maxTeam, int maxSquad, int minSquadPlayers, boolean[] resupplyFlags, boolean[] nbtFlags) {
        this.team = team;
        this.kitName = kitName;
        this.isLeader = isLeader;
        this.maxTeam = maxTeam;
        this.maxSquad = maxSquad;
        this.minSquadPlayers = minSquadPlayers;
        this.resupplyFlags = resupplyFlags;
        this.nbtFlags = nbtFlags; // <--- ПРИСВАИВАЕМ
    }

    public static void encode(PacketSaveKit msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.team);
        buf.writeUtf(msg.kitName);
        buf.writeBoolean(msg.isLeader);
        buf.writeInt(msg.maxTeam);
        buf.writeInt(msg.maxSquad);
        buf.writeInt(msg.minSquadPlayers);

        // 3. ПИШЕМ ПЕРВЫЙ МАССИВ (Ресаплай)
        for(int i=0; i<49; i++) buf.writeBoolean(msg.resupplyFlags[i]);

        // 4. ПИШЕМ ВТОРОЙ МАССИВ (NBT)
        for(int i=0; i<49; i++) buf.writeBoolean(msg.nbtFlags[i]);
    }

    public static PacketSaveKit decode(FriendlyByteBuf buf) {
        String t = buf.readUtf();
        String k = buf.readUtf();
        boolean l = buf.readBoolean();
        int mt = buf.readInt();
        int ms = buf.readInt();
        int minP = buf.readInt();

        // 5. ЧИТАЕМ ПЕРВЫЙ МАССИВ
        boolean[] f1 = new boolean[49];
        for(int i=0; i<49; i++) f1[i] = buf.readBoolean();

        // 6. ЧИТАЕМ ВТОРОЙ МАССИВ
        boolean[] f2 = new boolean[49];
        for(int i=0; i<49; i++) f2[i] = buf.readBoolean();

        return new PacketSaveKit(t, k, l, mt, ms, minP, f1, f2);
    }

    public static void handle(PacketSaveKit msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            // Проверяем, что у игрока открыто именно меню редактора
            if (player != null && player.isCreative() && player.containerMenu instanceof KitEditorMenu menu) {
                AASWorldData data = AASWorldData.get(player.serverLevel());
                AASWorldData.KitInfo kit = msg.team.equals("BLUE") ? data.blueKits.get(msg.kitName) : data.redKits.get(msg.kitName);

                if (kit != null) {
                    kit.isLeaderOnly = msg.isLeader;
                    kit.maxPerTeam = msg.maxTeam;
                    kit.maxPerSquad = msg.maxSquad;
                    kit.minSquadPlayers = msg.minSquadPlayers;

                    // 7. СОХРАНЯЕМ ОБА МАССИВА В ДАННЫЕ МИРА
                    kit.resupplyFlags = msg.resupplyFlags;
                    kit.saveNbtFlags = msg.nbtFlags; // <--- ВАЖНО: сохраняем NBT флаги

                    // Копируем предметы из инвентаря меню в кит
                    for(int i=0; i<49; i++) {
                        kit.inventory.set(i, menu.kitInventory.getItem(i).copy());
                    }

                    data.setDirty();
                    // Синхронизируем обновленные данные со всеми клиентами
                    PacketHandler.sendToAllClients(player.serverLevel(), data);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}