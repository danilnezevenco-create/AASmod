package com.example.aas.client.renderer;
import com.example.aas.client.model.AGS30Model;
import com.example.aas.entity.AGS30Entity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
public class AGS30Renderer extends GeoEntityRenderer<AGS30Entity> {
    public AGS30Renderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new AGS30Model());
        this.shadowRadius = 0.7f;
    }
}