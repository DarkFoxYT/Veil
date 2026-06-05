package foundry.veil.impl.client.render.shader.modifier;

import io.github.ocelot.glslprocessor.api.GlslSyntaxException;
import io.github.ocelot.glslprocessor.api.node.GlslTree;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public record ReplaceShaderModification(int priority, Identifier veilShader) implements ShaderModification {

    @Override
    public void inject(GlslTree tree, VeilJobParameters parameters) throws GlslSyntaxException {
        throw new UnsupportedOperationException("Replace modification replaces file");
    }
}
