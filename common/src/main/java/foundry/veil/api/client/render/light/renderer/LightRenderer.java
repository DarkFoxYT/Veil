package foundry.veil.api.client.render.light.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import foundry.veil.Veil;
import foundry.veil.api.client.registry.LightTypeRegistry;
import foundry.veil.api.client.render.CullFrustum;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.VeilRenderer;
import foundry.veil.api.client.render.dynamicbuffer.DynamicBufferType;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import foundry.veil.api.client.render.light.data.LightData;
import foundry.veil.api.client.render.rendertype.VeilRenderType;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import foundry.veil.api.client.render.vertex.VertexArray;
import foundry.veil.impl.client.render.dynamicbuffer.DynamicBufferManager;
import foundry.veil.impl.client.render.light.VoxelShadowGrid;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.UnmodifiableView;
import org.lwjgl.system.NativeResource;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import static org.lwjgl.opengl.GL11C.*;

/**
 * Renders all lights in a scene.
 * <br>
 * There is no way to retrieve a light, so care should be taken to keep track of what lights
 * have been added to the scene and when they should be removed.
 *
 * @author Ocelot
 */
public final class LightRenderer implements NativeResource {

    private static final ResourceLocation DYNAMIC_BUFFER_SOURCE = Veil.veilPath("deferred_lights");

    private final Map<LightTypeRegistry.LightType<?>, LightTypeRenderer<?>> renderers;
    private final Map<LightTypeRegistry.LightType<?>, LightTypeRenderer<?>> renderersView;
    private boolean dynamicBuffersEnabled;

    /**
     * Creates a new light renderer.
     */
    public LightRenderer() {
        this.renderers = new Object2ObjectArrayMap<>();
        this.renderersView = Collections.unmodifiableMap(this.renderers);
    }

    /**
     * Draws the lights to the specified framebuffer.
     *
     * @param lightFbo The framebuffer to render lights into
     * @return If any lights were actually rendered
     */
    @ApiStatus.Internal
    public boolean render(CullFrustum frustum, AdvancedFbo lightFbo) {
        for (LightTypeRenderer<?> lightRenderer : this.renderers.values()) {
            lightRenderer.prepareLights(this, frustum);
        }

        boolean hasRendered = false;
        boolean hasDdaLights = false;
        boolean hasOccludedDdaLights = false;
        for (LightTypeRenderer<?> lightRenderer : this.renderers.values()) {
            if (lightRenderer.getVisibleLights() <= 0) {
                continue;
            }

            hasRendered = true;
            if (lightRenderer instanceof DDALightRenderer<?> ddaLightRenderer) {
                hasDdaLights = true;
                hasOccludedDdaLights |= ddaLightRenderer.hasOccludedLights();
            }
        }

        if (!hasRendered) {
            return false;
        }

        lightFbo.bind(true);
        lightFbo.clear(GL_COLOR_BUFFER_BIT);
        AdvancedFbo.getMainFramebuffer().resolveToAdvancedFbo(lightFbo, GL_DEPTH_BUFFER_BIT, GL_NEAREST);

        if (hasDdaLights) {
            VoxelShadowGrid.setup(hasOccludedDdaLights);
        }

        for (LightTypeRenderer<?> lightRenderer : this.renderers.values()) {
            if (lightRenderer.getVisibleLights() <= 0) {
                continue;
            }

            if (lightRenderer instanceof DDALightRenderer<?> ddaLightRenderer) {
                ddaLightRenderer.uploadVoxelGridUniforms(VoxelShadowGrid.getTextureId(), VoxelShadowGrid.getUniformGridPos());
            }
            lightRenderer.renderLights(this);
        }

        VertexArray.unbind();
        return true;
    }

    /**
     * Adds a light to the renderer.
     *
     * @param lightData The light to add
     */
    @SuppressWarnings("unchecked")
    public <T extends LightData> LightRenderHandle<T> addLight(T lightData) {
        Objects.requireNonNull(lightData, "light");
        RenderSystem.assertOnRenderThreadOrInit();
        this.enableDeferredBuffers();
        return ((LightTypeRenderer<T>) this.renderers.computeIfAbsent(lightData.getType(), lightType -> lightType.rendererFactory().createRenderer())).addLight(lightData);
    }

    /**
     * Attempts to re-add the specified light handle to the renderer.
     *
     * @param handle The handle of the light to add
     * @return The same handle or a new one if re-added
     */
    @SuppressWarnings("unchecked")
    public <T extends LightData> LightRenderHandle<T> addLight(LightRenderHandle<T> handle) {
        Objects.requireNonNull(handle, "light");
        RenderSystem.assertOnRenderThreadOrInit();
        this.enableDeferredBuffers();
        return ((LightTypeRenderer<T>) this.renderers.computeIfAbsent(handle.getLightData().getType(), lightType -> lightType.rendererFactory().createRenderer())).steal(handle);
    }

    /**
     * Retrieves all lights of the specified type.
     *
     * @param type The type of lights to get
     * @return A list of lights for the specified type in the scene
     */
    @SuppressWarnings("unchecked")
    public <T extends LightData> Collection<? extends LightRenderHandle<T>> getLights(LightTypeRegistry.LightType<? extends T> type) {
        LightTypeRenderer<?> renderer = this.renderers.get(type);
        return renderer != null ? (Collection<? extends LightRenderHandle<T>>) renderer.getLights() : Collections.emptyList();
    }

    /**
     * @return A view of all existing light renderers
     * @since 3.3.0
     */
    @UnmodifiableView
    public Map<LightTypeRegistry.LightType<?>, LightTypeRenderer<?>> getRenderers() {
        return this.renderersView;
    }

    /**
     * @return Whether any light renderer currently owns lights
     */
    public boolean hasLights() {
        boolean hasLights = false;
        for (LightTypeRenderer<?> renderer : this.renderers.values()) {
            if (!renderer.getLights().isEmpty()) {
                hasLights = true;
                break;
            }
        }

        if (hasLights) {
            this.enableDeferredBuffers();
        } else {
            this.disableDeferredBuffers();
        }
        return hasLights;
    }

    /**
     * Rebinds light shader scene inputs every draw so resource reloads and framebuffer resizes cannot leave
     * stale scene, depth, or G-buffer texture ids attached to light programs.
     */
    @ApiStatus.Internal
    public static void bindSceneSamplers(RenderType renderType) {
        ResourceLocation shaderId = VeilRenderType.getShards(renderType).veilShaderId();
        if (shaderId == null) {
            return;
        }

        ShaderProgram shader = VeilRenderSystem.renderer().getShaderManager().getShader(shaderId);
        if (shader == null || !shader.isValid()) {
            return;
        }

        bindSceneSamplers(shader);
    }

    /**
     * Rebinds light shader scene inputs every draw so resource reloads and framebuffer resizes cannot leave
     * stale scene, depth, or G-buffer texture ids attached to light programs.
     */
    @ApiStatus.Internal
    public static void bindSceneSamplers(ShaderProgram shader) {
        VeilRenderer renderer = VeilRenderSystem.renderer();
        if (renderer == null) {
            return;
        }

        AdvancedFbo mainFramebuffer = AdvancedFbo.getMainFramebuffer();
        if (mainFramebuffer.isColorTextureAttachment(0)) {
            shader.setTexture("SceneSampler", GL_TEXTURE_2D, mainFramebuffer.getColorTextureAttachment(0).getId());
        }
        if (mainFramebuffer.isDepthTextureAttachment()) {
            shader.setTexture("DepthSampler", GL_TEXTURE_2D, mainFramebuffer.getDepthTextureAttachment().getId());
        }

        DynamicBufferManager dynamicBufferManager = renderer.getDynamicBufferManger();
        int activeBuffers = dynamicBufferManager.getActiveBuffers();
        boolean hasNormal = (activeBuffers & DynamicBufferType.NORMAL.getMask()) != 0;

        shader.setTexture("AlbedoSampler", GL_TEXTURE_2D, dynamicBufferManager.getBufferTexture(DynamicBufferType.ALBEDO));
        shader.setTexture("NormalSampler", GL_TEXTURE_2D, dynamicBufferManager.getBufferTexture(DynamicBufferType.NORMAL));
        shader.getUniformSafe("HasAlbedoSampler").setInt(0);
        shader.getUniformSafe("HasNormalSampler").setInt(hasNormal ? 1 : 0);
    }

    private void enableDeferredBuffers() {
        if (this.dynamicBuffersEnabled) {
            return;
        }
        VeilRenderer renderer = VeilRenderSystem.renderer();
        if (renderer == null) {
            return;
        }
        renderer.enableBuffers(DYNAMIC_BUFFER_SOURCE, DynamicBufferType.NORMAL);
        this.dynamicBuffersEnabled = true;
    }

    private void disableDeferredBuffers() {
        VeilRenderer renderer = VeilRenderSystem.renderer();
        if (!this.dynamicBuffersEnabled || renderer == null) {
            return;
        }
        this.dynamicBuffersEnabled = false;
        renderer.disableBuffers(DYNAMIC_BUFFER_SOURCE);
    }

    @Override
    public void free() {
        this.disableDeferredBuffers();
        this.renderers.values().forEach(LightTypeRenderer::free);
        this.renderers.clear();
    }

    @ApiStatus.Internal
    public void addDebugInfo(Consumer<String> consumer) {
        int visible = 0;
        int all = 0;
        for (LightTypeRenderer<?> renderer : this.renderers.values()) {
            visible += renderer.getVisibleLights();
            all += renderer.getLights().size();
        }
        consumer.accept("Lights: " + visible + " / " + all);
    }
}
