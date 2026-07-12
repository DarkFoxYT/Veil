package foundry.veil.fabric.mixin.compat.flashback;

import foundry.veil.api.client.render.VeilRenderSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.moulberry.flashback.exporting.ExportJob", remap = false)
public class ExportJobMixin {

    @Inject(method = "doExport", at = @At("HEAD"), require = 0, remap = false)
    public void beginFrame(CallbackInfo ci) {
        VeilRenderSystem.beginFrame();
    }

    @Inject(method = "doExport", at = @At("RETURN"), require = 0, remap = false)
    public void endFrame(CallbackInfo ci) {
        VeilRenderSystem.endFrame();
    }

}
