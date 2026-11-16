package com.meteorologia.client;

import com.meteorologia.entity.HotAirBalloon;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Wicker basket (hollow, with a rim), corner posts, burner frame and burner.
 * The big fabric envelope is drawn procedurally by {@link BalloonRenderer}.
 * Texture: 128x128.
 */
public class BalloonModel extends EntityModel<HotAirBalloon> {
    private final ModelPart root;

    public BalloonModel(ModelPart root) {
        this.root = root;
    }

    private static void box(PartDefinition parent, String name, int u, int v,
                            float x, float y, float z, float dx, float dy, float dz) {
        parent.addOrReplaceChild(name, CubeListBuilder.create().texOffs(u, v).addBox(x, y, z, dx, dy, dz), PartPose.ZERO);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition body = mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create(),
                PartPose.offset(0F, 24F, 0F));

        // basket: floor + four walls (hollow so the passengers are visible inside)
        box(body, "floor", 0, 40, -12F, -2F, -12F, 24F, 2F, 24F);
        box(body, "wall_n", 0, 0, -12F, -16F, -12F, 24F, 14F, 2F);
        box(body, "wall_s", 0, 0, -12F, -16F, 10F, 24F, 14F, 2F);
        box(body, "wall_w", 0, 0, -12F, -16F, -10F, 2F, 14F, 20F);
        box(body, "wall_e", 0, 0, 10F, -16F, -10F, 2F, 14F, 20F);
        // rim
        box(body, "rim_n", 0, 68, -13F, -18F, -13F, 26F, 2F, 3F);
        box(body, "rim_s", 0, 68, -13F, -18F, 10F, 26F, 2F, 3F);
        box(body, "rim_w", 0, 68, -13F, -18F, -10F, 3F, 2F, 20F);
        box(body, "rim_e", 0, 68, 10F, -18F, -10F, 3F, 2F, 20F);
        // corner posts up to the burner frame
        box(body, "post_nw", 0, 80, -13F, -46F, -13F, 2F, 28F, 2F);
        box(body, "post_ne", 0, 80, 11F, -46F, -13F, 2F, 28F, 2F);
        box(body, "post_sw", 0, 80, -13F, -46F, 11F, 2F, 28F, 2F);
        box(body, "post_se", 0, 80, 11F, -46F, 11F, 2F, 28F, 2F);
        // burner frame
        box(body, "frame_n", 20, 80, -13F, -48F, -13F, 26F, 2F, 2F);
        box(body, "frame_s", 20, 80, -13F, -48F, 11F, 26F, 2F, 2F);
        box(body, "frame_w", 20, 80, -13F, -48F, -11F, 2F, 2F, 22F);
        box(body, "frame_e", 20, 80, 11F, -48F, -11F, 2F, 2F, 22F);
        // burner and coil
        box(body, "burner", 60, 80, -4F, -56F, -4F, 8F, 8F, 8F);
        box(body, "coil", 60, 100, -3F, -62F, -3F, 6F, 6F, 6F);

        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public void setupAnim(HotAirBalloon entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        // no animation (the renderer sways the whole balloon)
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        root.render(poseStack, buffer, packedLight, packedOverlay, color);
    }
}
