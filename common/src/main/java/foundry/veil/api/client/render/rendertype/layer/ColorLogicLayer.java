package foundry.veil.api.client.render.rendertype.layer;


import com.mojang.blaze3d.platform.LogicOp;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.serialization.MapCodec;
import foundry.veil.api.client.registry.RenderTypeLayerRegistry;
import foundry.veil.api.client.render.rendertype.VeilRenderType;
import foundry.veil.api.client.render.rendertype.VeilRenderTypeBuilder;

public record ColorLogicLayer(LayerTemplateValue<LogicOp> operation) implements RenderTypeLayer {

    public static final MapCodec<ColorLogicLayer> CODEC = LayerTemplateValue.enumCodec(LogicOp.class)
            .fieldOf("operation")
            .xmap(ColorLogicLayer::new, ColorLogicLayer::operation);

    @Override
    public void addShard(VeilRenderTypeBuilder builder, Object... params) {
        builder.colorLogicState(VeilRenderType.colorLogicStateShard(this.operation.parse(params)));
    }

    @Override
    public RenderTypeLayerRegistry.LayerType<?> getType() {
        return RenderTypeLayerRegistry.COLOR_LOGIC.get();
    }
}
