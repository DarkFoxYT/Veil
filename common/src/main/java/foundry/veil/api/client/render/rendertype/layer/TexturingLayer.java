package foundry.veil.api.client.render.rendertype.layer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import foundry.veil.Veil;
import foundry.veil.api.client.registry.RenderTypeLayerRegistry;
import foundry.veil.api.client.render.rendertype.VeilRenderTypeBuilder;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import org.joml.Matrix4f;

public record TexturingLayer(float scale) implements RenderTypeLayer {

    public static final MapCodec<TexturingLayer> CODEC = Codec.FLOAT.fieldOf("scale")
            .xmap(TexturingLayer::new, TexturingLayer::scale);
    private static final Matrix4f MATRIX = new Matrix4f();

    @Override
    public void addShard(VeilRenderTypeBuilder builder, Object... params) {
        if (this.scale != 1) {
            builder.texturingState(new RenderStateShard.TexturingStateShard(Veil.MODID + ":glint_texturing", () -> {
            }, () -> {
            }));
        }
    }

    @Override
    public RenderTypeLayerRegistry.LayerType<?> getType() {
        return RenderTypeLayerRegistry.TEXTURING.get();
    }
}
