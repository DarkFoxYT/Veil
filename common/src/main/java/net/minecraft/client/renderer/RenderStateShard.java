package net.minecraft.client.renderer;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.LogicOp;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.function.Supplier;

import static org.lwjgl.opengl.GL11C.*;

public class RenderStateShard {

    protected final String name;
    protected final Runnable setupState;
    protected final Runnable clearState;

    public RenderStateShard(String name, Runnable setupState, Runnable clearState) {
        this.name = name;
        this.setupState = setupState;
        this.clearState = clearState;
    }

    public void setupRenderState() {
        this.setupState.run();
    }

    public void clearRenderState() {
        this.clearState.run();
    }

    public String name() {
        return this.name;
    }

    public static final EmptyTextureStateShard NO_TEXTURE = new EmptyTextureStateShard("no_texture", () -> {
    }, () -> {
    });
    public static final TransparencyStateShard NO_TRANSPARENCY = new TransparencyStateShard("no_transparency", null);
    public static final TransparencyStateShard ADDITIVE_TRANSPARENCY = new TransparencyStateShard("additive_transparency", BlendFunction.ADDITIVE);
    public static final TransparencyStateShard LIGHTNING_TRANSPARENCY = new TransparencyStateShard("lightning_transparency", BlendFunction.LIGHTNING);
    public static final TransparencyStateShard GLINT_TRANSPARENCY = new TransparencyStateShard("glint_transparency", BlendFunction.GLINT);
    public static final TransparencyStateShard CRUMBLING_TRANSPARENCY = new TransparencyStateShard("crumbling_transparency", BlendFunction.TRANSLUCENT);
    public static final TransparencyStateShard TRANSLUCENT_TRANSPARENCY = new TransparencyStateShard("translucent_transparency", BlendFunction.TRANSLUCENT);
    public static final DepthTestStateShard NO_DEPTH_TEST = new DepthTestStateShard("always", GL_ALWAYS);
    public static final DepthTestStateShard EQUAL_DEPTH_TEST = new DepthTestStateShard("=", GL_EQUAL);
    public static final DepthTestStateShard LEQUAL_DEPTH_TEST = new DepthTestStateShard("<=", GL_LEQUAL);
    public static final DepthTestStateShard GREATER_DEPTH_TEST = new DepthTestStateShard(">", GL_GREATER);
    public static final CullStateShard CULL = new CullStateShard(true);
    public static final CullStateShard NO_CULL = new CullStateShard(false);
    public static final LightmapStateShard LIGHTMAP = new LightmapStateShard(true);
    public static final LightmapStateShard NO_LIGHTMAP = new LightmapStateShard(false);
    public static final OverlayStateShard OVERLAY = new OverlayStateShard(true);
    public static final OverlayStateShard NO_OVERLAY = new OverlayStateShard(false);
    public static final LayeringStateShard NO_LAYERING = new LayeringStateShard("no_layering", () -> {
    }, () -> {
    });
    public static final LayeringStateShard POLYGON_OFFSET_LAYERING = new LayeringStateShard("polygon_offset_layering", () -> {
    }, () -> {
    });
    public static final LayeringStateShard VIEW_OFFSET_Z_LAYERING = new LayeringStateShard("view_offset_z_layering", () -> {
    }, () -> {
    });
    public static final OutputStateShard MAIN_TARGET = new OutputStateShard("main_target", () -> {
    }, () -> {
    });
    public static final TexturingStateShard DEFAULT_TEXTURING = new TexturingStateShard("default_texturing", () -> {
    }, () -> {
    });
    public static final WriteMaskStateShard COLOR_DEPTH_WRITE = new WriteMaskStateShard(true, true);
    public static final WriteMaskStateShard COLOR_WRITE = new WriteMaskStateShard(true, false);
    public static final WriteMaskStateShard DEPTH_WRITE = new WriteMaskStateShard(false, true);
    public static final LineStateShard DEFAULT_LINE = new LineStateShard(OptionalDouble.empty());
    public static final ColorLogicStateShard NO_COLOR_LOGIC = new ColorLogicStateShard("no_color_logic", LogicOp.NONE);

    public static class EmptyTextureStateShard extends RenderStateShard {
        public EmptyTextureStateShard(String name, Runnable setupState, Runnable clearState) {
            super(name, setupState, clearState);
        }
    }

    public static class TextureStateShard extends EmptyTextureStateShard {
        private final Identifier texture;
        private final boolean blur;
        private final boolean mipmap;

        public TextureStateShard(Identifier texture, boolean blur, boolean mipmap) {
            super("texture", () -> {
            }, () -> {
            });
            this.texture = texture;
            this.blur = blur;
            this.mipmap = mipmap;
        }

        public Identifier texture() {
            return this.texture;
        }

        public boolean blur() {
            return this.blur;
        }

        public boolean mipmap() {
            return this.mipmap;
        }
    }

    public static class MultiTextureStateShard extends EmptyTextureStateShard {
        private final List<TextureStateShard> textures;

        private MultiTextureStateShard(List<TextureStateShard> textures) {
            super("multi_texture", () -> {
            }, () -> {
            });
            this.textures = List.copyOf(textures);
        }

        public List<TextureStateShard> textures() {
            return this.textures;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private final List<TextureStateShard> textures = new ArrayList<>();

            public Builder add(Identifier texture, boolean blur, boolean mipmap) {
                this.textures.add(new TextureStateShard(texture, blur, mipmap));
                return this;
            }

            public MultiTextureStateShard build() {
                return new MultiTextureStateShard(this.textures);
            }
        }
    }

    public static class ShaderStateShard extends RenderStateShard {
        private final Supplier<@Nullable ShaderInstance> shader;

        public ShaderStateShard() {
            this(() -> null);
        }

        public ShaderStateShard(Supplier<@Nullable ShaderInstance> shader) {
            super("shader", () -> {
            }, () -> {
            });
            this.shader = shader;
        }

        public @Nullable ShaderInstance shader() {
            return this.shader.get();
        }
    }

    public static class TransparencyStateShard extends RenderStateShard {
        private final @Nullable BlendFunction blendFunction;

        public TransparencyStateShard(String name, @Nullable BlendFunction blendFunction) {
            super(name, () -> {
            }, () -> {
            });
            this.blendFunction = blendFunction;
        }

        public @Nullable BlendFunction blendFunction() {
            return this.blendFunction;
        }
    }

    public static class DepthTestStateShard extends RenderStateShard {
        private final int function;

        public DepthTestStateShard(String name, int function) {
            super(name, () -> {
            }, () -> {
            });
            this.function = function;
        }

        public DepthTestFunction function() {
            return switch (this.function) {
                case GL_NEVER -> DepthTestFunction.NO_DEPTH_TEST;
                case GL_EQUAL -> DepthTestFunction.EQUAL_DEPTH_TEST;
                case GL_LESS -> DepthTestFunction.LESS_DEPTH_TEST;
                case GL_GREATER -> DepthTestFunction.GREATER_DEPTH_TEST;
                default -> this.function == GL_ALWAYS ? DepthTestFunction.NO_DEPTH_TEST : DepthTestFunction.LEQUAL_DEPTH_TEST;
            };
        }
    }

    public static class CullStateShard extends RenderStateShard {
        private final boolean cull;

        public CullStateShard(boolean cull) {
            super(cull ? "cull" : "no_cull", () -> {
            }, () -> {
            });
            this.cull = cull;
        }

        public boolean cull() {
            return this.cull;
        }
    }

    public static class LightmapStateShard extends RenderStateShard {
        private final boolean enabled;

        public LightmapStateShard(boolean enabled) {
            super(enabled ? "lightmap" : "no_lightmap", () -> {
            }, () -> {
            });
            this.enabled = enabled;
        }

        public boolean enabled() {
            return this.enabled;
        }
    }

    public static class OverlayStateShard extends RenderStateShard {
        private final boolean enabled;

        public OverlayStateShard(boolean enabled) {
            super(enabled ? "overlay" : "no_overlay", () -> {
            }, () -> {
            });
            this.enabled = enabled;
        }

        public boolean enabled() {
            return this.enabled;
        }
    }

    public static class LayeringStateShard extends RenderStateShard {
        public LayeringStateShard(String name, Runnable setupState, Runnable clearState) {
            super(name, setupState, clearState);
        }
    }

    public static class OutputStateShard extends RenderStateShard {
        public OutputStateShard(String name, Runnable setupState, Runnable clearState) {
            super(name, setupState, clearState);
        }
    }

    public static class TexturingStateShard extends RenderStateShard {
        public TexturingStateShard(String name, Runnable setupState, Runnable clearState) {
            super(name, setupState, clearState);
        }
    }

    public static class WriteMaskStateShard extends RenderStateShard {
        private final boolean writeColor;
        private final boolean writeDepth;

        public WriteMaskStateShard(boolean writeColor, boolean writeDepth) {
            super("write_mask", () -> {
            }, () -> {
            });
            this.writeColor = writeColor;
            this.writeDepth = writeDepth;
        }

        public boolean writeColor() {
            return this.writeColor;
        }

        public boolean writeDepth() {
            return this.writeDepth;
        }
    }

    public static class LineStateShard extends RenderStateShard {
        private final OptionalDouble width;

        public LineStateShard(OptionalDouble width) {
            super("line", () -> {
            }, () -> {
            });
            this.width = width;
        }

        public OptionalDouble width() {
            return this.width;
        }
    }

    public static class ColorLogicStateShard extends RenderStateShard {
        private final LogicOp logicOp;

        public ColorLogicStateShard(String name, LogicOp logicOp) {
            super(name, () -> {
            }, () -> {
            });
            this.logicOp = logicOp;
        }

        public ColorLogicStateShard(String name, Runnable setupState, Runnable clearState) {
            super(name, setupState, clearState);
            this.logicOp = LogicOp.NONE;
        }

        public LogicOp logicOp() {
            return this.logicOp;
        }
    }

    public static class OffsetTexturingStateShard extends TexturingStateShard {
        public OffsetTexturingStateShard(float x, float y) {
            super("offset_texturing", () -> {
            }, () -> {
            });
        }
    }
}
