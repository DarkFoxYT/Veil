package foundry.veil.api.client.render.rendertype;

import com.mojang.blaze3d.platform.LogicOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import foundry.veil.Veil;
import foundry.veil.api.client.render.VeilRenderBridge;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.vertex.VeilVertexFormat;
import foundry.veil.impl.client.render.pipeline.CullFaceShard;
import foundry.veil.impl.client.render.pipeline.VeilRenderTypeBuilderImpl;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.lwjgl.opengl.GL11C.*;

/**
 * Custom Veil-implemented render types.
 */
public final class VeilRenderType {

    public static final RenderStateShard.DepthTestStateShard NEVER_DEPTH_TEST = new RenderStateShard.DepthTestStateShard("never", GL_NEVER);
    public static final RenderStateShard.DepthTestStateShard LESS_DEPTH_TEST = new RenderStateShard.DepthTestStateShard("<", GL_LESS);
    public static final RenderStateShard.DepthTestStateShard NOTEQUAL_DEPTH_TEST = new RenderStateShard.DepthTestStateShard("!=", GL_NOTEQUAL);
    public static final RenderStateShard.DepthTestStateShard GEQUAL_DEPTH_TEST = new RenderStateShard.DepthTestStateShard(">=", GL_GEQUAL);

    public static final RenderStateShard CULL_FRONT = new CullFaceShard(GL_FRONT);
    public static final RenderStateShard CULL_BACK = new CullFaceShard(GL_BACK);
    public static final RenderStateShard CULL_FRONT_AND_BACK = new CullFaceShard(GL_FRONT_AND_BACK);
    public static final RenderStateShard.WriteMaskStateShard NO_WRITE = new RenderStateShard.WriteMaskStateShard(false, false);

    private static final EnumMap<LogicOp, RenderStateShard.ColorLogicStateShard> COLOR_LOGIC_SHARDS = new EnumMap<>(LogicOp.class);
    private static final Map<RenderType, VeilRenderTypeAccessor> ACCESSORS = Collections.synchronizedMap(new WeakHashMap<>());

    static {
        for (LogicOp logicOp : LogicOp.values()) {
            COLOR_LOGIC_SHARDS.put(logicOp, new RenderStateShard.ColorLogicStateShard(logicOp.name().toLowerCase(Locale.ROOT), logicOp));
        }
    }

    private static final RenderStateShard.ShaderStateShard PARTICLE = VeilRenderBridge.shaderState(Veil.veilPath("quasar/particle"));
    private static final RenderStateShard.ShaderStateShard PARTICLE_ADDITIVE = VeilRenderBridge.shaderState(Veil.veilPath("quasar/particle_additive"));

    private static final BiFunction<Identifier, Boolean, RenderType> QUASAR_PARTICLE = net.minecraft.util.Util.memoize((texture, additive) -> {
        VeilRenderTypeBuilder builder = new VeilRenderTypeBuilderImpl()
                .shaderState(additive ? PARTICLE_ADDITIVE : PARTICLE)
                .textureState(new RenderStateShard.TextureStateShard(texture, false, false))
                .transparencyState(additive ? RenderStateShard.ADDITIVE_TRANSPARENCY : RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                .lightmapState(RenderStateShard.LIGHTMAP)
                .writeMaskState(RenderStateShard.COLOR_WRITE);
        return builder.create(Veil.MODID + ":quasar_particle", VeilVertexFormat.QUASAR_PARTICLE, VertexFormat.Mode.QUADS, RenderType.SMALL_BUFFER_SIZE, false, !additive, false);
    });
    private static final Function<Identifier, RenderType> QUASAR_TRAIL = net.minecraft.util.Util.memoize((texture) -> {
        VeilRenderTypeBuilder builder = new VeilRenderTypeBuilderImpl()
                .textureState(new RenderStateShard.TextureStateShard(texture, false, false))
                .transparencyState(RenderStateShard.ADDITIVE_TRANSPARENCY)
                .writeMaskState(RenderStateShard.COLOR_WRITE)
                .cullState(RenderStateShard.NO_CULL);
        return builder.create(Veil.MODID + ":quasar_trail", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.TRIANGLE_STRIP, RenderType.TRANSIENT_BUFFER_SIZE, false, false, false);
    });

    private VeilRenderType() {
    }

    public static RenderType quasarParticle(Identifier texture, boolean additive) {
        return QUASAR_PARTICLE.apply(texture, additive);
    }

    public static RenderType quasarTrail(Identifier texture) {
        return QUASAR_TRAIL.apply(texture);
    }

    public static RenderStateShard.TransparencyStateShard noTransparencyShard() {
        return RenderStateShard.NO_TRANSPARENCY;
    }

    public static RenderStateShard.TransparencyStateShard additiveTransparencyShard() {
        return RenderStateShard.ADDITIVE_TRANSPARENCY;
    }

    public static RenderStateShard.TransparencyStateShard lightningTransparencyShard() {
        return RenderStateShard.LIGHTNING_TRANSPARENCY;
    }

    public static RenderStateShard.TransparencyStateShard glintTransparencyShard() {
        return RenderStateShard.GLINT_TRANSPARENCY;
    }

    public static RenderStateShard.TransparencyStateShard crumblingTransparencyShard() {
        return RenderStateShard.CRUMBLING_TRANSPARENCY;
    }

    public static RenderStateShard.TransparencyStateShard translucentTransparencyShard() {
        return RenderStateShard.TRANSLUCENT_TRANSPARENCY;
    }

    public static RenderStateShard.DepthTestStateShard noDepthTestShard() {
        return RenderStateShard.NO_DEPTH_TEST;
    }

    public static RenderStateShard.DepthTestStateShard equalDepthTestShard() {
        return RenderStateShard.EQUAL_DEPTH_TEST;
    }

    public static RenderStateShard.DepthTestStateShard lequalDepthTestShard() {
        return RenderStateShard.LEQUAL_DEPTH_TEST;
    }

    public static RenderStateShard.DepthTestStateShard greaterDepthTestShard() {
        return RenderStateShard.GREATER_DEPTH_TEST;
    }

    public static RenderStateShard.CullStateShard cullShard() {
        return RenderStateShard.CULL;
    }

    public static RenderStateShard.CullStateShard noCullShard() {
        return RenderStateShard.NO_CULL;
    }

    public static RenderStateShard.LightmapStateShard lightmap() {
        return RenderStateShard.LIGHTMAP;
    }

    public static RenderStateShard.LightmapStateShard noLightmap() {
        return RenderStateShard.NO_LIGHTMAP;
    }

    public static RenderStateShard.OverlayStateShard overlay() {
        return RenderStateShard.OVERLAY;
    }

    public static RenderStateShard.OverlayStateShard noOverlay() {
        return RenderStateShard.NO_OVERLAY;
    }

    public static RenderStateShard.LayeringStateShard noLayering() {
        return RenderStateShard.NO_LAYERING;
    }

    public static RenderStateShard.LayeringStateShard polygonOffsetLayering() {
        return RenderStateShard.POLYGON_OFFSET_LAYERING;
    }

    public static RenderStateShard.LayeringStateShard viewOffsetLayering() {
        return RenderStateShard.VIEW_OFFSET_Z_LAYERING;
    }

    public static RenderStateShard.WriteMaskStateShard colorDepthWriteShard() {
        return RenderStateShard.COLOR_DEPTH_WRITE;
    }

    public static RenderStateShard.WriteMaskStateShard colorWriteShard() {
        return RenderStateShard.COLOR_WRITE;
    }

    public static RenderStateShard.WriteMaskStateShard depthWriteShard() {
        return RenderStateShard.DEPTH_WRITE;
    }

    public static RenderStateShard.ColorLogicStateShard colorLogicStateShard(LogicOp op) {
        return COLOR_LOGIC_SHARDS.get(op);
    }

    @Contract(pure = true)
    public static @Nullable RenderType get(Identifier id, Object... params) {
        return VeilRenderSystem.renderer().getDynamicRenderTypeManager().get(id, params);
    }

    @Contract(pure = true)
    public static RenderType getWrapper(Identifier id) {
        RenderType renderType = get(id);
        if (renderType != null) {
            return renderType;
        }
        return new VeilRenderTypeBuilderImpl().create(id.toString(), DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, RenderType.TRANSIENT_BUFFER_SIZE, false, false, false);
    }

    @Contract(pure = true)
    public static String getName(RenderStateShard shard) {
        return shard.name();
    }

    @Contract(pure = true)
    public static String getName(RenderType renderType) {
        String value = renderType.toString();
        int start = value.indexOf('[');
        int end = value.indexOf(':', start + 1);
        return start >= 0 && end > start ? value.substring(start + 1, end) : value;
    }

    @Contract(pure = true)
    public static VeilRenderTypeAccessor getShards(RenderType renderType) {
        return ACCESSORS.getOrDefault(renderType, EmptyAccessor.INSTANCE);
    }

    public static void register(RenderType renderType, VeilRenderTypeAccessor accessor) {
        ACCESSORS.put(renderType, accessor);
    }

    @Contract(pure = true)
    public static RenderType layered(RenderType... layers) {
        if (layers.length == 0) {
            throw new IllegalArgumentException("At least 1 render type must be specified");
        }
        return layers[0];
    }

    private enum EmptyAccessor implements VeilRenderTypeAccessor {
        INSTANCE;

        @Override
        public RenderStateShard.EmptyTextureStateShard textureState() {
            return RenderStateShard.NO_TEXTURE;
        }

        @Override
        public RenderStateShard.ShaderStateShard shaderState() {
            return new RenderStateShard.ShaderStateShard();
        }

        @Override
        public RenderStateShard.TransparencyStateShard transparencyState() {
            return RenderStateShard.NO_TRANSPARENCY;
        }

        @Override
        public RenderStateShard.DepthTestStateShard depthTestState() {
            return RenderStateShard.LEQUAL_DEPTH_TEST;
        }

        @Override
        public RenderStateShard.CullStateShard cullState() {
            return RenderStateShard.CULL;
        }

        @Override
        public RenderStateShard.LightmapStateShard lightmapState() {
            return RenderStateShard.NO_LIGHTMAP;
        }

        @Override
        public RenderStateShard.OverlayStateShard overlayState() {
            return RenderStateShard.NO_OVERLAY;
        }

        @Override
        public RenderStateShard.LayeringStateShard layeringState() {
            return RenderStateShard.NO_LAYERING;
        }

        @Override
        public RenderStateShard.OutputStateShard outputState() {
            return RenderStateShard.MAIN_TARGET;
        }

        @Override
        public RenderStateShard.TexturingStateShard texturingState() {
            return RenderStateShard.DEFAULT_TEXTURING;
        }

        @Override
        public RenderStateShard.WriteMaskStateShard writeMaskState() {
            return RenderStateShard.COLOR_DEPTH_WRITE;
        }

        @Override
        public RenderStateShard.LineStateShard lineState() {
            return RenderStateShard.DEFAULT_LINE;
        }

        @Override
        public RenderStateShard.ColorLogicStateShard colorLogicState() {
            return RenderStateShard.NO_COLOR_LOGIC;
        }

        @Override
        public RenderSetup.OutlineProperty outlineProperty() {
            return RenderSetup.OutlineProperty.NONE;
        }

        @Override
        public List<RenderStateShard> states() {
            return Collections.emptyList();
        }
    }
}
