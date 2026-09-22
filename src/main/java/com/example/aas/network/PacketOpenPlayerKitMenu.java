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
        public List<ItemStack> items;

        public boolean hasAlt;
        public List<ItemStack> altItems;

        public boolean isOfficer;

        // === НОВОЕ: раздельные отображаемые названия ===
        public String displayName;     // имя STANDARD-версии (уже с фоллбэком на name)
        public String altDisplayName;  // имя ALTERNATIVE-версии (уже с фоллбэком на name)

        public KitDTO(String n, boolean a, String r, List<ItemStack> items, boolean hasAlt, List<ItemStack> altItems,
                      boolean isOfficer, String displayName, String altDisplayName) {
            this.name = n; this.available = a; this.reason = r; this.items = items;
            this.hasAlt = hasAlt; this.altItems = altItems;
            this.isOfficer = isOfficer;
            this.displayName = (displayName == null || displayName.isEmpty()) ? n : displayName;
            this.altDisplayName = (altDisplayName == null || altDisplayName.isEmpty()) ? n : altDisplayName;
        }

        // Старые сигнатуры — оставлены для обратной совместимости, displayName = name по умолчанию.
        public KitDTO(String n, boolean a, String r, List<ItemStack> items, boolean hasAlt, List<ItemStack> altItems, boolean isOfficer) {
            this(n, a, r, items, hasAlt, altItems, isOfficer, n, n);
        }

        public KitDTO(String n, boolean a, String r, List<ItemStack> items, boolean hasAlt, List<ItemStack> altItems) {
            this(n, a, r, items, hasAlt, altItems, false, n, n);
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
            buf.writeInt(k.items.size());
            for(ItemStack stack : k.items) buf.writeItem(stack);

            buf.writeBoolean(k.hasAlt);
            buf.writeInt(k.altItems.size());
            for(ItemStack stack : k.altItems) buf.writeItem(stack);

            buf.writeBoolean(k.isOfficer);
            buf.writeUtf(k.displayName);      // <-- НОВОЕ
            buf.writeUtf(k.altDisplayName);   // <-- НОВОЕ
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

            boolean hasAlt = buf.readBoolean();
            int altItemCount = buf.readInt();
            List<ItemStack> altItems = new ArrayList<>();
            for(int j=0; j<altItemCount; j++) altItems.add(buf.readItem());

            boolean isOfficer = buf.readBoolean();
            String displayName = buf.readUtf();      // <-- НОВОЕ
            String altDisplayName = buf.readUtf();   // <-- НОВОЕ

            list.add(new KitDTO(name, avail, reason, items, hasAlt, altItems, isOfficer, displayName, altDisplayName));
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