package foundry.veil.fabric.mixin.client.stage.sodium;

import com.mojang.blaze3d.textures.GpuSampler;
import foundry.veil.api.event.VeilRenderLevelStageEvent;
import foundry.veil.fabric.ext.LevelRendererExtension;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SodiumWorldRenderer.class)
public class SodiumWorldRendererMixin {

    @Inject(method = "drawChunkLayer", at = @At("TAIL"), remap = false)
    public void postRenderChunkLayer(ChunkSectionLayerGroup renderLayer, ChunkRenderMatrices matrices, double x, double y, double z, GpuSampler sampler, CallbackInfo ci) {
        VeilRenderLevelStageEvent.Stage stage = switch (renderLayer) {
            case OPAQUE -> VeilRenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS;
            case TRANSLUCENT -> VeilRenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS;
            case TRIPWIRE -> VeilRenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS;
        };
        ((LevelRendererExtension) Minecraft.getInstance().levelRenderer).veil$renderStage(stage, matrices.modelView(), matrices.projection());
    }
}
