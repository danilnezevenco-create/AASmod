package com.example.aas.events;

import com.example.aas.network.PacketFireteamNotification;
import com.example.aas.network.PacketHandler;
import com.example.aas.world.AASWorldData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/** Личные уведомления о фаертимах. Каждое уходит ТОЛЬКО тому игроку, которого касается. */
public final class FireteamNotifier {
    public static final int ADDED = 0, YOU_LEADER = 1, LEADER_CHANGED = 2, YOUR_LEADER = 3;

    private static void send(MinecraftServer server, String playerName, int type, boolean bravo, String name) {
        if (playerName == null || playerName.isEmpty()) return;
        ServerPlayer sp = server.getPlayerList().getPlayerByName(playerName);
        if (sp == null) return; // оффлайн - пропускаем
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> sp),
                new PacketFireteamNotification(type, bravo, name == null ? "" : name));
    }

    /** Игрока добавили в группу: "вас добавили" + (если лидер уже есть) "ваш лидер". */
    public static void onAdded(MinecraftServer server, AASWorldData.Squad squad, String target, boolean bravo) {
        send(server, target, ADDED, bravo, "");
        String leader = bravo ? squad.bravoLeader : squad.charlieLeader;
        if (!leader.isEmpty() && !leader.equals(target)) {
            send(server, target, YOUR_LEADER, bravo, leader);
        }
    }

    /** Назначили лидера: ему "вы лидер", остальным участникам группы "лидер сменился" / "ваш лидер". */
    public static void onLeaderAssigned(MinecraftServer server, AASWorldData.Squad squad,
                                        String target, String oldLeader, boolean bravo) {
        send(server, target, YOU_LEADER, bravo, "");
        List<String> members = new ArrayList<>(bravo ? squad.bravoMembers : squad.charlieMembers);
        int type = (oldLeader == null || oldLeader.isEmpty()) ? YOUR_LEADER : LEADER_CHANGED;
        for (String m : members) {
            if (!m.equals(target)) send(server, m, type, bravo, target);
        }
    }

    private FireteamNotifier() {
    }
}