package foundry.veil.api.client.render.light.data;

/**
 * Distance attenuation modes available to deferred point and spot lights.
 *
 * @since 4.0.0
 */
public enum LightFalloff {
    SMOOTH("SMOOTH"),
    LINEAR("LINEAR"),
    QUADRATIC("QUADRATIC"),
    INVERSE_SQUARE("INVERSE SQUARE");

    private final String displayName;

    LightFalloff(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return this.displayName;
    }
}
