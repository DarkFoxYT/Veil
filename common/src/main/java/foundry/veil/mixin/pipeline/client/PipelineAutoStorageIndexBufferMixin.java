package foundry.veil.mixin.pipeline.client;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.opengl.GlBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import foundry.veil.ext.AutoStorageIndexBufferExtension;
import foundry.veil.mixin.pipeline.accessor.PipelineGlBufferAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(RenderSystem.AutoStorageIndexBuffer.class)
public abstract class PipelineAutoStorageIndexBufferMixin implements AutoStorageIndexBufferExtension {

    @Shadow
    private GpuBuffer buffer;

    @Shadow
    public abstract GpuBuffer getBuffer(int indexCount);

    @Override
    public void veil$ensureStorage(int neededIndexCount) {
        this.getBuffer(neededIndexCount);
    }

    @Override
    public int veil$getBuffer() {
        if (this.buffer instanceof GlBuffer glBuffer) {
            return ((PipelineGlBufferAccessor) glBuffer).getHandle();
        }
        return 0;
    }
}
