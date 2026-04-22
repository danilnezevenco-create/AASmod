package com.example.aas.network;

import com.example.aas.client.ClientData;
import com.example.aas.world.AASWorldData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class PacketSyncSquads {
    private final CompoundTag data;

    public PacketSyncSquads(List<AASWorldData.Squad> squads) {
        this.data = new CompoundTag();
        ListTag list = new ListTag();
        for (AASWorldData.Squad s : squads) list.add(s.save());
        this.data.put("List", list);
    }

    public PacketSyncSquads(FriendlyByteBuf buf) {
        this.data = buf.readNbt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeNbt(data);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                ClientData.clientSquads.clear();
                ListTag list = data.getList("List", Tag.TAG_COMPOUND);
                for (int i = 0; i < list.size(); i++) {
                    ClientData.clientSquads.add(AASWorldData.Squad.load(list.getCompound(i)));
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }
}