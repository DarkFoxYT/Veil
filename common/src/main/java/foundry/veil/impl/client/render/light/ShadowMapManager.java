package foundry.veil.impl.client.render.light;

import com.mojang.blaze3d.systems.RenderSystem;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import foundry.veil.api.client.render.framebuffer.FramebufferAttachmentDefinition;
import foundry.veil.api.client.render.light.data.LightData;
import org.lwjgl.system.NativeResource;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Map;
import static org.lwjgl.opengl.GL11C.*;

/**
 * Framebuffer-backed shadow atlas. A slot has both a depth page and an HDR
 * transmission page; the latter is the hook used by stained/translucent
 * materials to tint shadows instead of making them flat black.
 */
public final class ShadowMapManager implements NativeResource {
    public static final int ATLAS_RESOLUTION = 2048;
    public static final int SLOT_RESOLUTION = 1024;
    public static final int MAX_UPDATES_PER_FRAME = 2;
    private final Deque<Integer> freeSlots = new ArrayDeque<>();
    private final Map<LightData, CachedShadow> cachedShadows = new IdentityHashMap<>();
    private ShadowTarget atlas;
    private int updates;

    public void beginFrame() { this.updates = MAX_UPDATES_PER_FRAME; }
    public boolean claimUpdate() { return this.updates > 0 && this.updates-- > 0; }

    /**
     * Returns the cached atlas page for a light. A page only needs a world
     * render when its light revision changed or its bounded refresh period
     * elapsed, avoiding repeated scene renders for static lights.
     */
    public CachedShadow getOrCreate(LightData light, long frame) {
        CachedShadow cached = this.cachedShadows.get(light);
        if (cached != null) return cached;
        ShadowTarget target = this.acquire();
        if (target == null) return null;
        cached = new CachedShadow(target, light.getRevision(), frame);
        this.cachedShadows.put(light, cached);
        return cached;
    }

    public boolean needsUpdate(LightData light, CachedShadow cached, long frame) {
        return cached.revision != light.getRevision() || frame - cached.lastRenderedFrame >= 120;
    }

    public void markRendered(LightData light, CachedShadow cached, long frame) {
        cached.revision = light.getRevision();
        cached.lastRenderedFrame = frame;
    }

    public void remove(LightData light) {
        CachedShadow cached = this.cachedShadows.remove(light);
        if (cached != null) this.release(cached.target);
    }

    public ShadowTarget acquire() {
        RenderSystem.assertOnRenderThread();
        if (this.atlas == null) {
            this.atlas = create();
            for (int i = 3; i >= 0; i--) this.freeSlots.addFirst(i);
        }
        Integer slot = this.freeSlots.pollFirst();
        return slot == null ? null : this.atlas.slot(slot);
    }

    public void release(ShadowTarget target) {
        target.clear();
        this.freeSlots.addFirst(target.index());
    }

    private static ShadowTarget create() {
        AdvancedFbo fbo = AdvancedFbo.withSize(ATLAS_RESOLUTION, ATLAS_RESOLUTION)
                .setFormat(FramebufferAttachmentDefinition.Format.RGBA16F).setFilter(true, false).addColorTextureBuffer()
                .setFormat(FramebufferAttachmentDefinition.Format.DEPTH_COMPONENT).setFilter(false, false).setDepthTextureBuffer()
                .setDebugLabel("Veil Shadow Map").build(true);
        return new ShadowTarget(fbo, -1);
    }

    @Override public void free() { if (this.atlas != null) this.atlas.free(); this.atlas = null; this.freeSlots.clear(); this.cachedShadows.clear(); }

    public static final class CachedShadow {
        private final ShadowTarget target;
        private long revision;
        private long lastRenderedFrame;

        private CachedShadow(ShadowTarget target, long revision, long lastRenderedFrame) {
            this.target = target;
            this.revision = revision;
            this.lastRenderedFrame = lastRenderedFrame;
        }

        public ShadowTarget target() { return this.target; }
    }

    public record ShadowTarget(AdvancedFbo framebuffer, int index) implements NativeResource {
        private ShadowTarget slot(int index) { return new ShadowTarget(this.framebuffer, index); }
        public int depthTexture() { return framebuffer.getDepthTextureAttachment().getId(); }
        public int transmissionTexture() { return framebuffer.getColorTextureAttachment(0).getId(); }
        public float u0() { return (index & 1) * 0.5F; }
        public float v0() { return (index >> 1) * 0.5F; }
        public float u1() { return u0() + 0.5F; }
        public float v1() { return v0() + 0.5F; }
        public void bind() { framebuffer.bind(true); framebuffer.drawBuffers(0); }
        public void clear() {
            framebuffer.bind(false);
            glEnable(GL_SCISSOR_TEST);
            glScissor((index & 1) * SLOT_RESOLUTION, (index >> 1) * SLOT_RESOLUTION, SLOT_RESOLUTION, SLOT_RESOLUTION);
            framebuffer.clear(1, 1, 1, 1, 1, GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            glDisable(GL_SCISSOR_TEST);
        }
        @Override public void free() { framebuffer.free(); }
    }
}
