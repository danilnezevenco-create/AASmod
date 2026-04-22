// PATH: src\main\java\com\example\aas\network\PacketOpenPlayerKitMenu.java
package com.example.aas.network;

import com.example.aas.client.ClientHooks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class PacketOpenPlayerKitMenu {
    public static class KitDTO {
        public String name;
        public boolean available;
        public String reason;
        public List<ItemStack> items; // <--- ДОБАВЛЕНО

        public KitDTO(String n, boolean a, String r, List<ItemStack> items) {
            this.name = n; this.available = a; this.reason = r; this.items = items;
        }
    }

    public final List<KitDTO> kits;
    public PacketOpenPlayerKitMenu(List<KitDTO> kits) { this.kits = kits; }

    public static void encode(PacketOpenPlayerKitMenu msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.kits.size());
        for(KitDTO k : msg.kits) {
            buf.writeUtf(k.name);
            buf.writeBoolean(k.available);
            buf.writeUtf(k.reason);
            // Пишем предметы
            buf.writeInt(k.items.size());
            for(ItemStack stack : k.items) buf.writeItem(stack);
        }
    }

    public static PacketOpenPlayerKitMenu decode(FriendlyByteBuf buf) {
        int size = buf.readInt();
        List<KitDTO> list = new ArrayList<>();
        for(int i=0; i<size; i++) {
            String name = buf.readUtf();
            boolean avail = buf.readBoolean();
            String reason = buf.readUtf();
            int itemCount = buf.readInt();
            List<ItemStack> items = new ArrayList<>();
            for(int j=0; j<itemCount; j++) items.add(buf.readItem());

            list.add(new KitDTO(name, avail, reason, items));
        }
        return new PacketOpenPlayerKitMenu(list);
    }

    public static void handle(PacketOpenPlayerKitMenu msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.openPlayerKitMenu(msg.kits));
        });
        ctx.get().setPacketHandled(true);
    }
}