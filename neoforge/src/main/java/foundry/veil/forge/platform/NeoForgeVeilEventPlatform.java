package foundry.veil.forge.platform;

import foundry.veil.api.client.render.MatrixStack;
import foundry.veil.api.client.render.VeilRenderBridge;
import foundry.veil.api.event.*;
import foundry.veil.forge.event.*;
import foundry.veil.platform.VeilEventPlatform;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.Map;

@SuppressWarnings({"Convert2MethodRef", "RedundantCast"})
@ApiStatus.Internal
public class NeoForgeVeilEventPlatform implements VeilEventPlatform {

    private static final Map<VeilRenderLevelStageEvent.Stage, Class<? extends RenderLevelStageEvent>> STAGE_MAPPING = Map.ofEntries(
            Map.entry(VeilRenderLevelStageEvent.Stage.AFTER_SKY, RenderLevelStageEvent.AfterSky.class),
            Map.entry(VeilRenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS, RenderLevelStageEvent.AfterOpaqueBlocks.class),
            Map.entry(VeilRenderLevelStageEvent.Stage.AFTER_CUTOUT_MIPPED_BLOCKS, RenderLevelStageEvent.AfterOpaqueBlocks.class),
            Map.entry(VeilRenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS, RenderLevelStageEvent.AfterOpaqueBlocks.class),
            Map.entry(VeilRenderLevelStageEvent.Stage.AFTER_ENTITIES, RenderLevelStageEvent.AfterEntities.class),
            Map.entry(VeilRenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES, RenderLevelStageEvent.AfterEntities.class),
            Map.entry(VeilRenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS, RenderLevelStageEvent.AfterTranslucentBlocks.class),
            Map.entry(VeilRenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS, RenderLevelStageEvent.AfterTripwireBlocks.class),
            Map.entry(VeilRenderLevelStageEvent.Stage.AFTER_PARTICLES, RenderLevelStageEvent.AfterParticles.class),
            Map.entry(VeilRenderLevelStageEvent.Stage.AFTER_WEATHER, RenderLevelStageEvent.AfterWeather.class),
            Map.entry(VeilRenderLevelStageEvent.Stage.AFTER_LEVEL, RenderLevelStageEvent.AfterLevel.class)
    );

    private IEventBus getModBus() {
        ModContainer container = ModLoadingContext.get().getActiveContainer();
        if (container.getEventBus() == null) {
            throw new IllegalStateException("Veil platform events must be registered from mod constructor");
        }
        return container.getEventBus();
    }

    @Override
    public void onFreeNativeResources(FreeNativeResourcesEvent event) {
        NeoForge.EVENT_BUS.<ForgeFreeNativeResourcesEvent>addListener(forgeEvent -> event.onFree());
    }

    @Override
    public void onVeilAddShaderProcessors(VeilAddShaderPreProcessorsEvent event) {
        this.getModBus().<ForgeVeilAddShaderProcessorsEvent>addListener(forgeEvent -> event.onRegisterShaderPreProcessors(forgeEvent.getResourceProvider(), forgeEvent));
    }

    @Override
    public void preVeilPostProcessing(VeilPostProcessingEvent.Pre event) {
        NeoForge.EVENT_BUS.<ForgeVeilPostProcessingEvent.Pre>addListener(forgeEvent -> event.preVeilPostProcessing(forgeEvent.getName(), forgeEvent.getPipeline(), forgeEvent.getContext()));
    }

    @Override
    public void postVeilPostProcessing(VeilPostProcessingEvent.Post event) {
        NeoForge.EVENT_BUS.<ForgeVeilPostProcessingEvent.Post>addListener(forgeEvent -> event.postVeilPostProcessing(forgeEvent.getName(), forgeEvent.getPipeline(), forgeEvent.getContext()));
    }

    // This is needed for types to line up
    @Override
    public void onVeilRegisterBlockLayers(VeilRegisterBlockLayersEvent event) {
        this.getModBus().<ForgeVeilRegisterBlockLayersEvent>addListener(forgeEvent -> event.onRegisterBlockLayers((VeilRegisterBlockLayersEvent.Registry) forgeEvent));
    }

    @Override
    public void onVeilRegisterFixedBuffers(VeilRegisterFixedBuffersEvent event) {
        this.getModBus().<ForgeVeilRegisterFixedBuffersEvent>addListener(forgeEvent -> event.onRegisterFixedBuffers((stage, renderType) -> {
            if (stage == null) {
                forgeEvent.register(null, renderType);
                return;
            }

            Class<? extends RenderLevelStageEvent> forgeStage = getForgeStage(stage);
            if (forgeStage != null) {
                forgeEvent.register(forgeStage, renderType);
            }
        }));
    }

    @Override
    public void onVeilRegisterGlobalControllers(VeilRegisterGlobalControllersEvent event) {
        this.getModBus().<ForgeVeilRegisterGlobalControllersEvent>addListener(forgeEvent -> event.onRegisterGlobalControllers((VeilRegisterGlobalControllersEvent.Registry) forgeEvent));
    }

    @Override
    public void onVeilRegisterInspectors(VeilRegisterInspectorsEvent event) {
        this.getModBus().<ForgeVeilRegisterInspectorsEvent>addListener(forgeEvent -> event.onRegisterInspectors((VeilRegisterInspectorsEvent.Registry) forgeEvent));
    }

    @Override
    public void onVeilRendererAvailable(VeilRendererAvailableEvent event) {
        this.getModBus().<ForgeVeilRendererAvailableEvent>addListener(forgeEvent -> event.onVeilRendererAvailable(forgeEvent.getRenderer()));
    }

    @Override
    public void onVeilRenderLevelStage(VeilRenderLevelStageEvent event) {
        NeoForge.EVENT_BUS.<RenderLevelStageEvent>addListener(forgeEvent -> {
            VeilRenderLevelStageEvent.Stage stage = getVeilStage(forgeEvent);
            if (stage == null) {
                return;
            }

            Minecraft minecraft = Minecraft.getInstance();
            LevelRenderer levelRenderer = forgeEvent.getLevelRenderer();
            MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
            MatrixStack poseStack = VeilRenderBridge.create(forgeEvent.getPoseStack());
            Matrix4f modelViewMatrix = forgeEvent.getModelViewMatrix();
            Matrix4f projectionMatrix = minecraft.gameRenderer.getProjectionMatrix(minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false));
            int renderTick = 0;
            DeltaTracker deltaTracker = minecraft.getDeltaTracker();
            Camera camera = minecraft.gameRenderer.getMainCamera();
            Frustum frustum = null;
            event.onRenderLevelStage(stage, levelRenderer, bufferSource, poseStack, modelViewMatrix, projectionMatrix, renderTick, deltaTracker, camera, frustum);
        });
    }

    @Override
    public void onVeilShaderCompile(VeilShaderCompileEvent event) {
        this.getModBus().<ForgeVeilShaderCompileEvent>addListener(forgeEvent -> event.onVeilCompileShaders(forgeEvent.getShaderManager(), forgeEvent.getUpdatedPrograms()));
    }

    @Override
    public void onVeilDynamicBuffersChanged(VeilDynamicBuffersChangedEvent event) {
        this.getModBus().<ForgeVeilDynamicBuffersChangedEvent>addListener(forgeEvent -> event.onVeilDynamicBuffersChanged(forgeEvent.getChange()));
    }

    public static @Nullable Class<? extends RenderLevelStageEvent> getForgeStage(VeilRenderLevelStageEvent.Stage stage) {
        return STAGE_MAPPING.get(stage);
    }

    public static @Nullable VeilRenderLevelStageEvent.Stage getVeilStage(RenderLevelStageEvent event) {
        if (event instanceof RenderLevelStageEvent.AfterSky) return VeilRenderLevelStageEvent.Stage.AFTER_SKY;
        if (event instanceof RenderLevelStageEvent.AfterOpaqueBlocks) return VeilRenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS;
        if (event instanceof RenderLevelStageEvent.AfterEntities) return VeilRenderLevelStageEvent.Stage.AFTER_ENTITIES;
        if (event instanceof RenderLevelStageEvent.AfterTranslucentBlocks) return VeilRenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS;
        if (event instanceof RenderLevelStageEvent.AfterTripwireBlocks) return VeilRenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS;
        if (event instanceof RenderLevelStageEvent.AfterParticles) return VeilRenderLevelStageEvent.Stage.AFTER_PARTICLES;
        if (event instanceof RenderLevelStageEvent.AfterWeather) return VeilRenderLevelStageEvent.Stage.AFTER_WEATHER;
        if (event instanceof RenderLevelStageEvent.AfterLevel) return VeilRenderLevelStageEvent.Stage.AFTER_LEVEL;
        return null;
    }
}
