package foundry.veil.api.client.render.vertex;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.rendertype.VeilRenderType;
import foundry.veil.api.client.render.rendertype.VeilRenderTypeAccessor;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import foundry.veil.impl.client.render.vertex.ARBVertexArray;
import foundry.veil.impl.client.render.vertex.DSAVertexArray;
import foundry.veil.impl.client.render.vertex.LegacyVertexArray;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL40C;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.NativeResource;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.function.Function;
import java.util.function.IntFunction;

import static org.lwjgl.opengl.ARBDirectStateAccess.glCreateVertexArrays;
import static org.lwjgl.opengl.ARBDirectStateAccess.glNamedBufferData;
import static org.lwjgl.opengl.ARBMultiDrawIndirect.glMultiDrawElementsIndirect;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL15C.*;
import static org.lwjgl.opengl.GL20C.GL_CURRENT_PROGRAM;
import static org.lwjgl.opengl.GL30C.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30C.glGenVertexArrays;
import static org.lwjgl.opengl.GL31C.glDrawArraysInstanced;
import static org.lwjgl.opengl.GL31C.glDrawElementsInstanced;
import static org.lwjgl.opengl.GL40C.GL_PATCHES;

/**
 * More generic alternative to {@link VertexBuffer} that uses the latest available OpenGL version.
 *
 * @author Ocelot
 */
public abstract class VertexArray implements NativeResource {

    public static final int VERTEX_BUFFER = 0;
    public static final int ELEMENT_ARRAY_BUFFER = 1;

    private static VertexArrayType vertexArrayType;

    protected final int id;
    protected final VertexArrayBuilder builder;
    protected final Int2IntMap buffers;
    protected int indexCount;
    protected IndexType indexType;
    protected VertexFormat.Mode drawMode;

    @Nullable
    protected RenderSystem.AutoStorageIndexBuffer indexBuffer;

    @ApiStatus.Internal
    protected VertexArray(int id, Function<VertexArray, VertexArrayBuilder> builder) {
        this.id = id;
        this.builder = builder.apply(this);
        this.buffers = new Int2IntArrayMap();
        this.indexCount = 0;
        this.indexType = IndexType.BYTE;
        this.drawMode = VertexFormat.Mode.TRIANGLES;
    }

    private static void loadType() {
        if (vertexArrayType == null) {
            if (VeilRenderSystem.directStateAccessSupported()) {
                vertexArrayType = VertexArrayType.DSA;
            } else {
                GLCapabilities caps = GL.getCapabilities();
                if (caps.OpenGL43 || caps.GL_ARB_vertex_attrib_binding) {
                    vertexArrayType = VertexArrayType.ARB;
                } else {
                    vertexArrayType = VertexArrayType.LEGACY;
                }
            }
        }
    }

    /**
     * Creates a single new vertex array.
     *
     * @return A new vertex array
     */
    public static VertexArray create() {
        RenderSystem.assertOnRenderThread();
        loadType();
        return vertexArrayType.factory.apply(VeilRenderSystem.directStateAccessSupported() ? glCreateVertexArrays() : glGenVertexArrays());
    }

    /**
     * Creates an array of vertex arrays.
     *
     * @param count The number of arrays to create
     * @return An array of new vertex arrays
     */
    public static VertexArray[] create(int count) {
        VertexArray[] fill = new VertexArray[count];
        create(fill);
        return fill;
    }

    /**
     * Replaces each element of the specified array with a new vertex array.
     *
     * @param fill The array to fill
     */
    public static void create(VertexArray[] fill) {
        RenderSystem.assertOnRenderThread();
        if (fill.length == 0) {
            return;
        }

        loadType();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer arrays = stack.mallocInt(fill.length);
            if (VeilRenderSystem.directStateAccessSupported()) {
                glCreateVertexArrays(arrays);
            } else {
                glGenVertexArrays(arrays);
            }

            for (int i = 0; i < arrays.limit(); i++) {
                fill[i] = vertexArrayType.factory.apply(arrays.get(i));
            }
        }
    }

    /**
     * Sets up the draw state with the specified render type.
     *
     * @param renderType The render type to set up
     * @since 1.2.0
     */
    public void setup(RenderType renderType) {
        VeilRenderTypeAccessor accessor = VeilRenderType.getShards(renderType);
        accessor.outputState().setupRenderState();
        accessor.shaderState().setupRenderState();

        ShaderProgram shader = VeilRenderSystem.getShader();
        if (shader != null) {
            shader.setDefaultUniforms(this.drawMode);
            shader.bindSamplers(0);
        }

        BlendFunction blend = accessor.transparencyState().blendFunction();
        if (blend == BlendFunction.ADDITIVE) {
            GlStateManager._enableBlend();
            GlStateManager._blendFuncSeparate(GL_ONE, GL_ONE, GL_ONE, GL_ONE);
        } else if (blend != null) {
            GlStateManager._enableBlend();
            GlStateManager._blendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
        } else {
            GlStateManager._disableBlend();
        }

        String depthName = accessor.depthTestState().name();
        if ("always".equals(depthName)) {
            GlStateManager._disableDepthTest();
        } else {
            GlStateManager._enableDepthTest();
            GlStateManager._depthFunc(switch (depthName) {
                case ">", "greater" -> GL_GREATER;
                case "=", "equal" -> GL_EQUAL;
                case "<", "less" -> GL_LESS;
                case ">=", "gequal" -> GL_GEQUAL;
                case "!=", "notequal" -> GL_NOTEQUAL;
                default -> GL_LEQUAL;
            });
        }

        if (accessor.cullState().cull()) {
            GlStateManager._enableCull();
        } else {
            GlStateManager._disableCull();
        }

        boolean color = accessor.writeMaskState().writeColor();
        GlStateManager._colorMask(color, color, color, color);
        GlStateManager._depthMask(accessor.writeMaskState().writeDepth());
    }

    /**
     * Clears the specified render type.
     *
     * @param renderType The render type to clear
     * @since 1.2.0
     */
    public void clear(RenderType renderType) {
        VeilRenderTypeAccessor accessor = VeilRenderType.getShards(renderType);
        accessor.outputState().clearRenderState();
        ShaderProgram.unbind();
        GlStateManager._enableDepthTest();
        GlStateManager._depthFunc(GL_LEQUAL);
        GlStateManager._depthMask(true);
        GlStateManager._colorMask(true, true, true, true);
        GlStateManager._disableBlend();
        GlStateManager._blendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ZERO);
        GlStateManager._enableCull();
    }

    /**
     * Creates a new buffer object owned by this vertex array or retrieves an existing buffer.
     *
     * @param index The index of the buffer to get
     * @return A vertex array object
     */
    public int getOrCreateBuffer(int index) {
        if (index < 0) {
            throw new ArrayIndexOutOfBoundsException(index);
        }
        return this.buffers.computeIfAbsent(index, unused -> GlStateManager._glGenBuffers());
    }

    /**
     * @return The OpenGL id of this vertex array
     */
    public int getId() {
        return this.id;
    }

    /**
     * @return The number of indices in this array
     */
    public int getIndexCount() {
        return this.indexCount;
    }

    /**
     * @return The data type of the index buffer
     */
    public IndexType getIndexType() {
        return this.indexType;
    }

    /**
     * @return The GL polygon draw type
     * @see #setDrawMode(VertexFormat.Mode)
     */
    public VertexFormat.Mode getDrawMode() {
        return this.drawMode;
    }

    /**
     * Uploads mesh data into the specified buffer.
     *
     * @param data  The data to upload
     * @param usage The draw usage
     */
    public static void upload(int buffer, ByteBuffer data, DrawUsage usage) {
        if (VeilRenderSystem.directStateAccessSupported()) {
            glNamedBufferData(buffer, data, usage.getGlType());
        } else {
            glBindBuffer(GL_ARRAY_BUFFER, buffer);
            glBufferData(GL_ARRAY_BUFFER, data, usage.getGlType());
            glBindBuffer(GL_ARRAY_BUFFER, 0);
        }
    }

    /**
     * Uploads vanilla mc mesh data into this vertex array. Only a single mesh can be uploaded this way.
     *
     * @param meshData The data to upload
     * @param usage    The draw usage
     */
    public void upload(MeshData meshData, DrawUsage usage) {
        this.upload(0, meshData, usage);
    }

    /**
     * Uploads vanilla mc mesh data into this vertex array. Only a single mesh can be uploaded this way.
     *
     * @param attributeStart The attribute to start uploading vertex data to
     * @param meshData       The data to upload
     * @param usage          The draw usage
     */
    public void upload(int attributeStart, MeshData meshData, DrawUsage usage) {
        try (meshData) {
            RenderSystem.assertOnRenderThread();
            MeshData.DrawState drawState = meshData.drawState();
            VertexArrayBuilder builder = this.editFormat();

            int vertexBuffer = this.getOrCreateBuffer(VERTEX_BUFFER);
            upload(vertexBuffer, meshData.vertexBuffer(), usage);
            builder.applyFrom(VERTEX_BUFFER, vertexBuffer, attributeStart, drawState.format());

            ByteBuffer indexBuffer = meshData.indexBuffer();
            if (indexBuffer != null) {
                this.uploadIndexBuffer(indexBuffer);
            } else {
                this.uploadIndexBuffer(drawState);
            }

            this.indexCount = drawState.indexCount();
            this.indexType = IndexType.fromBlaze3D(drawState.indexType());
            this.drawMode = drawState.mode();
        }
    }

    /**
     * Uploads index data to the vertex array.
     *
     * @param drawState The buffer draw state
     */
    public void uploadIndexBuffer(MeshData.DrawState drawState) {
        this.indexBuffer = null;
        IndexType indexType = IndexType.fromBlaze3D(drawState.indexType());
        ByteBuffer data = MemoryUtil.memAlloc(drawState.indexCount() * indexType.getBytes());
        try {
            if (drawState.mode() == VertexFormat.Mode.QUADS) {
                for (int vertex = 0; data.position() < data.capacity(); vertex += 4) {
                    putIndex(data, indexType, vertex);
                    putIndex(data, indexType, vertex + 1);
                    putIndex(data, indexType, vertex + 2);
                    putIndex(data, indexType, vertex + 2);
                    putIndex(data, indexType, vertex + 3);
                    putIndex(data, indexType, vertex);
                }
            } else {
                for (int i = 0; i < drawState.indexCount(); i++) {
                    putIndex(data, indexType, i);
                }
            }
            data.flip();
            this.uploadIndexBuffer(data);
        } finally {
            MemoryUtil.memFree(data);
        }
    }

    /**
     * Uploads index data to the vertex array.
     *
     * @param data The data to upload
     */
    public void uploadIndexBuffer(ByteBuffer data) {
        this.indexBuffer = null;
        GlStateManager._glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, this.getOrCreateBuffer(ELEMENT_ARRAY_BUFFER));
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, data, GL_STATIC_DRAW);
    }

    /**
     * Uploads index data to the vertex array.
     *
     * @param data      The data to upload
     * @param indexType The type of data stored in data
     * @since 1.2.0
     */
    public void uploadIndexBuffer(ByteBuffer data, IndexType indexType) {
        this.uploadIndexBuffer(data);
        this.setIndexCount(data.remaining() >> indexType.ordinal(), indexType);
    }

    /**
     * @return A builder for applying changes to this array
     */
    public VertexArrayBuilder editFormat() {
        this.bind();
        return this.builder;
    }

    /**
     * Binds this vertex array and applies any changes to the format automatically.
     */
    public void bind() {
        VeilRenderSystem.bindVertexArray(this.id);

        // Because the auto index buffers can decide to change formats occasionally, this is needed to keep the type correct
        this.indexBuffer = null;
    }

    /**
     * Unbinds the current vertex array.
     */
    public static void unbind() {
        VeilRenderSystem.bindVertexArray(0);
    }

    /**
     * Draws {@link #indexCount} number of indices with the previously defined draw mode.
     * <br>
     * {@link #bind()} must be called before this.
     */
    public void draw() {
        ShaderProgram shader = VeilRenderSystem.getShader();
        if (shader != null && shader.hasTesselation() && shader.getProgram() == glGetInteger(GL_CURRENT_PROGRAM)) {
            if (this.drawMode == VertexFormat.Mode.QUADS) {
                // Quads are internally switched to triangles with indices in vanilla mc, so just use draw arrays
                // This will be wrong if custom indices are used! (transparent objects)
                glDrawArrays(GL_PATCHES, 0, this.indexCount * 4 / 6);
                return;
            }

            glDrawElements(GL_PATCHES, this.indexCount, this.indexType.getGlType(), 0L);
            return;
        }

        glDrawElements(glMode(this.drawMode), this.indexCount, this.indexType.getGlType(), 0L);
    }

    /**
     * Draws {@link #indexCount} number of indices with the previously defined draw mode a number of times.
     * <br>
     * {@link #bind()} must be called before this.
     *
     * @param instances The number of instances to draw
     */
    public void drawInstanced(int instances) {
        ShaderProgram shader = VeilRenderSystem.getShader();
        if (shader != null && shader.hasTesselation() && shader.getProgram() == glGetInteger(GL_CURRENT_PROGRAM)) {
            if (this.drawMode == VertexFormat.Mode.QUADS) {
                // Quads are internally switched to triangles with indices in vanilla mc, so just use draw arrays
                // This will be wrong if custom indices are used! (transparent objects)
                glDrawArraysInstanced(GL_PATCHES, 0, this.indexCount * 4 / 6, instances);
                return;
            }

            glDrawElementsInstanced(GL_PATCHES, this.indexCount, this.indexType.getGlType(), 0L, instances);
            return;
        }

        glDrawElementsInstanced(glMode(this.drawMode), this.indexCount, this.indexType.getGlType(), 0L, instances);
    }

    /**
     * Draws {@link #indexCount} number of indices with the previously defined draw mode a number of times.
     * <br>
     * {@link #bind()} must be called before this.
     * <br>
     * <strong>Note: This only works if {@link VeilRenderSystem#multiDrawIndirectSupported()} is <code>true</code></strong>
     *
     * @param indirect  A pointer into the currently bound {@link GL40C#GL_DRAW_INDIRECT_BUFFER} or the address of a struct containing draw data
     * @param drawCount The number of instances to draw
     * @param stride    The stride between commands or <code>0</code> if they are tightly packed
     */
    public void drawIndirect(long indirect, int drawCount, int stride) {
        if (!VeilRenderSystem.multiDrawIndirectSupported()) {
            throw new UnsupportedOperationException("Indirect rendering is not supported by the active rendering backend");
        }

        glMultiDrawElementsIndirect(glMode(this.drawMode), this.indexType.getGlType(), indirect, drawCount, stride);
    }

    /**
     * Draws {@link #indexCount} number of indices with the previously defined draw mode.
     * This method applies the specified render type automatically.
     * <br>
     * {@link #bind()} must be called before this.
     */
    public void drawWithRenderType(RenderType renderType) {
        if (renderType == null) {
            return;
        }

        this.setup(renderType);
        this.draw();
        this.clear(renderType);

    }

    /**
     * Draws {@link #indexCount} number of indices with the previously defined draw mode a number of times.
     * This method applies the specified render type automatically.
     * <br>
     * {@link #bind()} must be called before this.
     *
     * @param instances The number of instances to draw
     */
    public void drawInstancedWithRenderType(RenderType renderType, int instances) {
        if (renderType == null) {
            return;
        }

        this.setup(renderType);
        this.drawInstanced(instances);
        this.clear(renderType);

    }

    /**
     * Draws {@link #indexCount} number of indices with the previously defined draw mode a number of times.
     * This method applies the specified render type automatically.
     * <br>
     * {@link #bind()} must be called before this.
     * <br>
     * <strong>Note: This only works if {@link VeilRenderSystem#multiDrawIndirectSupported()} is <code>true</code></strong>
     *
     * @param indirect  A pointer into the currently bound {@link GL40C#GL_DRAW_INDIRECT_BUFFER} or the address of a struct containing draw data
     * @param drawCount The number of instances to draw
     * @param stride    The stride between commands or <code>0</code> if they are tightly packed
     */
    public void drawIndirectWithRenderType(RenderType renderType, long indirect, int drawCount, int stride) {
        if (renderType == null) {
            return;
        }

        this.setup(renderType);
        this.drawIndirect(indirect, drawCount, stride);
        this.clear(renderType);

    }

    /**
     * Sets the number of indices and what data type they are.
     *
     * @param indexCount The number of indices in the entire mesh
     * @param indexType  The data type of the indices
     */
    public void setIndexCount(int indexCount, IndexType indexType) {
        this.indexCount = indexCount;
        this.indexType = indexType;
    }

    /**
     * Sets the type of polygons draw calls will draw.
     *
     * @param drawMode The new draw mode
     */
    public void setDrawMode(VertexFormat.Mode drawMode) {
        this.drawMode = drawMode;
    }

    private static void putIndex(ByteBuffer data, IndexType indexType, int value) {
        switch (indexType) {
            case BYTE -> data.put((byte) value);
            case SHORT -> data.putShort((short) value);
            case INT -> data.putInt(value);
        }
    }

    private static int glMode(VertexFormat.Mode mode) {
        return switch (mode) {
            case LINES, DEBUG_LINES -> GL_LINES;
            case DEBUG_LINE_STRIP -> GL_LINE_STRIP;
            case POINTS -> GL_POINTS;
            case TRIANGLES, QUADS -> GL_TRIANGLES;
            case TRIANGLE_STRIP -> GL_TRIANGLE_STRIP;
            case TRIANGLE_FAN -> GL_TRIANGLE_FAN;
        };
    }

    @Override
    public void free() {
        RenderSystem.assertOnRenderThread();
        glDeleteBuffers(this.buffers.values().toIntArray());
        glDeleteVertexArrays(this.id);
        this.buffers.clear();
    }

    private enum VertexArrayType {
        LEGACY(LegacyVertexArray::new),
        ARB(ARBVertexArray::new),
        DSA(DSAVertexArray::new);

        private final IntFunction<VertexArray> factory;

        VertexArrayType(IntFunction<VertexArray> factory) {
            this.factory = factory;
        }
    }

    /**
     * The type of GL indices that can be used.
     *
     * @author Ocelot
     */
    public enum IndexType {
        BYTE(GL_UNSIGNED_BYTE),
        SHORT(GL_UNSIGNED_SHORT),
        INT(GL_UNSIGNED_INT);

        private final int glType;
        private final int bytes;

        IndexType(int glType) {
            this.glType = glType;
            this.bytes = 1 << this.ordinal();
        }

        public int getGlType() {
            return this.glType;
        }

        public int getBytes() {
            return this.bytes;
        }

        public static IndexType fromBlaze3D(VertexFormat.IndexType type) {
            return switch (type) {
                case SHORT -> SHORT;
                case INT -> INT;
            };
        }

        public static IndexType least(int maxIndex) {
            if ((maxIndex & 0xFFFFFF00) == 0) {
                return BYTE;
            }
            if ((maxIndex & 0xFFFF0000) == 0) {
                return SHORT;
            }
            return INT;
        }
    }

    /**
     * Specifies how the graphics card should manage buffer data.
     *
     * @author Ocelot
     */
    public enum DrawUsage {
        /**
         * The data is set only once and used many times.
         */
        STATIC(GL_STATIC_DRAW),
        /**
         * The data is changed a lot and used many times.
         */
        DYNAMIC(GL_DYNAMIC_DRAW),
        /**
         * The data is set only once and used by the GPU at most a few times.
         */
        STREAM(GL_STREAM_DRAW);

        private final int glType;

        DrawUsage(int glType) {
            this.glType = glType;
        }

        public int getGlType() {
            return this.glType;
        }

        /**
         * Converts the given Blaze3D type to Veil draw usage.
         *
         * @param type The type to convert
         * @return The Veil draw usage
         */
        public static DrawUsage fromBlaze3D(VertexBuffer.Usage type) {
            return switch (type) {
                case STATIC -> STATIC;
                case DYNAMIC -> DYNAMIC;
            };
        }
    }
}
