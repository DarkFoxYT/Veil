package foundry.veil.mixin.pipeline.client;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import foundry.veil.api.client.render.CullFrustum;
import foundry.veil.api.client.render.VeilLevelPerspectiveRenderer;
import foundry.veil.api.client.render.VeilRenderBridge;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.compat.SodiumCompat;
import foundry.veil.ext.LevelRendererExtension;
import foundry.veil.impl.client.render.light.VoxelShadowGrid;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LevelRenderer.class, priority = 800)
public abstract class PipelineLevelRendererMixin implements LevelRendererExtension {

    @Shadow
    @Nullable
    private Frustum capturedFrustum;

    @Shadow
    @Final
    private ObjectArrayList<SectionRenderDispatcher.RenderSection> visibleSections;

    @Unique
    private CullFrustum veil$cullFrustum;

    @Inject(method = "prepareCullFrustum", at = @At("RETURN"))
    private void veil$setupLevelCamera(Matrix4f frustumMatrix, Matrix4f projectionMatrix, Vec3 pos, CallbackInfoReturnable<Frustum> cir) {
        Frustum frustum = cir.getReturnValue();
        this.veil$cullFrustum = frustum != null ? VeilRenderBridge.create(frustum) : null;
        VeilRenderSystem.renderer().getCameraMatrices().update(projectionMatrix, frustumMatrix, pos.x(), pos.y(), pos.z());
    }

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void veil$renderLevelTail(GraphicsResourceAllocator allocator, DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera, Matrix4f frustumMatrix, Matrix4f projectionMatrix, Matrix4f poseMatrix, GpuBufferSlice fogBuffer, Vector4f clearColor, boolean shouldRenderSky, CallbackInfo ci) {
        if (!VeilLevelPerspectiveRenderer.isRenderingPerspective()) {
            CullFrustum cullFrustum = this.veil$getCullFrustum();
            if (cullFrustum == null) {
                return;
            }

            ProfilerFiller profiler = Profiler.get();
            if (VeilRenderSystem.drawLights(profiler, cullFrustum)) {
                VeilRenderSystem.compositeLights(profiler);
            }
        }
    }

    @Inject(method = "blockChanged", at = @At("TAIL"))
    private void veil$onBlockChanged(BlockGetter level, BlockPos pos, BlockState oldState, BlockState newState, int flags, CallbackInfo ci) {
        VoxelShadowGrid.markBlockDirty(pos);
    }

    @Inject(method = "setLevel", at = @At("HEAD"))
    private void veil$clearLevel(ClientLevel level, CallbackInfo ci) {
        VeilRenderSystem.clearLevel();
    }

    @Override
    public CullFrustum veil$getCullFrustum() {
        return this.capturedFrustum != null ? VeilRenderBridge.create(this.capturedFrustum) : this.veil$cullFrustum;
    }

    @Override
    public void veil$drawBlockLayer(RenderType renderType, double x, double y, double z, Matrix4fc frustum, Matrix4fc projection) {
    }

    @Override
    public void veil$markChunksDirty() {
        SodiumCompat sodiumCompat = SodiumCompat.INSTANCE;

        if (sodiumCompat != null) {
            sodiumCompat.markChunksDirty();
        } else {
            for (SectionRenderDispatcher.RenderSection section : this.visibleSections) {
                section.setDirty(false);
            }
        }
    }
}
