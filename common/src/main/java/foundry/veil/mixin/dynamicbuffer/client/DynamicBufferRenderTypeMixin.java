package foundry.veil.mixin.dynamicbuffer.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import foundry.veil.Veil;
import foundry.veil.api.client.render.VeilLevelPerspectiveRenderer;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import foundry.veil.impl.client.render.dynamicbuffer.DynamicBufferManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderType.class)
public abstract class DynamicBufferRenderTypeMixin {

    @Unique
    private AdvancedFbo veil$dynamicRenderPassFbo;

    @WrapOperation(method = "draw", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/rendertype/OutputTarget;getRenderTarget()Lcom/mojang/blaze3d/pipeline/RenderTarget;"))
    private RenderTarget setupDynamicRenderPass(OutputTarget outputTarget, Operation<RenderTarget> original) {
        RenderTarget renderTarget = original.call(outputTarget);
        if (!Veil.platform().hasErrors() && !VeilLevelPerspectiveRenderer.isRenderingPerspective()) {
            DynamicBufferManager manager = VeilRenderSystem.renderer().getDynamicBufferManger();
            Identifier name = this.veil$getDynamicBufferName(renderTarget);
            if (name != null) {
                this.veil$dynamicRenderPassFbo = manager.beginRenderPass(name, renderTarget);
            }
        }
        return renderTarget;
    }

    @Inject(method = "draw", at = @At("RETURN"))
    private void clearDynamicRenderPass(CallbackInfo ci) {
        if (this.veil$dynamicRenderPassFbo != null) {
            VeilRenderSystem.renderer().getDynamicBufferManger().endRenderPass(this.veil$dynamicRenderPassFbo);
            this.veil$dynamicRenderPassFbo = null;
        }
    }

    @Unique
    private @Nullable Identifier veil$getDynamicBufferName(RenderTarget renderTarget) {
        Minecraft minecraft = Minecraft.getInstance();
        LevelRenderer levelRenderer = minecraft.levelRenderer;
        if (renderTarget == minecraft.getMainRenderTarget()) {
            return DynamicBufferManager.MAIN_WRAPPER;
        }
        if (renderTarget == levelRenderer.getTranslucentTarget()) {
            return Veil.veilPath("dynamic_translucent");
        }
        if (renderTarget == levelRenderer.getParticlesTarget()) {
            return Veil.veilPath("dynamic_particles");
        }
        if (renderTarget == levelRenderer.getWeatherTarget()) {
            return Veil.veilPath("dynamic_weather");
        }
        if (renderTarget == levelRenderer.getCloudsTarget()) {
            return Veil.veilPath("dynamic_clouds");
        }
        if (renderTarget == levelRenderer.getItemEntityTarget()) {
            return Veil.veilPath("dynamic_item_entity");
        }
        return null;
    }
}
