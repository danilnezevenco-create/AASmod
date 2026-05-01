package com.example.aas.network;

import com.example.aas.client.ClientData;
// Импорт MapPlayerInfo удален, так как они в одном пакете

public class ClientPacketHandler {
    public static void handleSyncMap(PacketSyncMapPlayers msg) {
        ClientData.mapPlayers.clear();
        for (MapPlayerInfo info : msg.getPlayers()) { // Используйте геттер или прямой доступ
            ClientData.mapPlayers.put(info.name, info);
        }
    }
}