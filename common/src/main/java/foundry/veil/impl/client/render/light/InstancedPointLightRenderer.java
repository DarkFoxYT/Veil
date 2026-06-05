package foundry.veil.impl.client.render.light;

import com.mojang.blaze3d.vertex.Tesselator;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import foundry.veil.Veil;
import foundry.veil.api.client.render.light.data.PointLightData;
import foundry.veil.api.client.render.light.renderer.DDALightRenderer;
import foundry.veil.api.client.render.light.renderer.InstancedLightRenderer;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import foundry.veil.api.client.render.light.renderer.LightTypeRenderer;
import foundry.veil.api.client.render.rendertype.VeilRenderType;
import foundry.veil.api.client.render.vertex.VertexArrayBuilder;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3fc;

import java.util.List;

@ApiStatus.Internal
public class InstancedPointLightRenderer extends InstancedLightRenderer<PointLightData> implements DDALightRenderer<PointLightData> {

    private static final Identifier RENDER_TYPE = Veil.veilPath("light/point");

    public InstancedPointLightRenderer() {
        super(Float.BYTES * 8);
    }

    @Override
    protected MeshData createMesh() {
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION);
        LightTypeRenderer.createInvertedCube(builder);
        return builder.buildOrThrow();
    }

    @Override
    protected void setupBufferState(VertexArrayBuilder builder) {
        builder.setVertexAttribute(1, 2, 3, VertexArrayBuilder.DataType.FLOAT, false, 0);
        builder.setVertexAttribute(2, 2, 3, VertexArrayBuilder.DataType.FLOAT, false, Float.BYTES * 3);
        builder.setVertexAttribute(3, 2, 1, VertexArrayBuilder.DataType.FLOAT, false, Float.BYTES * 6);
        builder.setVertexAttribute(4, 2, 1, VertexArrayBuilder.DataType.FLOAT, false, Float.BYTES * 7);
    }

    @Override
    protected @Nullable RenderType getRenderType(List<? extends LightRenderHandle<PointLightData>> lights) {
        return VeilRenderType.get(RENDER_TYPE);
    }

    @Override
    public void uploadVoxelGridUniforms(int voxelGridTexture, Vector3fc voxelGridOrigin) {
        RenderType renderType = VeilRenderType.get(RENDER_TYPE);
        if (renderType == null) {
            return;
        }

        Identifier veilShaderId = VeilRenderType.getShards(renderType).veilShaderId();
        if (veilShaderId != null) {
            DDALightRenderer.uploadVoxelGridUniforms(veilShaderId, voxelGridTexture, voxelGridOrigin);
        }
    }
}
