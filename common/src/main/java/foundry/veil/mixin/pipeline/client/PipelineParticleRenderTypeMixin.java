package foundry.veil.mixin.pipeline.client;



import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = {"net.minecraft.client.particle.ParticleRenderType$1", "net.minecraft.client.particle.ParticleRenderType$3"})
public class PipelineParticleRenderTypeMixin {

    /**
     * This corrects the blend function for particles. This also fixes particles in Fabulous graphics
     *
     * @author Ocelot
     */
    @Redirect(method = "begin", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;defaultBlendFunc()V", remap = false))
    public void changeBlendFunction() {
        GlStateManager._blendFuncSeparate(org.lwjgl.opengl.GL11C.GL_SRC_ALPHA, org.lwjgl.opengl.GL11C.GL_ONE_MINUS_SRC_ALPHA, org.lwjgl.opengl.GL11C.GL_ONE, org.lwjgl.opengl.GL11C.GL_ONE_MINUS_SRC_ALPHA);
    }
}
