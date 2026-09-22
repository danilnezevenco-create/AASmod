package com.example.aas.client;

import com.example.aas.client.renderer.EntrenchingToolRenderer;
import com.example.aas.client.renderer.SquadRadioRenderer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

public class ClientItemExtensions {

    public static final IClientItemExtensions ENTRENCHING_TOOL = new IClientItemExtensions() {
        private EntrenchingToolRenderer renderer;
        @Override
        public BlockEntityWithoutLevelRenderer getCustomRenderer() {
            if (renderer == null) renderer = new EntrenchingToolRenderer();
            return renderer;
        }
    };

    public static final IClientItemExtensions RALLY_RADIO = new IClientItemExtensions() {
        private SquadRadioRenderer renderer;
        @Override
        public BlockEntityWithoutLevelRenderer getCustomRenderer() {
            if (renderer == null) renderer = new SquadRadioRenderer();
            return renderer;
        }
    };

    public static final IClientItemExtensions OFFICER_PHONE = new IClientItemExtensions() {
        private com.example.aas.client.renderer.OfficerPhoneRenderer renderer;
        @Override
        public BlockEntityWithoutLevelRenderer getCustomRenderer() {
            if (renderer == null) renderer = new com.example.aas.client.renderer.OfficerPhoneRenderer();
            return renderer;
        }
    };
    public static final IClientItemExtensions BINOCULARS = new IClientItemExtensions() {
        private com.example.aas.client.renderer.BinocularsRenderer renderer;
        @Override
        public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
            if (renderer == null) renderer = new com.example.aas.client.renderer.BinocularsRenderer();
            return renderer;
        }
    };
}