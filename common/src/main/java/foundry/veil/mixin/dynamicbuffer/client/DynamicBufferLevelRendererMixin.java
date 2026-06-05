package foundry.veil.mixin.dynamicbuffer.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.rendertype.VeilRenderType;
import foundry.veil.impl.client.render.dynamicbuffer.DynamicBufferShard;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LevelRenderer.class, priority = 200) // Apply these changes last
public abstract class DynamicBufferLevelRendererMixin {

    @Shadow
    @Nullable
    public abstract RenderTarget getWeatherTarget();

    @Unique
    private final DynamicBufferShard veil$weatherBufferShard = new DynamicBufferShard("weather", this::getWeatherTarget);

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;prepareCullFrustum(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/client/renderer/culling/Frustum;", shift = At.Shift.BEFORE), require = 0)
    public void setupOpaque(CallbackInfo ci) {
        VeilRenderSystem.renderer().getDynamicBufferManger().setEnabled(true);
    }

    // Make sure the correct dynamic buffer state is set
    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderStateShard$OutputStateShard;setupRenderState()V"), require = 0)
    public void setupState(RenderStateShard.OutputStateShard instance, Operation<Void> original) {
        if ("weather_target".equals(VeilRenderType.getName(instance))) {
            this.veil$weatherBufferShard.setupRenderState();
        } else if (!"particles_target".equals(VeilRenderType.getName(instance))) {
            original.call(instance);
        }
    }

    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderStateShard$OutputStateShard;setupRenderState()V"), require = 0)
    public void clearState(RenderStateShard.OutputStateShard instance, Operation<Void> original) {
        if ("weather_target".equals(VeilRenderType.getName(instance))) {
            this.veil$weatherBufferShard.clearRenderState();
        } else if (!"particles_target".equals(VeilRenderType.getName(instance))) {
            original.call(instance);
        }
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/OutlineBufferSource;endOutlineBatch()V", ordinal = 0), require = 0)
    public void endOpaque(CallbackInfo ci) {
        VeilRenderSystem.renderer().getDynamicBufferManger().setEnabled(false);
    }

    // Platform-specific now
//    @Inject(method = "renderLevel", at = @At(value = "INVOKE_STRING", target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V", args = "ldc=destroyProgress"))
//    public void beginTranslucent(CallbackInfo ci) {
//        VeilRenderSystem.renderer().getDynamicBufferManger().setEnabled(true);
//    }

    @Inject(method = "renderLevel", at = @At("TAIL"))
    public void blit(CallbackInfo ci) {
        VeilRenderSystem.renderer().getDynamicBufferManger().setEnabled(false);
    }
}
