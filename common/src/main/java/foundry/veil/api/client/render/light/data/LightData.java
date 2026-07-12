package foundry.veil.api.client.render.light.data;

import foundry.veil.api.client.color.Color;
import foundry.veil.api.client.color.Colorc;
import foundry.veil.api.client.registry.LightTypeRegistry;
import foundry.veil.api.client.render.CullFrustum;
import foundry.veil.api.client.render.light.renderer.LightRenderer;
import net.minecraft.client.Camera;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * A source of luminance in a scene. Drawn using {@link LightRenderer}.
 *
 * @since 2.0.0
 */
public abstract class LightData {

    protected final Color color;
    protected float brightness;
    protected float temperature;
    private long revision;

    public LightData() {
        this.color = new Color(Color.WHITE);
        this.brightness = 1.0F;
        this.temperature = 6500.0F;
    }

    /**
     * Marks this light as changed so renderer-side buffers can be refreshed lazily.
     */
    public void markDirty() {
        this.revision++;
    }

    /**
     * @return The current mutation revision of this light.
     */
    public long getRevision() {
        return this.revision;
    }

    /**
     * @return The color of this light
     */
    public Color getColor() {
        return this.color;
    }

    /**
     * @return The brightness multiplier of the light.
     */
    public float getBrightness() {
        return this.brightness;
    }

    /**
     * @return The light color temperature in Kelvin.
     */
    public float getTemperature() {
        return this.temperature;
    }

    /**
     * Calculates the RGB multiplier for this light's color temperature.
     *
     * @param store The vector to store the color in
     * @return The passed in vector
     */
    public Vector3f getTemperatureColor(Vector3f store) {
        return temperatureToColor(this.temperature, store);
    }

    /**
     * Sets the RGB color of this light.
     *
     * @param color The new color values
     */
    public LightData setColor(Vector3fc color) {
        return this.setColor(color.x(), color.y(), color.z());
    }

    /**
     * Sets the RGB color of this light.
     *
     * @param color The new color values
     */
    public LightData setColor(Colorc color) {
        return this.setColor(color.red(), color.green(), color.blue());
    }

    /**
     * Sets the RGB color of this light.
     *
     * @param red   The new red
     * @param green The new green
     * @param blue  The new blue
     */
    public LightData setColor(float red, float green, float blue) {
        if (Float.compare(this.color.red(), red) == 0 &&
                Float.compare(this.color.green(), green) == 0 &&
                Float.compare(this.color.blue(), blue) == 0) {
            return this;
        }
        this.color.set(red, green, blue);
        this.markDirty();
        return this;
    }

    /**
     * Sets the RGB color of this light.
     *
     * @param color THe new RGB of this light
     */
    public LightData setColor(int color) {
        if (this.color.rgb() == (color & 0xFFFFFF)) {
            return this;
        }
        this.color.setRGB(color);
        this.markDirty();
        return this;
    }

    /**
     * Sets the brightness of the light. This acts as a multiplier on the light's color.
     *
     * @param brightness The new brightness of the light.
     */
    public LightData setBrightness(float brightness) {
        if (Float.compare(this.brightness, brightness) == 0) {
            return this;
        }
        this.brightness = brightness;
        this.markDirty();
        return this;
    }

    /**
     * Sets the light color temperature in Kelvin.
     *
     * @param temperature The new color temperature
     */
    public LightData setTemperature(float temperature) {
        temperature = Math.max(1000.0F, Math.min(40000.0F, temperature));
        if (Float.compare(this.temperature, temperature) == 0) {
            return this;
        }
        this.temperature = temperature;
        this.markDirty();
        return this;
    }

    /**
     * Converts a Kelvin temperature into a normalized RGB multiplier.
     *
     * @param temperature The temperature in Kelvin
     * @param store       The vector to store the color in
     * @return The passed in vector
     */
    public static Vector3f temperatureToColor(float temperature, Vector3f store) {
        double kelvin = Math.max(1000.0, Math.min(40000.0, temperature)) / 100.0;
        double red;
        double green;
        double blue;

        if (kelvin <= 66.0) {
            red = 255.0;
            green = 99.4708025861 * Math.log(kelvin) - 161.1195681661;
            blue = kelvin <= 19.0 ? 0.0 : 138.5177312231 * Math.log(kelvin - 10.0) - 305.0447927307;
        } else {
            red = 329.698727446 * Math.pow(kelvin - 60.0, -0.1332047592);
            green = 288.1221695283 * Math.pow(kelvin - 60.0, -0.0755148492);
            blue = 255.0;
        }

        return store.set(
                (float) (Math.max(0.0, Math.min(255.0, red)) / 255.0),
                (float) (Math.max(0.0, Math.min(255.0, green)) / 255.0),
                (float) (Math.max(0.0, Math.min(255.0, blue)) / 255.0));
    }

    /**
     * Checks if this light is visible to the camera.
     *
     * @param frustum The frustum to check against
     * @return Whether this light is visible
     */
    public abstract boolean isVisible(CullFrustum frustum);

    /**
     * Sets the light position/rotation to be the same as the specified camera.
     *
     * @param camera The camera to set relative to
     */
    public LightData setTo(Camera camera) {
        return this;
    }

    /**
     * @return The type of light this is
     */
    public abstract LightTypeRegistry.LightType<?> getType();

}
