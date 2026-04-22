package com.example.aas.client.renderer;

import com.example.aas.client.model.M2BrowningModel;
import com.example.aas.entity.M2BrowningEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class M2BrowningRenderer extends GeoEntityRenderer<M2BrowningEntity> {
    public M2BrowningRenderer(EntityRendererProvider.Context renderManager) {
        // Мы используем модель M2BrowningModel, которую создали ранее
        super(renderManager, new M2BrowningModel());
        this.shadowRadius = 0.7f; // Размер тени под пулеметом
    }
}