package foundry.veil.api.client.render.light.data;

import foundry.veil.api.client.color.Colorc;
import foundry.veil.api.client.editor.EditorAttributeProvider;
import foundry.veil.api.client.registry.LightTypeRegistry;
import foundry.veil.api.client.render.CullFrustum;
import foundry.veil.api.client.render.light.DDALightData;
import foundry.veil.api.client.render.light.InstancedLightData;
import imgui.ImGui;
import net.minecraft.client.Camera;
import net.minecraft.core.Position;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;
import org.joml.*;

import java.lang.Math;
import java.nio.ByteBuffer;

/**
 * Represents a light emitting quad in the world.
 *
 * @since 2.0.0
 */
public class AreaLightData extends LightData implements InstancedLightData, DDALightData, EditorAttributeProvider {

    private static final float MAX_ANGLE_SIZE = (float) (65535.0 / 2.0 / Math.PI);

    protected final Vector3d position;
    protected final Quaternionf orientation;
    private final Matrix4d matrix;
    private final Vector3f temperatureColor;

    protected final Vector2f size;

    protected float angle;
    protected float distance;
    protected float shadowIntensity;
    protected boolean occlusionEnabled;

    public AreaLightData() {
        this.matrix = new Matrix4d();
        this.temperatureColor = new Vector3f();
        this.position = new Vector3d();
        this.orientation = new Quaternionf();

        this.size = new Vector2f(1.0F, 1.0F);

        this.angle = (float) Math.toRadians(45);
        this.distance = 1.0F;
        this.shadowIntensity = 1.0F;
        this.occlusionEnabled = false;
    }

    /**
     * @deprecated No longer used
     */
    @ApiStatus.ScheduledForRemoval(inVersion = "5.0.0")
    @Deprecated
    protected void updateMatrix() {
        Quaternionfc orientation = this.getOrientation();
        this.matrix.rotation(orientation).translate(this.position);
    }

    @Override
    public LightTypeRegistry.LightType<?> getType() {
        return LightTypeRegistry.AREA.get();
    }

    /**
     * @return The XYZ position of this light in the world
     */
    public Vector3d getPosition() {
        return this.position;
    }

    /**
     * Copies this light's position into the specified vector.
     *
     * @param store The vector to store the position in
     * @return The passed in vector
     */
    public Vector3d getPosition(Vector3d store) {
        return store.set(this.position);
    }

    /**
     * @return The current orientation of the light
     */
    public Quaternionf getOrientation() {
        return this.orientation;
    }

    /**
     * Copies this light's orientation into the specified quaternion.
     *
     * @param store The quaternion to store the orientation in
     * @return The passed in quaternion
     */
    public Quaternionf getOrientation(Quaternionf store) {
        return store.set(this.orientation);
    }

    /**
     * Stores this light's orientation as XYZ Euler angles in radians.
     *
     * @param store The vector to store the angles in
     * @return The passed in vector
     */
    public Vector3f getRotationXYZ(Vector3f store) {
        return this.orientation.getEulerAnglesXYZ(store);
    }

    /**
     * @return The size of the light's surface
     */
    public Vector2f getSize() {
        return this.size;
    }

    /**
     * @return The maximum angle of the light from the plane's surface.
     */
    public float getAngle() {
        return this.angle;
    }

    /**
     * @return The maximum distance the light can travel
     */
    public float getDistance() {
        return this.distance;
    }

    @Override
    public boolean isOcclusionEnabled() {
        return this.occlusionEnabled;
    }

    public float getShadowIntensity() {
        return this.shadowIntensity;
    }

    /**
     * Sets the size of the light's surface
     *
     * @param x The length, in blocks, of the light's surface.
     * @param y The width, in blocks, of the light's surface.
     */
    public AreaLightData setSize(double x, double y) {
        if (Float.compare(this.size.x, (float) x) == 0 && Float.compare(this.size.y, (float) y) == 0) {
            return this;
        }
        this.size.set(x, y);
        this.markDirty();
        return this;
    }

    public AreaLightData setPosition(Vector3dc pos) {
        return this.setPosition(pos.x(), pos.y(), pos.z());
    }

    public AreaLightData setPosition(Vector3fc pos) {
        return this.setPosition(pos.x(), pos.y(), pos.z());
    }

    public AreaLightData setPosition(Position pos) {
        return this.setPosition(pos.x(), pos.y(), pos.z());
    }

    public AreaLightData setPosition(double x, double y, double z) {
        if (Double.compare(this.position.x, x) == 0 &&
                Double.compare(this.position.y, y) == 0 &&
                Double.compare(this.position.z, z) == 0) {
            return this;
        }
        this.position.set(x, y, z);
        this.markDirty();
        return this;
    }

    public AreaLightData setOrientation(Quaternionfc orientation) {
        if (Float.compare(this.orientation.x(), orientation.x()) == 0 &&
                Float.compare(this.orientation.y(), orientation.y()) == 0 &&
                Float.compare(this.orientation.z(), orientation.z()) == 0 &&
                Float.compare(this.orientation.w(), orientation.w()) == 0) {
            return this;
        }
        this.orientation.set(orientation);
        this.markDirty();
        return this;
    }

    public AreaLightData setRotation(float xRot, float yRot, float zRot) {
        return this.setOrientation(new Quaternionf().rotationXYZ(xRot, yRot, zRot));
    }

    /**
     * Sets the maximum angle the light can influence.
     *
     * @param angle The maximum angle of the light's influence in radians
     */
    public AreaLightData setAngle(float angle) {
        if (Float.compare(this.angle, angle) == 0) {
            return this;
        }
        this.angle = angle;
        this.markDirty();
        return this;
    }

    /**
     * Sets the maximum distance the light can influence.
     *
     * @param distance The maximum area of influence for the light
     */
    public AreaLightData setDistance(float distance) {
        if (Float.compare(this.distance, distance) == 0) {
            return this;
        }
        this.distance = distance;
        this.markDirty();
        return this;
    }

    public AreaLightData setOcclusionEnabled(boolean occlusionEnabled) {
        if (this.occlusionEnabled == occlusionEnabled) {
            return this;
        }
        this.occlusionEnabled = occlusionEnabled;
        this.markDirty();
        return this;
    }

    public AreaLightData setShadowIntensity(float shadowIntensity) {
        shadowIntensity = Math.max(0.0F, shadowIntensity);
        if (Float.compare(this.shadowIntensity, shadowIntensity) == 0) {
            return this;
        }
        this.shadowIntensity = shadowIntensity;
        this.markDirty();
        return this;
    }

    @Override
    public AreaLightData setColor(Vector3fc color) {
        super.setColor(color);
        return this;
    }

    @Override
    public AreaLightData setColor(Colorc color) {
        this.setColor(color.red(), color.green(), color.blue());
        return this;
    }

    @Override
    public AreaLightData setColor(float red, float green, float blue) {
        super.setColor(red, green, blue);
        return this;
    }

    @Override
    public AreaLightData setColor(int color) {
        super.setColor(color);
        return this;
    }

    @Override
    public AreaLightData setBrightness(float brightness) {
        super.setBrightness(brightness);
        return this;
    }

    @Override
    public AreaLightData setTemperature(float temperature) {
        super.setTemperature(temperature);
        return this;
    }

    @Override
    public void store(ByteBuffer buffer) {
        this.matrix.identity().rotation(this.orientation).translate(this.position).getFloats(buffer.position(), buffer);
        buffer.position(buffer.position() + Float.BYTES * 16);

        this.getTemperatureColor(this.temperatureColor);
        buffer.putFloat(this.color.red() * this.brightness * this.temperatureColor.x());
        buffer.putFloat(this.color.green() * this.brightness * this.temperatureColor.y());
        buffer.putFloat(this.color.blue() * this.brightness * this.temperatureColor.z());

        this.size.get(buffer.position(), buffer);
        buffer.position(buffer.position() + Float.BYTES * 2);

        buffer.putShort((short) Mth.clamp((int) (this.angle * MAX_ANGLE_SIZE), 0, 65535));
        buffer.putFloat(this.distance);
        buffer.putFloat(this.occlusionEnabled ? this.shadowIntensity : 0.0F);
    }

    @Override
    public boolean isVisible(CullFrustum frustum) {
        float radius = Math.max(this.size.x, this.size.y) + this.distance;
        return frustum.testAab(
                this.position.x - radius,
                this.position.y - radius,
                this.position.z - radius,
                this.position.x + radius,
                this.position.y + radius,
                this.position.z + radius);
    }

    @Override
    public AreaLightData setTo(Camera camera) {
        Vec3 pos = camera.getPosition();
        this.setPosition(pos.x, pos.y, pos.z);
        this.setOrientation(new Quaternionf().lookAlong(camera.getLookVector().mul(-1), camera.getUpVector()));
        return this;
    }

    @Override
    public void renderImGuiAttributes() {
        Vector3f orientationAngles = new Quaternionf(this.orientation).normalize().getEulerAnglesXYZ(new Vector3f());

        float[] editSize = new float[]{this.size.x(), this.size.y()};

        double[] editX = new double[]{this.position.x()};
        double[] editY = new double[]{this.position.y()};
        double[] editZ = new double[]{this.position.z()};

        float[] editXRot = new float[]{orientationAngles.x()};
        float[] editYRot = new float[]{orientationAngles.y()};
        float[] editZRot = new float[]{orientationAngles.z()};

        float[] editAngle = new float[]{this.angle};
        float[] editDistance = new float[]{this.distance};

        if (ImGui.dragFloat2("size", editSize, 0.02F, 0.0001F)) {
            this.setSize(editSize[0], editSize[1]);
        }

        float totalWidth = ImGui.calcItemWidth();
        ImGui.pushItemWidth(totalWidth / 3.0F - (ImGui.getStyle().getItemInnerSpacingX() * 0.58F));
        if (ImGui.dragScalar("##x", editX, 0.02F)) {
            this.position.x = editX[0];
            this.markDirty();
        }
        ImGui.sameLine(0, ImGui.getStyle().getItemInnerSpacingX());
        if (ImGui.dragScalar("##y", editY, 0.02F)) {
            this.position.y = editY[0];
            this.markDirty();
        }
        ImGui.sameLine(0, ImGui.getStyle().getItemInnerSpacingX());
        if (ImGui.dragScalar("##z", editZ, 0.02F)) {
            this.position.z = editZ[0];
            this.markDirty();
        }

        ImGui.popItemWidth();
        ImGui.sameLine(0, ImGui.getStyle().getItemInnerSpacingX());
        ImGui.text("position");

        ImGui.pushItemWidth(totalWidth / 3.0F - (ImGui.getStyle().getItemInnerSpacingX() * 0.58F));
        if (ImGui.sliderAngle("##xrot", editXRot)) {
            this.setRotation(editXRot[0], orientationAngles.y(), orientationAngles.z());
        }
        ImGui.sameLine(0, ImGui.getStyle().getItemInnerSpacingX());
        if (ImGui.sliderAngle("##yrot", editYRot)) {
            this.setRotation(orientationAngles.x(), editYRot[0], orientationAngles.z());
        }
        ImGui.sameLine(0, ImGui.getStyle().getItemInnerSpacingX());
        if (ImGui.sliderAngle("##zrot", editZRot)) {
            this.setRotation(orientationAngles.x(), orientationAngles.y(), editZRot[0]);
        }

        ImGui.popItemWidth();
        ImGui.sameLine(0, ImGui.getStyle().getItemInnerSpacingX());
        ImGui.text("orientation");

        if (ImGui.sliderAngle("##angle", editAngle, 0.1F, 180.0F, "%.1f")) {
            this.setAngle(editAngle[0]);
        }
        ImGui.sameLine(0, ImGui.getStyle().getItemInnerSpacingX());
        ImGui.text("angle");

        if (ImGui.dragScalar("distance", editDistance, 0.02F, 0.0F)) {
            this.setDistance(editDistance[0]);
        }

        if (ImGui.checkbox("Occluded", this.occlusionEnabled)) {
            this.occlusionEnabled = !this.occlusionEnabled;
            this.markDirty();
        }
        float[] editShadowIntensity = new float[]{this.shadowIntensity};
        if (ImGui.dragScalar("Shadow intensity", editShadowIntensity, 0.02F, 0.0F)) {
            this.setShadowIntensity(editShadowIntensity[0]);
        }
    }
}
