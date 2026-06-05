package foundry.veil.api.client.render.rendertype;

import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.rendertype.RenderType;
import com.mojang.blaze3d.vertex.VertexFormat;

/**
 * Extended render type builder that adds support for custom layers.
 */
public interface VeilRenderTypeBuilder {

    VeilRenderTypeBuilder textureState(RenderStateShard.EmptyTextureStateShard state);

    VeilRenderTypeBuilder shaderState(RenderStateShard.ShaderStateShard state);

    VeilRenderTypeBuilder transparencyState(RenderStateShard.TransparencyStateShard state);

    VeilRenderTypeBuilder depthTestState(RenderStateShard.DepthTestStateShard state);

    VeilRenderTypeBuilder cullState(RenderStateShard.CullStateShard state);

    VeilRenderTypeBuilder lightmapState(RenderStateShard.LightmapStateShard state);

    VeilRenderTypeBuilder overlayState(RenderStateShard.OverlayStateShard state);

    VeilRenderTypeBuilder layeringState(RenderStateShard.LayeringStateShard state);

    VeilRenderTypeBuilder outputState(RenderStateShard.OutputStateShard state);

    VeilRenderTypeBuilder texturingState(RenderStateShard.TexturingStateShard state);

    VeilRenderTypeBuilder writeMaskState(RenderStateShard.WriteMaskStateShard state);

    VeilRenderTypeBuilder lineState(RenderStateShard.LineStateShard state);

    VeilRenderTypeBuilder colorLogicState(RenderStateShard.ColorLogicStateShard state);

    VeilRenderTypeBuilder addLayer(RenderStateShard shard);

    RenderType create(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize, boolean affectsCrumbling, boolean sortOnUpload, boolean affectsOutline);
}
