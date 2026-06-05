package foundry.veil.impl.client.render.pipeline;

import foundry.veil.api.client.render.VeilRenderSystem;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class ShaderProgramShard extends RenderStateShard.ShaderStateShard {

    private final Identifier shader;

    public ShaderProgramShard(Identifier shader) {
        this.shader = shader;
    }

    @Override
    public void setupRenderState() {
        VeilRenderSystem.setShader(this.shader);
    }

    @Override
    public String toString() {
        return this.name + "[" + this.shader + "]";
    }

    public Identifier getShader() {
        return this.shader;
    }
}
