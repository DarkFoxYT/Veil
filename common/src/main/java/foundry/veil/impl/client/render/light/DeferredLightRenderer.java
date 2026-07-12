package foundry.veil.impl.client.render.light;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import foundry.veil.Veil;
import foundry.veil.api.client.render.CullFrustum;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.light.DDALightData;
import foundry.veil.api.client.render.light.data.LightData;
import foundry.veil.api.client.render.light.renderer.DDALightRenderer;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import foundry.veil.api.client.render.light.renderer.LightRenderer;
import foundry.veil.api.client.render.shader.block.DynamicShaderBlock;
import foundry.veil.api.client.render.shader.block.ShaderBlock;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@ApiStatus.Internal
public abstract class DeferredLightRenderer<T extends LightData & DDALightData> implements DDALightRenderer<T> {

    private final ResourceLocation shaderId;
    private final String blockName;
    private final int lightSize;
    private final List<LightHandle> lights;
    private final List<LightHandle> visibleLights;
    private final DynamicShaderBlock<List<LightHandle>> lightBlock;

    private boolean warnedMissingSsbo;
    private boolean freed;

    protected DeferredLightRenderer(ResourceLocation shaderId, String blockName, int lightSize) {
        this.shaderId = shaderId;
        this.blockName = blockName;
        this.lightSize = lightSize;
        this.lights = new ArrayList<>();
        this.visibleLights = new ArrayList<>();
        this.lightBlock = ShaderBlock.dynamic(ShaderBlock.BufferBinding.SHADER_STORAGE, lightSize, this::storeLights);
    }

    /**
     * Stores one light as a std430-safe sequence of vec4 records.
     */
    protected abstract void store(T light, ByteBuffer buffer);

    private void storeLights(List<LightHandle> handles, ByteBuffer buffer) {
        buffer.clear();
        for (LightHandle handle : handles) {
            this.store(handle.data, buffer);
        }
    }

    @Override
    public LightRenderHandle<T> addLight(T light) {
        LightHandle handle = new LightHandle(light);
        this.lights.add(handle);
        return handle;
    }

    @Override
    public LightRenderHandle<T> steal(LightRenderHandle<T> handle) {
        if (this.lights.contains(handle)) {
            return handle;
        }

        T lightData = handle.getLightData();
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
    }

    @Override
    public void renderLights(LightRenderer lightRenderer) {
        if (this.visibleLights.isEmpty()) {
            return;
        }

        if (!VeilRenderSystem.shaderStorageBufferSupported()) {
            if (!this.warnedMissingSsbo) {
                this.warnedMissingSsbo = true;
                Veil.LOGGER.warn("Deferred light shader '{}' requires shader storage buffers", this.shaderId);
            }
            return;
        }

        ShaderProgram shader = VeilRenderSystem.setShader(this.shaderId);
        if (shader == null || !shader.isValid()) {
            return;
        }
        shader.bind();

        int requiredSize = Math.max(this.lightSize, this.visibleLights.size() * this.lightSize);
        if (this.lightBlock.getSize() < requiredSize) {
            this.lightBlock.setSize(requiredSize);
        }
        this.lightBlock.set(this.visibleLights);

        LightRenderer.bindSceneSamplers(shader);
        shader.getUniformSafe("LightCount").setInt(this.visibleLights.size());
        shader.setDefaultUniforms(VertexFormat.Mode.TRIANGLE_STRIP);

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);

        try {
            VeilRenderSystem.bind(this.blockName, this.lightBlock);
            shader.bindSamplers(0);
            VeilRenderSystem.drawScreenQuad();
        } finally {
            VeilRenderSystem.unbind(this.lightBlock);
            ShaderProgram.unbind();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }
    }

    @Override
    public Collection<? extends LightRenderHandle<T>> getLights() {
        return this.lights;
    }

    @Override
    public Collection<? extends LightRenderHandle<T>> getPreparedLights() {
        return this.visibleLights;
    }

    @Override
    public int getVisibleLights() {
        return this.visibleLights.size();
    }

    @Override
    public void uploadVoxelGridUniforms(int voxelGridTexture, org.joml.Vector3fc voxelGridOrigin) {
        DDALightRenderer.uploadVoxelGridUniforms(this.shaderId, voxelGridTexture, voxelGridOrigin);
    }

    @Override
    public void free() {
        this.lightBlock.free();
        this.lights.clear();
        this.visibleLights.clear();
        this.freed = true;
    }

    private class LightHandle implements LightRenderHandle<T> {

        private final T data;

        private LightHandle(T data) {
            this.data = data;
        }

        @Override
        public T getLightData() {
            return this.data;
        }

        @Override
        public void markDirty() {
            this.data.markDirty();
        }

        @Override
        public boolean isValid() {
            return !DeferredLightRenderer.this.freed;
        }

        @Override
        public void free() {
            DeferredLightRenderer.this.lights.remove(this);
            DeferredLightRenderer.this.visibleLights.remove(this);
        }
    }
}
