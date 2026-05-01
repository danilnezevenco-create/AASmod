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
}