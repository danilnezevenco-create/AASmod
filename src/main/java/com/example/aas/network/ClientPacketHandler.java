package com.example.aas.network;

import com.example.aas.client.ClientData;
// Раскомментируйте или добавьте этот импорт, если он пропал
import com.example.aas.network.MapPlayerInfo;

public class ClientPacketHandler {
    public static void handleSyncMap(PacketSyncMapPlayers msg) {
        ClientData.mapPlayers.clear();
        for (MapPlayerInfo info : msg.getPlayers()) {
            ClientData.mapPlayers.put(info.name, info);
        }
    }
}