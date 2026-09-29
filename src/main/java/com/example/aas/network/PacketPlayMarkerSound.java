// PATH: src/main/java/com/example/aas/network/PacketPlayMarkerSound.java
package com.example.aas.network;

import com.example.aas.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Сервер -> клиент: разовый эффект "метка установлена" для одного игрока.
 * Используется, чтобы при установке сквад-метки (Move/Attack/Defend/Build/Eye/Ромб)
 * звук проигрывался не только у того, кто поставил метку, но и у остальных членов отряда.
 */
public class PacketPlayMarkerSound {

    public PacketPlayMarkerSound() {
    }

    public static void encode(PacketPlayMarkerSound msg, FriendlyByteBuf buf) {
        // полей нет — нечего передавать
    }

    public static PacketPlayMarkerSound decode(FriendlyByteBuf buf) {
        return new PacketPlayMarkerSound();
    }

    public static void handle(PacketPlayMarkerSound msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        mc.player.playSound(ModSounds.MAP_MARKER_PLACE.get(), 1.0f, 1.0f);
                    }
                }));
        ctx.get().setPacketHandled(true);
    }
}