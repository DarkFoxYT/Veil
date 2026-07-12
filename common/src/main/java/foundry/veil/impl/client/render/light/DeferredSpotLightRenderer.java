package foundry.veil.impl.client.render.light;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import foundry.veil.Veil;
import foundry.veil.api.client.color.Colorc;
import foundry.veil.api.client.render.CullFrustum;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.light.data.SpotLightData;
import foundry.veil.api.client.render.light.renderer.DDALightRenderer;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import foundry.veil.api.client.render.light.renderer.LightRenderer;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.ApiStatus;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@ApiStatus.Internal
public class DeferredSpotLightRenderer implements DDALightRenderer<SpotLightData> {

    private static final ResourceLocation SHADER = Veil.veilPath("light/spot");

    private final List<LightHandle> lights;
    private final List<LightHandle> visibleLights;
    private final Vector3f temperatureColor;
    private final Vector3f voxelGridOrigin;
    private int voxelGridTexture;
    private boolean freed;

    public DeferredSpotLightRenderer() {
        this.lights = new ArrayList<>();
        this.visibleLights = new ArrayList<>();
        this.temperatureColor = new Vector3f();
        this.voxelGridOrigin = new Vector3f();
    }

    @Override
    public LightRenderHandle<SpotLightData> addLight(SpotLightData light) {
        LightHandle handle = new LightHandle(light);
        this.lights.add(handle);
        return handle;
    }

    @Override
    public LightRenderHandle<SpotLightData> steal(LightRenderHandle<SpotLightData> handle) {
        if (this.lights.contains(handle)) {
            return handle;
        }

        SpotLightData lightData = handle.getLightData();
        handle.free();
        return this.addLight(lightData);
    }

    @Override
    public void prepareLights(LightRenderer lightRenderer, CullFrustum frustum) {
        this.visibleLights.clear();
        for (LightHandle light : this.lights) {
            if (light.data.isVisible(frustum)) {
                this.visibleLights.add(light);
            }
        }
        // Each spot currently draws a full-screen volumetric pass. Rank and
        // bound it more aggressively than batched point lights.
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        this.visibleLights.sort((a, b) -> Double.compare(
                a.data.getPosition().distanceSquared(camera.x, camera.y, camera.z),
                b.data.getPosition().distanceSquared(camera.x, camera.y, camera.z)));
        if (this.visibleLights.size() > 48) {
            this.visibleLights.subList(48, this.visibleLights.size()).clear();
        }
    }

    @Override
    public void renderLights(LightRenderer lightRenderer) {
        if (this.visibleLights.isEmpty()) {
            return;
        }

        ShaderProgram shader = VeilRenderSystem.setShader(SHADER);
        if (shader == null || !shader.isValid()) {
            return;
        }
        shader.bind();

        LightRenderer.bindSceneSamplers(shader);
        DDALightRenderer.uploadVoxelGridUniforms(shader, this.voxelGridTexture, this.voxelGridOrigin);
        shader.setDefaultUniforms(VertexFormat.Mode.TRIANGLE_STRIP);

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);

        try {
            shader.bindSamplers(0);
            for (LightHandle handle : this.visibleLights) {
                uploadLight(shader, handle.data);
                VeilRenderSystem.drawScreenQuad();
            }
        } finally {
            ShaderProgram.unbind();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }
    }

    private void uploadLight(ShaderProgram shader, SpotLightData light) {
        Vector3dc position = light.getPosition();
        Vector3fc direction = light.getDirection();
        Colorc color = light.getColor();
        light.getTemperatureColor(this.temperatureColor);

        float brightness = light.getBrightness();
        shader.getUniformSafe("LightPositionRange").setVector(
                (float) position.x(),
                (float) position.y(),
                (float) position.z(),
                Math.max(0.0F, light.getRange()));
        shader.getUniformSafe("LightColorFalloff").setVector(
                color.red() * brightness * this.temperatureColor.x(),
                color.green() * brightness * this.temperatureColor.y(),
                color.blue() * brightness * this.temperatureColor.z(),
                Math.max(0.001F, light.getFalloff()));
        shader.getUniformSafe("LightDirectionInnerCone").setVector(
                direction.x(),
                direction.y(),
                direction.z(),
                (float) Math.cos(light.getInnerConeAngle()));
        shader.getUniformSafe("LightOuterConeSpecularOcclusion").setVector(
                (float) Math.cos(light.getOuterConeAngle()),
                Math.max(0.0F, light.getSpecularStrength()),
                light.isOcclusionEnabled() ? Math.max(0.0F, light.getShadowIntensity()) : 0.0F,
                Math.max(0.0F, light.getGodRayStrength()));
        shader.getUniformSafe("LightParams").setVector(light.getFalloffType().ordinal(), 0.0F, 0.0F, 0.0F);
    }

    @Override
    public Collection<? extends LightRenderHandle<SpotLightData>> getLights() {
        return this.lights;
    }

    @Override
    public Collection<? extends LightRenderHandle<SpotLightData>> getPreparedLights() {
        return this.visibleLights;
    }

    @Override
    public int getVisibleLights() {
        return this.visibleLights.size();
    }

    @Override
    public void uploadVoxelGridUniforms(int voxelGridTexture, Vector3fc voxelGridOrigin) {
        this.voxelGridTexture = voxelGridTexture;
        this.voxelGridOrigin.set(voxelGridOrigin);
        DDALightRenderer.uploadVoxelGridUniforms(SHADER, voxelGridTexture, voxelGridOrigin);
    }

    @Override
    public void free() {
        this.lights.clear();
        this.visibleLights.clear();
        this.freed = true;
    }

    private class LightHandle implements LightRenderHandle<SpotLightData> {

        private final SpotLightData data;

        private LightHandle(SpotLightData data) {
            this.data = data;
        }

        @Override
        public SpotLightData getLightData() {
            return this.data;
        }

        @Override
        public void markDirty() {
            this.data.markDirty();
        }

        @Override
        public boolean isValid() {
            return !DeferredSpotLightRenderer.this.freed;
        }

        @Override
        public void free() {
            DeferredSpotLightRenderer.this.lights.remove(this);
            DeferredSpotLightRenderer.this.visibleLights.remove(this);
        }
    }
}
