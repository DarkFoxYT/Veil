package foundry.veil.impl.client.render.pipeline;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.LogicOp;
import com.mojang.blaze3d.vertex.VertexFormat;
import foundry.veil.Veil;
import foundry.veil.api.client.render.rendertype.VeilRenderTypeBuilder;
import foundry.veil.api.client.render.rendertype.VeilRenderType;
import foundry.veil.api.client.render.rendertype.VeilRenderTypeAccessor;
import foundry.veil.mixin.rendertype.accessor.RenderTypeAccessor;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import net.minecraft.resources.Identifier;

import java.util.Locale;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class VeilRenderTypeBuilderImpl implements VeilRenderTypeBuilder {

    private static final AtomicInteger NEXT_ID = new AtomicInteger();

    private RenderStateShard.EmptyTextureStateShard textureState = RenderStateShard.NO_TEXTURE;
    private RenderStateShard.ShaderStateShard shaderState = new RenderStateShard.ShaderStateShard();
    private RenderStateShard.TransparencyStateShard transparencyState = RenderStateShard.NO_TRANSPARENCY;
    private RenderStateShard.DepthTestStateShard depthTestState = RenderStateShard.LEQUAL_DEPTH_TEST;
    private RenderStateShard.CullStateShard cullState = RenderStateShard.CULL;
    private RenderStateShard.LightmapStateShard lightmapState = RenderStateShard.NO_LIGHTMAP;
    private RenderStateShard.OverlayStateShard overlayState = RenderStateShard.NO_OVERLAY;
    private RenderStateShard.LayeringStateShard layeringState = RenderStateShard.NO_LAYERING;
    private RenderStateShard.OutputStateShard outputState = RenderStateShard.MAIN_TARGET;
    private RenderStateShard.TexturingStateShard texturingState = RenderStateShard.DEFAULT_TEXTURING;
    private RenderStateShard.WriteMaskStateShard writeMaskState = RenderStateShard.COLOR_DEPTH_WRITE;
    private RenderStateShard.LineStateShard lineState = RenderStateShard.DEFAULT_LINE;
    private RenderStateShard.ColorLogicStateShard colorLogicState = RenderStateShard.NO_COLOR_LOGIC;

    @Override
    public VeilRenderTypeBuilder textureState(RenderStateShard.EmptyTextureStateShard state) {
        this.textureState = state;
        return this;
    }

    @Override
    public VeilRenderTypeBuilder shaderState(RenderStateShard.ShaderStateShard state) {
        this.shaderState = state;
        return this;
    }

    @Override
    public VeilRenderTypeBuilder transparencyState(RenderStateShard.TransparencyStateShard state) {
        this.transparencyState = state;
        return this;
    }

    @Override
    public VeilRenderTypeBuilder depthTestState(RenderStateShard.DepthTestStateShard state) {
        this.depthTestState = state;
        return this;
    }

    @Override
    public VeilRenderTypeBuilder cullState(RenderStateShard.CullStateShard state) {
        this.cullState = state;
        return this;
    }

    @Override
    public VeilRenderTypeBuilder lightmapState(RenderStateShard.LightmapStateShard state) {
        this.lightmapState = state;
        return this;
    }

    @Override
    public VeilRenderTypeBuilder overlayState(RenderStateShard.OverlayStateShard state) {
        this.overlayState = state;
        return this;
    }

    @Override
    public VeilRenderTypeBuilder layeringState(RenderStateShard.LayeringStateShard state) {
        this.layeringState = state;
        return this;
    }

    @Override
    public VeilRenderTypeBuilder outputState(RenderStateShard.OutputStateShard state) {
        this.outputState = state;
        return this;
    }

    @Override
    public VeilRenderTypeBuilder texturingState(RenderStateShard.TexturingStateShard state) {
        this.texturingState = state;
        return this;
    }

    @Override
    public VeilRenderTypeBuilder writeMaskState(RenderStateShard.WriteMaskStateShard state) {
        this.writeMaskState = state;
        return this;
    }

    @Override
    public VeilRenderTypeBuilder lineState(RenderStateShard.LineStateShard state) {
        this.lineState = state;
        return this;
    }

    @Override
    public VeilRenderTypeBuilder colorLogicState(RenderStateShard.ColorLogicStateShard state) {
        this.colorLogicState = state;
        return this;
    }

    @Override
    public VeilRenderTypeBuilder addLayer(RenderStateShard shard) {
        return this;
    }

    @Override
    public RenderType create(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize, boolean affectsCrumbling, boolean sortOnUpload, boolean affectsOutline) {
        Identifier location = Identifier.fromNamespaceAndPath(Veil.MODID, "pipeline/" + sanitize(name) + "_" + NEXT_ID.getAndIncrement());
        RenderPipeline.Builder pipeline = RenderPipeline.builder()
                .withLocation(location)
                .withVertexShader("core/position_color")
                .withFragmentShader("core/position_color")
                .withVertexFormat(format, mode)
                .withDepthTestFunction(this.depthTestState.function())
                .withCull(this.cullState.cull())
                .withColorWrite(this.writeMaskState.writeColor())
                .withDepthWrite(this.writeMaskState.writeDepth());

        if (this.transparencyState.blendFunction() != null) {
            pipeline.withBlend(this.transparencyState.blendFunction());
        }
        if (this.colorLogicState.logicOp() != LogicOp.NONE) {
            pipeline.withColorLogic(this.colorLogicState.logicOp());
        }
        if (this.textureState instanceof RenderStateShard.TextureStateShard || this.textureState instanceof RenderStateShard.MultiTextureStateShard) {
            pipeline.withSampler("Sampler0");
        }
        if (this.overlayState.enabled()) {
            pipeline.withSampler("Sampler1");
        }
        if (this.lightmapState.enabled()) {
            pipeline.withSampler("Sampler2");
        }

        RenderSetup.RenderSetupBuilder setup = RenderSetup.builder(pipeline.build())
                .bufferSize(bufferSize)
                .setOutline(affectsOutline ? RenderSetup.OutlineProperty.AFFECTS_OUTLINE : RenderSetup.OutlineProperty.NONE);
        if (affectsCrumbling) {
            setup.affectsCrumbling();
        }
        if (sortOnUpload) {
            setup.sortOnUpload();
        }
        if (this.lightmapState.enabled()) {
            setup.useLightmap();
        }
        if (this.overlayState.enabled()) {
            setup.useOverlay();
        }
        if (this.textureState instanceof RenderStateShard.TextureStateShard texture) {
            setup.withTexture("Sampler0", texture.texture());
        } else if (this.textureState instanceof RenderStateShard.MultiTextureStateShard multiTexture && !multiTexture.textures().isEmpty()) {
            setup.withTexture("Sampler0", multiTexture.textures().getFirst().texture());
        }
        if ("view_offset_z_layering".equals(this.layeringState.name())) {
            setup.setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING);
        }
        if (!"default_texturing".equals(this.texturingState.name())) {
            setup.setTextureTransform(TextureTransform.GLINT_TEXTURING);
        }
        RenderType renderType = RenderTypeAccessor.veil$create(name, setup.createRenderSetup());
        VeilRenderType.register(renderType, new Accessor(
                this.textureState,
                this.shaderState,
                this.transparencyState,
                this.depthTestState,
                this.cullState,
                this.lightmapState,
                this.overlayState,
                this.layeringState,
                this.outputState,
                this.texturingState,
                this.writeMaskState,
                this.lineState,
                this.colorLogicState,
                affectsOutline ? RenderSetup.OutlineProperty.AFFECTS_OUTLINE : RenderSetup.OutlineProperty.NONE
        ));
        return renderType;
    }

    private static String sanitize(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_/.-]", "_");
    }

    private record Accessor(
            RenderStateShard.EmptyTextureStateShard textureState,
            RenderStateShard.ShaderStateShard shaderState,
            RenderStateShard.TransparencyStateShard transparencyState,
            RenderStateShard.DepthTestStateShard depthTestState,
            RenderStateShard.CullStateShard cullState,
            RenderStateShard.LightmapStateShard lightmapState,
            RenderStateShard.OverlayStateShard overlayState,
            RenderStateShard.LayeringStateShard layeringState,
            RenderStateShard.OutputStateShard outputState,
            RenderStateShard.TexturingStateShard texturingState,
            RenderStateShard.WriteMaskStateShard writeMaskState,
            RenderStateShard.LineStateShard lineState,
            RenderStateShard.ColorLogicStateShard colorLogicState,
            RenderSetup.OutlineProperty outlineProperty
    ) implements VeilRenderTypeAccessor {

        @Override
        public List<RenderStateShard> states() {
            return List.of(
                    this.textureState,
                    this.shaderState,
                    this.transparencyState,
                    this.depthTestState,
                    this.cullState,
                    this.lightmapState,
                    this.overlayState,
                    this.layeringState,
                    this.outputState,
                    this.texturingState,
                    this.writeMaskState,
                    this.lineState,
                    this.colorLogicState
            );
        }
    }
}
