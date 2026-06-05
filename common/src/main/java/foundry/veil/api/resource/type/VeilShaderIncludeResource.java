package foundry.veil.api.resource.type;

import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.shader.ShaderManager;
import foundry.veil.api.client.render.shader.compiler.CompiledShader;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import foundry.veil.api.resource.VeilResourceAction;
import foundry.veil.api.resource.VeilResourceInfo;
import foundry.veil.api.resource.VeilResourceManager;
import foundry.veil.impl.resource.action.TextEditAction;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.ApiStatus;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApiStatus.Internal
public record VeilShaderIncludeResource(VeilResourceInfo resourceInfo) implements VeilShaderResource<VeilShaderIncludeResource> {

    @Override
    public List<VeilResourceAction<VeilShaderIncludeResource>> getActions() {
        return List.of(new TextEditAction<>());
    }

    @Override
    public boolean canHotReload() {
        return true;
    }

    @Override
    public void hotReload(VeilResourceManager resourceManager) {
        Identifier id = ShaderManager.INCLUDE_LISTER.fileToId(this.resourceInfo.location());

        ShaderManager shaderManager = VeilRenderSystem.renderer().getShaderManager();
        Set<Identifier> programs = getShaders(id, shaderManager);
        for (Identifier program : programs) {
            shaderManager.scheduleRecompile(program);
        }
    }

    private static Set<Identifier> getShaders(Identifier id, ShaderManager shaderManager) {
        Set<Identifier> programs = new HashSet<>();
        for (Map.Entry<Identifier, ShaderProgram> entry : shaderManager.getShaders().entrySet()) {
            ShaderProgram program = entry.getValue();
            for (CompiledShader shader : program.getShaders().values()) {
                if (shader.includes().contains(id)) {
                    programs.add(entry.getKey());
                    break;
                }
            }
        }
        return programs;
    }
}
