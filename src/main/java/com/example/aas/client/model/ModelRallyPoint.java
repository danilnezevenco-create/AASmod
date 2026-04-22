package com.example.aas.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

// Мы убрали <T extends Entity>, так как для блока это не критично, чтобы упростить код
public class ModelRallyPoint extends EntityModel<Entity> {
    // Ссылка на слой (не обязательно использовать, если мы создаем модель вручную в рендерере)
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(new ResourceLocation("aas", "rally_point"), "main");
    private final ModelPart Rally;

    // Исправлено имя конструктора: теперь оно совпадает с именем класса
    public ModelRallyPoint(ModelPart root) {
        this.Rally = root.getChild("Rally");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        // Твоя геометрия из Blockbench
        PartDefinition Rally = partdefinition.addOrReplaceChild("Rally", CubeListBuilder.create()
                        .texOffs(0, 42).addBox(-8.0F, 2.0F, -8.0F, 16.0F, 6.0F, 16.0F, new CubeDeformation(0.0F))
                        .texOffs(52, 30).addBox(-7.0F, 0.0F, -4.0F, 4.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
                        .texOffs(40, 34).addBox(-3.0F, -1.0F, -5.0F, 7.0F, 3.0F, 5.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 16.0F, 0.0F));

        return LayerDefinition.create(meshdefinition, 64, 64);
    }

    @Override
    public void setupAnim(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        // Для простого блока анимация не нужна
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        Rally.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}