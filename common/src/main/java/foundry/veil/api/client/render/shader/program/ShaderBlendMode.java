package foundry.veil.api.client.render.shader.program;



import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import foundry.veil.api.util.EnumCodec;

import static org.lwjgl.opengl.GL14C.*;
import static org.lwjgl.opengl.GL20C.glBlendEquationSeparate;

/**
 * Specifies the blend mode for a {@link ShaderProgram}.
 *
 * @param colorEquation  The color component equation. The default is {@link BlendEquation#ADD}
 * @param alphaEquation  The alpha component equation. The default is {@link BlendEquation#ADD}
 * @param srcColorFactor The source color factor. The default is {@link SourceFactor#ONE}
 * @param dstColorFactor The destination color factor. The default is {@link DestFactor#ONE}
 * @param srcAlphaFactor The source alpha factor. The default is {@link SourceFactor#ONE}
 * @param dstAlphaFactor The destination alpha factor. The default is {@link DestFactor#ONE}
 * @author Ocelot
 */
public record ShaderBlendMode(
        BlendEquation colorEquation,
        BlendEquation alphaEquation,
        SourceFactor srcColorFactor,
        DestFactor dstColorFactor,
        SourceFactor srcAlphaFactor,
        DestFactor dstAlphaFactor
) {

    public static final Codec<SourceFactor> SOURCE_FACTOR_CODEC = EnumCodec
            .<SourceFactor>builder("Source Factor")
            .values(SourceFactor.class)
            .build();
    public static final Codec<DestFactor> DESTINATION_FACTOR_CODEC = EnumCodec
            .<DestFactor>builder("Destination Factor")
            .values(DestFactor.class)
            .build();

    public static final Codec<ShaderBlendMode> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlendEquation.CODEC.optionalFieldOf("func", BlendEquation.ADD).forGetter(ShaderBlendMode::colorEquation),
            BlendEquation.CODEC.optionalFieldOf("alphafunc", BlendEquation.ADD).forGetter(ShaderBlendMode::alphaEquation),
            SOURCE_FACTOR_CODEC.optionalFieldOf("srcrgb", SourceFactor.ONE).forGetter(ShaderBlendMode::srcColorFactor),
            DESTINATION_FACTOR_CODEC.optionalFieldOf("dstrgb", DestFactor.ZERO).forGetter(ShaderBlendMode::dstColorFactor),
            SOURCE_FACTOR_CODEC.optionalFieldOf("srcalpha", SourceFactor.ONE).forGetter(ShaderBlendMode::srcAlphaFactor),
            DESTINATION_FACTOR_CODEC.optionalFieldOf("dstalpha", DestFactor.ZERO).forGetter(ShaderBlendMode::dstAlphaFactor)
    ).apply(instance, ShaderBlendMode::new));

    /**
     * Applies this blend mode.
     */
    public void apply() {
        if (this.colorEquation != BlendEquation.ADD || this.alphaEquation != BlendEquation.ADD) {
            glBlendEquationSeparate(this.colorEquation.getGlType(), this.alphaEquation.getGlType());
        }
        GlStateManager._blendFuncSeparate(gl(this.srcColorFactor), gl(this.dstColorFactor), gl(this.srcAlphaFactor), gl(this.dstAlphaFactor));
    }

    /**
     * @return Whether the blend equations have been changed from {@link BlendEquation#ADD}
     */
    public boolean hasEquation() {
        return this.colorEquation != BlendEquation.ADD || this.alphaEquation != BlendEquation.ADD;
    }

    private static int gl(SourceFactor factor) {
        return switch (factor) {
            case CONSTANT_ALPHA -> GL_CONSTANT_ALPHA;
            case CONSTANT_COLOR -> GL_CONSTANT_COLOR;
            case DST_ALPHA -> GL_DST_ALPHA;
            case DST_COLOR -> GL_DST_COLOR;
            case ONE -> GL_ONE;
            case ONE_MINUS_CONSTANT_ALPHA -> GL_ONE_MINUS_CONSTANT_ALPHA;
            case ONE_MINUS_CONSTANT_COLOR -> GL_ONE_MINUS_CONSTANT_COLOR;
            case ONE_MINUS_DST_ALPHA -> GL_ONE_MINUS_DST_ALPHA;
            case ONE_MINUS_DST_COLOR -> GL_ONE_MINUS_DST_COLOR;
            case ONE_MINUS_SRC_ALPHA -> GL_ONE_MINUS_SRC_ALPHA;
            case ONE_MINUS_SRC_COLOR -> GL_ONE_MINUS_SRC_COLOR;
            case SRC_ALPHA -> GL_SRC_ALPHA;
            case SRC_ALPHA_SATURATE -> GL_SRC_ALPHA_SATURATE;
            case SRC_COLOR -> GL_SRC_COLOR;
            case ZERO -> GL_ZERO;
        };
    }

    private static int gl(DestFactor factor) {
        return switch (factor) {
            case CONSTANT_ALPHA -> GL_CONSTANT_ALPHA;
            case CONSTANT_COLOR -> GL_CONSTANT_COLOR;
            case DST_ALPHA -> GL_DST_ALPHA;
            case DST_COLOR -> GL_DST_COLOR;
            case ONE -> GL_ONE;
            case ONE_MINUS_CONSTANT_ALPHA -> GL_ONE_MINUS_CONSTANT_ALPHA;
            case ONE_MINUS_CONSTANT_COLOR -> GL_ONE_MINUS_CONSTANT_COLOR;
            case ONE_MINUS_DST_ALPHA -> GL_ONE_MINUS_DST_ALPHA;
            case ONE_MINUS_DST_COLOR -> GL_ONE_MINUS_DST_COLOR;
            case ONE_MINUS_SRC_ALPHA -> GL_ONE_MINUS_SRC_ALPHA;
            case ONE_MINUS_SRC_COLOR -> GL_ONE_MINUS_SRC_COLOR;
            case SRC_ALPHA -> GL_SRC_ALPHA;
            case SRC_COLOR -> GL_SRC_COLOR;
            case ZERO -> GL_ZERO;
        };
    }

    /**
     * Possible OpenGL blend equations.
     *
     * @author Ocelot
     */
    public enum BlendEquation {
        ADD(GL_FUNC_ADD),
        SUBTRACT(GL_FUNC_SUBTRACT),
        REVERSE_SUBTRACT(GL_FUNC_REVERSE_SUBTRACT),
        MIN(GL_MIN),
        MAX(GL_MAX);

        public static final Codec<BlendEquation> CODEC = EnumCodec
                .<BlendEquation>builder("Blend Equation")
                .values(BlendEquation.class)
                .build();

        private final int glType;

        BlendEquation(int glType) {
            this.glType = glType;
        }

        /**
         * @return The OpenGL enum
         */
        public int getGlType() {
            return this.glType;
        }
    }
}
