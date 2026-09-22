// FILE: PacketDownedAction.java
// PATH: src\main\java\com\example\aas\network\PacketDownedAction.java
package com.example.aas.network;

import com.example.aas.sound.ModSounds;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.network.NetworkEvent;
import com.example.aas.world.AASWorldData;

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
            // В файле PacketDownedAction.java в методе handle (внутри else if (msg.action == 0))
            // В PacketDownedAction.java внутри метода handle, в блоке action == 0 (Call Medic)
            else if (msg.action == 0) {
                long currentTime = level.getGameTime();
                long lastCall = player.getPersistentData().getLong("AAS_LastMedicShout");

                if (currentTime - lastCall >= 300) {
                    player.getPersistentData().putLong("AAS_LastMedicShout", currentTime);
                    player.getPersistentData().putLong("AAS_LastMedicShoutTimeMS", level.getGameTime());

                    AASWorldData data = AASWorldData.get(level);
                    String faction = "none";

                    if (player.getTeam() != null) {
                        String teamName = player.getTeam().getName();
                        faction = teamName.equalsIgnoreCase("Blue") ? data.blueFaction : data.redFaction;
                    }

                    net.minecraft.sounds.SoundEvent finalSound;

                    // ПРОВЕРКА: Если фракция не установлена ("none" или пустая), сразу ставим обычный крик
                    if (faction == null || faction.isEmpty() || faction.equalsIgnoreCase("none")) {
                        finalSound = ModSounds.HELP_SCREAM.get();
                    }
                    // Иначе проверяем, есть ли такая фракция в нашем списке звуков
                    else if (ModSounds.FACTION_SCREAMS.containsKey(faction.toLowerCase())) {
                        int randomIndex = player.getRandom().nextInt(3);
                        finalSound = ModSounds.FACTION_SCREAMS.get(faction.toLowerCase()).get(randomIndex).get();
                    }
                    // Если фракция какая-то странная, которой нет в списке — тоже обычный крик
                    else {
                        finalSound = ModSounds.HELP_SCREAM.get();
                    }

                    level.playSound(null, player.getX(), player.getY(), player.getZ(), finalSound, SoundSource.PLAYERS, 2.0F, 1.0F);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}