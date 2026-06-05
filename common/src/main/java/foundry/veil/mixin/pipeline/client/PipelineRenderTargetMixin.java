package foundry.veil.mixin.pipeline.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import foundry.veil.ext.RenderTargetExtension;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(RenderTarget.class)
public abstract class PipelineRenderTargetMixin implements RenderTargetExtension {

    @Unique
    private AdvancedFbo veil$wrapper;

    @Override
    public void veil$setWrapper(@Nullable AdvancedFbo fbo) {
        this.veil$wrapper = fbo;
    }

    @Override
    @Nullable
    public AdvancedFbo veil$getWrapper() {
        return this.veil$wrapper;
    }

    @Override
    public int veil$getTexture(int buffer) {
        if (this.veil$wrapper != null && this.veil$wrapper.isColorTextureAttachment(buffer)) {
            return this.veil$wrapper.getColorTextureAttachment(buffer).getId();
        }
        return buffer == 0 ? VeilRenderSystem.getColorTextureId((RenderTarget) (Object) this) : 0;
    }
}
