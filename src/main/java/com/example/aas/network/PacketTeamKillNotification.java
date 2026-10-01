package com.example.aas.network;

import com.example.aas.client.TeamKillOverlay;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Сервер -> клиент. Уведомление об убийстве/ноке союзника.
 *  youAreKiller = true  -> получает убийца
 *  youAreKiller = false -> получает жертва
 *  knocked      = true  -> союзника только нокнули (не убили)
 */
public class PacketTeamKillNotification {
    private final String otherName;
    private final boolean youAreKiller;
    private final boolean knocked;

    public PacketTeamKillNotification(String otherName, boolean youAreKiller, boolean knocked) {
        this.otherName = otherName;
        this.youAreKiller = youAreKiller;
        this.knocked = knocked;
    }

    public PacketTeamKillNotification(String otherName, boolean youAreKiller) {
        this(otherName, youAreKiller, false);
    }

    public static void encode(PacketTeamKillNotification msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.otherName);
        buf.writeBoolean(msg.youAreKiller);
        buf.writeBoolean(msg.knocked);
    }

    public static PacketTeamKillNotification decode(FriendlyByteBuf buf) {
        return new PacketTeamKillNotification(buf.readUtf(), buf.readBoolean(), buf.readBoolean());
    }

    public static void handle(PacketTeamKillNotification msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        TeamKillOverlay.push(msg.otherName, msg.youAreKiller, msg.knocked)));
        ctx.get().setPacketHandled(true);
    }
}