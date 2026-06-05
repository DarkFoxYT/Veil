package foundry.veil.api.client.render.rendertype.layer;

import com.mojang.serialization.MapCodec;
import foundry.veil.api.client.registry.RenderTypeLayerRegistry;
import foundry.veil.api.client.render.VeilRenderBridge;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.framebuffer.VeilFramebuffers;
import foundry.veil.api.client.render.rendertype.VeilRenderTypeBuilder;
import net.minecraft.resources.Identifier;

public record OutputLayer(LayerTemplateValue<Identifier> framebufferId) implements RenderTypeLayer {

    public static final MapCodec<OutputLayer> CODEC = LayerTemplateValue.LOCATION_CODEC
            .fieldOf("framebuffer")
            .xmap(OutputLayer::new, OutputLayer::framebufferId);

    @Override
    public void addShard(VeilRenderTypeBuilder builder, Object... params) {
        Identifier id = this.framebufferId.parse(params);
        if (VeilFramebuffers.BLOOM.equals(id)) {
            builder.outputState(VeilRenderSystem.BLOOM_SHARD);
        } else {
            builder.outputState(VeilRenderBridge.outputState(id));
        }
    }

    @Override
    public RenderTypeLayerRegistry.LayerType<?> getType() {
        return RenderTypeLayerRegistry.OUTPUT.get();
    }
}
