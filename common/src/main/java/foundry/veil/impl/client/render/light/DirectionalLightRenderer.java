package foundry.veil.impl.client.render.light;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import foundry.veil.Veil;
import foundry.veil.api.client.color.Colorc;
import foundry.veil.api.client.render.CullFrustum;
import foundry.veil.api.client.render.light.data.DirectionalLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import foundry.veil.api.client.render.light.renderer.DDALightRenderer;
import foundry.veil.api.client.render.light.renderer.LightRenderer;
import foundry.veil.api.client.render.light.renderer.LightTypeRenderer;
import foundry.veil.api.client.render.rendertype.VeilRenderType;
import foundry.veil.api.client.render.vertex.VertexArray;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@ApiStatus.Internal
public class DirectionalLightRenderer implements LightTypeRenderer<DirectionalLightData>, DDALightRenderer<DirectionalLightData> {

    private static final Vector3f DIRECTION = new Vector3f();
    private static final Vector3f TEMPERATURE = new Vector3f();
    private static final ResourceLocation RENDER_TYPE = Veil.veilPath("light/directional");

    private final List<LightHandle> lights;
    private final VertexArray vertexArray;

    private boolean freed;

    public DirectionalLightRenderer() {
        this.lights = new ArrayList<>();

        this.vertexArray = VertexArray.create();
        this.vertexArray.upload(createMesh(), VertexArray.DrawUsage.STATIC);
        VertexArray.unbind();
    }

    private static MeshData createMesh() {
        Tesselator tesselator = RenderSystem.renderThreadTesselator();
        BufferBuilder bufferBuilder = tesselator.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION);
        LightTypeRenderer.createQuad(bufferBuilder);
        return bufferBuilder.buildOrThrow();
    }

    @Override
    public LightRenderHandle<DirectionalLightData> addLight(DirectionalLightData light) {
        LightHandle handle = new LightHandle(light);
        this.lights.add(handle);
        return handle;
    }

    @Override
    public LightRenderHandle<DirectionalLightData> steal(LightRenderHandle<DirectionalLightData> handle) {
        if (!(handle instanceof LightHandle)) {
            handle.free();
            return this.addLight(handle.getLightData());
        }
        return handle;
    }

    @Override
    public void prepareLights(LightRenderer lightRenderer, CullFrustum frustum) {
    }

    @Override
    public void renderLights(LightRenderer lightRenderer) {
        if (this.lights.isEmpty()) {
            return;
        }

        RenderType renderType = VeilRenderType.get(RENDER_TYPE);
        if (renderType == null) {
            return;
        }

        this.vertexArray.bind();
        LightRenderer.bindSceneSamplers(renderType);
        this.vertexArray.setup(renderType);
        this.render();
        this.vertexArray.clear(renderType);
        if (renderType instanceof VeilRenderType.LayeredRenderType layeredRenderType) {
            for (RenderType layer : layeredRenderType.getLayers()) {
                LightRenderer.bindSceneSamplers(layer);
                this.vertexArray.setup(layer);
                this.render();
                this.vertexArray.clear(layer);
            }
        }
    }

    private void render() {
        ShaderInstance shader = RenderSystem.getShader();
        if (shader == null) {
            return;
        }

        Uniform lightColorUniform = shader.getUniform("LightColor");
        Uniform lightDirection = shader.getUniform("LightDirection");
        Uniform specularStrength = shader.getUniform("SpecularStrength");
        Uniform shadowIntensity = shader.getUniform("ShadowIntensity");
        for (LightHandle handle : this.lights) {
            DirectionalLightData light = handle.getLightData();

            if (lightColorUniform != null) {
                Colorc lightColor = light.getColor();
                float brightness = light.getBrightness();
                light.getTemperatureColor(TEMPERATURE);
                lightColorUniform.set(lightColor.red() * brightness * TEMPERATURE.x(), lightColor.green() * brightness * TEMPERATURE.y(), lightColor.blue() * brightness * TEMPERATURE.z());
                lightColorUniform.upload();
            }

            if (lightDirection != null) {
                lightDirection.set(light.getDirection().normalize(DIRECTION));
                lightDirection.upload();
            }

            if (specularStrength != null) {
                specularStrength.set(0.08F);
                specularStrength.upload();
            }
            if (shadowIntensity != null) {
                shadowIntensity.set(light.isOcclusionEnabled() ? light.getShadowIntensity() : 0.0F);
                shadowIntensity.upload();
            }

            this.vertexArray.draw();
        }
    }

    @Override
    public Collection<? extends LightRenderHandle<DirectionalLightData>> getLights() {
        return this.lights;
    }

    @Override
    public int getVisibleLights() {
        return this.lights.size();
    }

    @Override
    public void uploadVoxelGridUniforms(int voxelGridTexture, org.joml.Vector3fc voxelGridOrigin) {
        DDALightRenderer.uploadVoxelGridUniforms(Veil.veilPath("light/directional"), voxelGridTexture, voxelGridOrigin);
    }

    @Override
    public void free() {
        this.vertexArray.close();
        this.freed = true;
    }

    private class LightHandle implements LightRenderHandle<DirectionalLightData> {

        private final DirectionalLightData data;

        private LightHandle(DirectionalLightData data) {
            this.data = data;
        }

        @Override
        public DirectionalLightData getLightData() {
            return this.data;
        }

        @Override
        public void markDirty() {
            this.data.markDirty();
        }

        @Override
        public boolean isValid() {
            return !DirectionalLightRenderer.this.freed;
        }

        @Override
        public void free() {
            DirectionalLightRenderer.this.lights.remove(this);
        }
    }
}
