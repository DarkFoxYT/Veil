package foundry.veil.mixin.pipeline.client;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.pipeline.RenderTarget;
import foundry.veil.Veil;
import foundry.veil.api.client.render.VeilLevelPerspectiveRenderer;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.compat.IrisCompat;
import foundry.veil.impl.client.render.pipeline.VeilBloomRenderer;
import foundry.veil.impl.client.render.pipeline.VeilFirstPersonRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(GameRenderer.class)
public abstract class PipelineGameRendererMixin {

    @Shadow
    @Final
    Minecraft minecraft;

    @Shadow
    public abstract boolean isPanoramicMode();

    @Unique
    private final Vector3f veil$cameraBobOffset = new Vector3f();

    @Inject(method = "renderLevel", at = @At("HEAD"))
    public void renderLevelStart(DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!this.minecraft.options.bobView().get()) {
            VeilRenderSystem.setCameraBobOffset(this.veil$cameraBobOffset.set(0));
        }
    }

    @Inject(method = "bobView", at = @At("HEAD"))
    public void bobViewSetup(PoseStack poseStack, float partialTick, CallbackInfo ci) {
        this.veil$cameraBobOffset.set(0);
    }

    @Inject(method = "bobView", at = @At("TAIL"))
    public void bobViewClear(PoseStack poseStack, float partialTick, CallbackInfo ci) {
        VeilRenderSystem.setCameraBobOffset(this.veil$cameraBobOffset);
    }

    @ModifyArgs(method = "bobView", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"))
    public void translateBob(Args args) {
        this.veil$cameraBobOffset.sub(args.get(0), args.get(1), args.get(2));
    }

    @Inject(method = "resize", at = @At(value = "TAIL"))
    public void resizeListener(int width, int height, CallbackInfo ci) {
        // Use the main render target instead of the actual screen size, so any mod that changes resolutions doesn't break
        RenderTarget renderTarget = Minecraft.getInstance().getMainRenderTarget();
        VeilRenderSystem.resize(renderTarget.width, renderTarget.height);
    }

    @WrapWithCondition(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;doEntityOutline()V"))
    public boolean wrapRenderPost(LevelRenderer instance) {
        return !VeilLevelPerspectiveRenderer.isRenderingPerspective();
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;doEntityOutline()V", shift = At.Shift.AFTER))
    public void renderPost(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        if (!VeilLevelPerspectiveRenderer.isRenderingPerspective()) {
            VeilRenderSystem.renderPost(null);
        }
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Lighting;setupFor(Lcom/mojang/blaze3d/platform/Lighting$Entry;)V", shift = At.Shift.AFTER))
    public void updateGuiCamera(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        if (Veil.platform().hasErrors()) {
            return;
        }

        VeilRenderSystem.renderer().getGuiInfo().update();
    }

    @Inject(method = "render", at = @At("TAIL"))
    public void unbindGuiCamera(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        if (Veil.platform().hasErrors()) {
            return;
        }

        VeilRenderSystem.renderer().getGuiInfo().unbind();
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;renderItemInHand(FZLorg/joml/Matrix4f;)V", shift = At.Shift.BEFORE))
    public void bindFirstPerson(DeltaTracker deltaTracker, CallbackInfo ci) {
        // Don't try to run first person processing if the hand is hidden
        if (!this.isPanoramicMode() && (IrisCompat.INSTANCE == null || !IrisCompat.INSTANCE.areShadersLoaded())) {
            VeilFirstPersonRenderer.bind();
        }
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;renderItemInHand(FZLorg/joml/Matrix4f;)V", shift = At.Shift.AFTER))
    public void unbindFirstPerson(DeltaTracker deltaTracker, CallbackInfo ci) {
        // Don't try to run first person processing if the hand is hidden
        if (!this.isPanoramicMode() && (IrisCompat.INSTANCE == null || !IrisCompat.INSTANCE.areShadersLoaded())) {
            VeilFirstPersonRenderer.unbind();
        }
    }

    @Inject(method = "close", at = @At("TAIL"))
    public void free(CallbackInfo ci) {
        VeilFirstPersonRenderer.free();
        VeilBloomRenderer.free();
    }
}
