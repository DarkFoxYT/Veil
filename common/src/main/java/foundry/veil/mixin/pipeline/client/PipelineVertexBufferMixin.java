package foundry.veil.mixin.pipeline.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import foundry.veil.ext.VertexBufferExtension;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.lwjgl.opengl.ARBMultiDrawIndirect.glMultiDrawElementsIndirect;
import static org.lwjgl.opengl.GL11C.GL_LINES;
import static org.lwjgl.opengl.GL11C.GL_LINE_STRIP;
import static org.lwjgl.opengl.GL11C.GL_POINTS;
import static org.lwjgl.opengl.GL11C.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11C.GL_TRIANGLE_FAN;
import static org.lwjgl.opengl.GL11C.GL_TRIANGLE_STRIP;
import static org.lwjgl.opengl.GL11C.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL11C.GL_UNSIGNED_SHORT;
import static org.lwjgl.opengl.GL11C.glDrawArrays;
import static org.lwjgl.opengl.GL11C.glGetInteger;
import static org.lwjgl.opengl.GL15C.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15C.glBindBuffer;
import static org.lwjgl.opengl.GL20C.GL_CURRENT_PROGRAM;
import static org.lwjgl.opengl.GL31C.glDrawArraysInstanced;
import static org.lwjgl.opengl.GL31C.glDrawElementsInstanced;
import static org.lwjgl.opengl.GL40C.GL_PATCHES;

@Mixin(VertexBuffer.class)
public abstract class PipelineVertexBufferMixin implements VertexBufferExtension {

    @Shadow
    private VertexFormat.Mode mode;

    @Shadow
    private int indexBufferId;

    @Shadow
    private int indexCount;

    @Shadow
    protected abstract VertexFormat.IndexType getIndexType();

    @Shadow
    @Nullable
    private RenderSystem.AutoStorageIndexBuffer sequentialIndices;

    @Shadow
    private VertexFormat.IndexType indexType;

    @Override
    public void veil$drawInstanced(int instances) {
        VeilRenderSystem.renderThreadExecutor().execute(() -> this._veil$drawInstanced(instances));
    }

    @Override
    public void veil$drawIndirect(long indirect, int drawCount, int stride) {
        VeilRenderSystem.renderThreadExecutor().execute(() -> this._veil$drawIndirect(indirect, drawCount, stride));
    }

    @Override
    public int veil$getIndexCount() {
        return this.indexCount;
    }

    @Inject(method = "draw", at = @At("HEAD"), cancellable = true)
    public void drawPatches(CallbackInfo ci) {
        if (this.mode != VertexFormat.Mode.QUADS) {
            return;
        }

        ShaderProgram shader = VeilRenderSystem.getShader();
        if (shader != null && shader.hasTesselation() && shader.getProgram() == glGetInteger(GL_CURRENT_PROGRAM)) {
            // Quads are internally switched to triangles with indices in vanilla mc, so just use draw arrays
            // This will be wrong if custom indices are used! (transparent objects)
            glDrawArrays(GL_PATCHES, 0, this.indexCount * 4 / 6);
            ci.cancel();
        }
    }

    @ModifyArg(method = "draw", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;drawElements(III)V", remap = false), index = 0)
    public int modifyDrawMode(int glMode) {
        return this.veil$getDrawMode(glMode);
    }

    @Unique
    private int veil$getDrawMode(int defaultMode) {
        ShaderProgram shader = VeilRenderSystem.getShader();
        if (shader != null && shader.hasTesselation() && shader.getProgram() == glGetInteger(GL_CURRENT_PROGRAM)) {
            return GL_PATCHES;
        }
        return defaultMode;
    }

    @Unique
    private void _veil$drawInstanced(int instances) {
        if (this.mode == VertexFormat.Mode.QUADS) {
            ShaderProgram shader = VeilRenderSystem.getShader();
            if (shader != null && shader.hasTesselation() && shader.getProgram() == glGetInteger(GL_CURRENT_PROGRAM)) {
                // Quads are internally switched to triangles with indices in vanilla mc, so just use draw arrays
                // This will be wrong if custom indices are used! (transparent objects)
                glDrawArraysInstanced(GL_PATCHES, 0, this.indexCount * 4 / 6, instances);
                return;
            }
        }

        glDrawElementsInstanced(this.veil$getDrawMode(veil$glMode(this.mode)), this.indexCount, veil$glType(this.getIndexType()), 0L, instances);
    }

    @Unique
    private void _veil$drawIndirect(long indirect, int drawCount, int stride) {
        if (!VeilRenderSystem.multiDrawIndirectSupported()) {
            throw new UnsupportedOperationException("Indirect rendering is not supported by the active rendering backend");
        }

        if (this.sequentialIndices != null) {
            throw new UnsupportedOperationException("Sequential indirect rendering is not supported");
        } else {
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, this.indexBufferId);
            glMultiDrawElementsIndirect(this.veil$getDrawMode(veil$glMode(this.mode)), veil$glType(this.indexType), indirect, drawCount, stride);
        }
    }

    @Unique
    private static int veil$glMode(VertexFormat.Mode mode) {
        return switch (mode) {
            case LINES, DEBUG_LINES -> GL_LINES;
            case DEBUG_LINE_STRIP -> GL_LINE_STRIP;
            case POINTS -> GL_POINTS;
            case TRIANGLES, QUADS -> GL_TRIANGLES;
            case TRIANGLE_STRIP -> GL_TRIANGLE_STRIP;
            case TRIANGLE_FAN -> GL_TRIANGLE_FAN;
        };
    }

    @Unique
    private static int veil$glType(VertexFormat.IndexType type) {
        return switch (type) {
            case SHORT -> GL_UNSIGNED_SHORT;
            case INT -> GL_UNSIGNED_INT;
        };
    }
}
