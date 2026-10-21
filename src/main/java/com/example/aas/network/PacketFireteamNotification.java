package com.example.aas.network;

import com.example.aas.client.TeamKillOverlay;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Сервер -> клиент. Личное уведомление о фаертиме (Bravo/Charlie).
 *  type 0 = вас добавили в группу
 *  type 1 = вы назначены лидером группы
 *  type 2 = у вашей группы сменился лидер (name = новый лидер)
 *  type 3 = ваш лидер группы (name = лидер)
 */
public class PacketFireteamNotification {
    private final int type;
    private final boolean bravo;
    private final String name;

    public PacketFireteamNotification(int type, boolean bravo, String name) {
        this.type = type;
        this.bravo = bravo;
        this.name = name;
    }

    public static void encode(PacketFireteamNotification msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.type);
        buf.writeBoolean(msg.bravo);
        buf.writeUtf(msg.name);
    }

    public static PacketFireteamNotification decode(FriendlyByteBuf buf) {
        return new PacketFireteamNotification(buf.readInt(), buf.readBoolean(), buf.readUtf());
    }

    public static void handle(PacketFireteamNotification msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        TeamKillOverlay.pushFireteam(msg.type, msg.bravo, msg.name)));
        ctx.get().setPacketHandled(true);
    }
}