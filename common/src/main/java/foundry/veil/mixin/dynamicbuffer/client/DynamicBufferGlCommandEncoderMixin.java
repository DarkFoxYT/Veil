package foundry.veil.mixin.dynamicbuffer.client;

import com.mojang.blaze3d.opengl.GlCommandEncoder;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import foundry.veil.impl.client.render.dynamicbuffer.DynamicBufferManager;
import org.lwjgl.system.MemoryStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.IntBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Supplier;

import static org.lwjgl.opengl.GL11C.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL20C.glDrawBuffers;
import static org.lwjgl.opengl.GL30C.GL_COLOR_ATTACHMENT0;
import static org.lwjgl.opengl.GL30C.GL_DRAW_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30C.glBindFramebuffer;

@Mixin(GlCommandEncoder.class)
public abstract class DynamicBufferGlCommandEncoderMixin {

    @Inject(method = "createRenderPass(Ljava/util/function/Supplier;Lcom/mojang/blaze3d/textures/GpuTextureView;Ljava/util/OptionalInt;Lcom/mojang/blaze3d/textures/GpuTextureView;Ljava/util/OptionalDouble;)Lcom/mojang/blaze3d/systems/RenderPass;",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/opengl/GlStateManager;_viewport(IIII)V", shift = At.Shift.BEFORE))
    private void bindDynamicBufferFbo(Supplier<String> debugGroup, Object colorTexture, OptionalInt clearColor, Object depthTexture, OptionalDouble clearDepth, CallbackInfoReturnable<?> cir) {
        DynamicBufferManager manager = VeilRenderSystem.renderer().getDynamicBufferManger();
        AdvancedFbo fbo = manager.getActiveRenderPassFbo();
        if (fbo == null) {
            return;
        }

        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, fbo.getId());
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer drawBuffers = stack.mallocInt(fbo.getColorAttachments());
            for (int i = 0; i < fbo.getColorAttachments(); i++) {
                drawBuffers.put(GL_COLOR_ATTACHMENT0 + i);
            }
            drawBuffers.flip();
            glDrawBuffers(drawBuffers);
        }
        if (clearColor.isPresent()) {
            fbo.clear(GL_COLOR_BUFFER_BIT, manager.getClearBuffers());
        }
    }
}
