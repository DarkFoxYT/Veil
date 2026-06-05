package foundry.veil.mixin.pipeline.client;

import foundry.veil.api.client.render.VeilRenderSystem;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class PipelineMinecraftMixin {

    @Inject(method = "runTick", at = @At("HEAD"))
    public void beginFrame(CallbackInfo ci) {
        VeilRenderSystem.beginFrame();
    }

    @Inject(method = "runTick", at = @At("TAIL"))
    public void endFrame(CallbackInfo ci) {
        VeilRenderSystem.endFrame();
    }
}
