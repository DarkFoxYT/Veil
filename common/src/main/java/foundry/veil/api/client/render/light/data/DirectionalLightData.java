package foundry.veil.api.client.render.light.data;

import foundry.veil.api.client.color.Colorc;
import foundry.veil.api.client.editor.EditorAttributeProvider;
import foundry.veil.api.client.registry.LightTypeRegistry;
import foundry.veil.api.client.render.CullFrustum;
import imgui.ImGui;
import net.minecraft.client.Camera;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Represents a light where all rays come from the same direction everywhere. (The sun)
 *
 * @since 2.0.0
 */
public class DirectionalLightData extends LightData implements EditorAttributeProvider {

    protected final Vector3f direction;

    public DirectionalLightData() {
        this.direction = new Vector3f(0.0F, -1.0F, 0.0F);
    }

    /**
     * @return The direction this light is facing
     */
    public Vector3f getDirection() {
        return this.direction;
    }

    /**
     * Copies this light's direction into the specified vector.
     *
     * @param store The vector to store the direction in
     * @return The passed in vector
     */
    public Vector3f getDirection(Vector3f store) {
        return store.set(this.direction);
    }

    /**
     * Sets the direction of this light.
     *
     * @param direction The new direction
     */
    public DirectionalLightData setDirection(Vector3fc direction) {
        return this.setDirection(direction.x(), direction.y(), direction.z());
    }

    /**
     * Sets the direction of this light.
     *
     * @param x The new x direction
     * @param y The new y direction
     * @param z The new z direction
     */
    public DirectionalLightData setDirection(float x, float y, float z) {
        if (Float.compare(this.direction.x, x) == 0 &&
                Float.compare(this.direction.y, y) == 0 &&
                Float.compare(this.direction.z, z) == 0) {
            return this;
        }
        this.direction.set(x, y, z);
        this.markDirty();
        return this;
    }

    @Override
    public DirectionalLightData setColor(Vector3fc color) {
        super.setColor(color);
        return this;
    }

    @Override
    public DirectionalLightData setColor(Colorc color) {
        this.setColor(color.red(), color.green(), color.blue());
        return this;
    }

    @Override
    public DirectionalLightData setColor(float red, float green, float blue) {
        super.setColor(red, green, blue);
        return this;
    }

    @Override
    public DirectionalLightData setColor(int color) {
        super.setColor(color);
        return this;
    }

    @Override
    public DirectionalLightData setBrightness(float brightness) {
        super.setBrightness(brightness);
        return this;
    }

    @Override
    public boolean isVisible(CullFrustum frustum) {
        return true;
    }

    @Override
    public DirectionalLightData setTo(Camera camera) {
        return this.setDirection(camera.getLookVector());
    }

    @Override
    public LightTypeRegistry.LightType<?> getType() {
        return LightTypeRegistry.DIRECTIONAL.get();
    }

    @Override
    public void renderImGuiAttributes() {
        float[] editDirection = new float[]{this.direction.x(), this.direction.y(), this.direction.z()};

        if (ImGui.sliderFloat3("##direction", editDirection, -1.0F, 1.0F)) {
            this.setDirection(editDirection[0], editDirection[1], editDirection[2]);
        }
        ImGui.sameLine(0, ImGui.getStyle().getItemInnerSpacingX());
        ImGui.text("direction");
    }
}
