package foundry.veil.api.client.render.light;

/**
 * Marks a light as Digital Differential Analyzer (DDA) enabled light. This allows the light to draw raymarched shadows.
 *
 * @since 3.3.0
 */
public interface DDALightData {

    /**
     * @return Whether occlusion is enabled
     */
    boolean isOcclusionEnabled();

    /**
     * @return How strongly voxel occlusion affects this light. A value of {@code 0} lets renderers skip shadow work.
     */
    default float getShadowIntensity() {
        return this.isOcclusionEnabled() ? 1.0F : 0.0F;
    }
}
