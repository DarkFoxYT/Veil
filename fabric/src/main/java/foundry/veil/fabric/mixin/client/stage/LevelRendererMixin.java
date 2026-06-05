package foundry.veil.fabric.mixin.client.stage;

import com.mojang.blaze3d.vertex.PoseStack;
import foundry.veil.api.client.render.CullFrustum;
import foundry.veil.api.client.render.rendertype.VeilRenderType;
import foundry.veil.api.event.VeilRenderLevelStageEvent;
import foundry.veil.fabric.FabricRenderTypeStageHandler;
import foundry.veil.fabric.ext.LevelRendererExtension;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.util.profiling.Profiler;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin implements LevelRendererExtension {

    @Shadow
    private int ticks;

    @Shadow
    @Nullable
    private Frustum capturedFrustum;

    @Shadow
    @Final
    private RenderBuffers renderBuffers;

    @Override
    public void veil$renderStage(RenderType layer, Matrix4fc frustumMatrix, Matrix4fc projection) {
        VeilRenderLevelStageEvent.Stage stage;
        String name = VeilRenderType.getName(layer);
        if ("solid".equals(name)) {
            stage = VeilRenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS;
        } else if ("cutout_mipped".equals(name)) {
            stage = VeilRenderLevelStageEvent.Stage.AFTER_CUTOUT_MIPPED_BLOCKS;
        } else if ("cutout".equals(name)) {
            stage = VeilRenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS;
        } else if ("translucent".equals(name)) {
            stage = VeilRenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS;
        } else if ("tripwire".equals(name)) {
            stage = VeilRenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS;
        } else {
            stage = null;
        }

        if (stage != null) {
            this.veil$renderStage(stage, frustumMatrix, projection);
        }
    }

    @Override
    public void veil$renderStage(VeilRenderLevelStageEvent.Stage stage, Matrix4fc frustumMatrix, Matrix4fc projection) {
        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = minecraft.gameRenderer.getMainCamera();
        Frustum frustum = this.capturedFrustum;
        if (frustum == null) {
            CullFrustum cullFrustum = ((foundry.veil.ext.LevelRendererExtension) this).veil$getCullFrustum();
            frustum = cullFrustum != null ? cullFrustum.toFrustum() : null;
        }

        FabricRenderTypeStageHandler.renderStage((foundry.veil.ext.LevelRendererBlockLayerExtension) this, Profiler.get(), stage, (LevelRenderer) (Object) this, this.renderBuffers.bufferSource(), new PoseStack(), frustumMatrix, projection, this.ticks, minecraft.getDeltaTracker(), camera, frustum);
    }
}
