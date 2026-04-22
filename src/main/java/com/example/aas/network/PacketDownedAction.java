// FILE: PacketDownedAction.java
// PATH: src\main\java\com\example\aas\network\PacketDownedAction.java
package com.example.aas.network;

import com.example.aas.sound.ModSounds;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketDownedAction {
    private final int action; // 0 = Call Medic, 1 = Give Up

    public PacketDownedAction(int action) {
        this.action = action;
    }

    public static void encode(PacketDownedAction msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.action);
    }

    public static PacketDownedAction decode(FriendlyByteBuf buf) {
        return new PacketDownedAction(buf.readInt());
    }

    public static void handle(PacketDownedAction msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ServerLevel level = (ServerLevel) player.level();

            if (msg.action == 1) { // Нажата кнопка GIVE UP
                // Вся логика смерти, сообщений в чат и снятия тикетов теперь находится в этом методе:
                com.example.aas.events.DownedHandler.forceGiveUp(player);
            }
            else if (msg.action == 0) { // CALL MEDIC
                long currentTime = level.getGameTime();
                long lastCall = player.getPersistentData().getLong("AAS_LastMedicShout");

                // Защита от спама кнопкой "Позвать медика" (задержка 5 секунд)
                if (currentTime - lastCall >= 100) {
                    player.getPersistentData().putLong("AAS_LastMedicShout", currentTime);
                    player.getPersistentData().putLong("AAS_LastMedicShoutTimeMS", System.currentTimeMillis());

                    level.playSound(null, player.blockPosition(),
                            ModSounds.HELP_SCREAM.get(), SoundSource.PLAYERS, 2.0F, 1.0F);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}