package com.example.aas.item;

import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import java.util.function.Consumer;

public class OfficerPhoneItem extends RallyItem {
    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(com.example.aas.client.ClientItemExtensions.OFFICER_PHONE);
    }
}