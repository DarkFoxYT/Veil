package foundry.veil.mixin.debug.client;

import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.ext.VeilDebug;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.lwjgl.opengl.GL11C.GL_TEXTURE;

@Mixin(TextureManager.class)
public class DebugTextureManagerMixin {

    @Inject(method = "register(Lnet/minecraft/resources/Identifier;Lnet/minecraft/client/renderer/texture/AbstractTexture;)V", at = @At("TAIL"))
    public void applyLabel(Identifier name, AbstractTexture texture, CallbackInfo ci) {
        VeilDebug debug = VeilDebug.get();
        if (debug == VeilDebug.ENABLED) {
            VeilRenderSystem.renderThreadExecutor().execute(() -> {
                debug.objectLabel(GL_TEXTURE, VeilRenderSystem.getTextureId(texture), "Texture " + name);
            });
        }
    }
}
