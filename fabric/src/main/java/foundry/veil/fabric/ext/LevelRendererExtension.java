package foundry.veil.fabric.ext;

import foundry.veil.api.event.VeilRenderLevelStageEvent;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.jetbrains.annotations.ApiStatus;
import org.joml.Matrix4fc;

@ApiStatus.Internal
public interface LevelRendererExtension {

    void veil$renderStage(RenderType layer, Matrix4fc frustumMatrix, Matrix4fc projection);

    void veil$renderStage(VeilRenderLevelStageEvent.Stage stage, Matrix4fc frustumMatrix, Matrix4fc projection);
}
