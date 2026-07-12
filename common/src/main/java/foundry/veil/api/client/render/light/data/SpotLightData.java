package foundry.veil.api.client.render.light.data;

import foundry.veil.api.client.color.Colorc;
import foundry.veil.api.client.editor.EditorAttributeProvider;
import foundry.veil.api.client.registry.LightTypeRegistry;
import foundry.veil.api.client.render.CullFrustum;
import foundry.veil.api.client.render.light.DDALightData;
import imgui.ImGui;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Represents a cone light emitted from a point in space.
 *
 * @since 4.0.0
 */
public class SpotLightData extends LightData implements DDALightData, EditorAttributeProvider {

    protected final Vector3d position;
    protected final Vector3f direction;
    protected float range;
    protected float falloff;
    protected LightFalloff falloffType;
    protected float innerConeAngle;
    protected float outerConeAngle;
    protected float specularStrength;
    protected float godRayStrength;
    protected float shadowIntensity;
    protected boolean occlusionEnabled;

    public SpotLightData() {
        this.position = new Vector3d();
        this.direction = new Vector3f(0.0F, -1.0F, 0.0F);
        this.range = 8.0F;
        this.falloff = 2.0F;
        this.falloffType = LightFalloff.SMOOTH;
        this.innerConeAngle = (float) Math.toRadians(22.0);
        this.outerConeAngle = (float) Math.toRadians(35.0);
        this.specularStrength = 0.22F;
        this.godRayStrength = 1.0F;
        this.shadowIntensity = 1.0F;
        this.occlusionEnabled = true;
    }

    /**
     * @return The XYZ position of this light in the world.
     */
    public Vector3dc getPosition() {
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
     * @return The normalized direction this spotlight emits toward.
     */
    public Vector3fc getDirection() {
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
     * @return The maximum distance this light can travel.
     */
    public float getRange() {
        return this.range;
    }

    /**
     * @return The curve strength used by deferred attenuation.
     */
    public float getFalloff() {
        return this.falloff;
    }

    /**
     * @return The deferred attenuation mode.
     */
    public LightFalloff getFalloffType() {
        return this.falloffType;
    }

    /**
     * @return The full-strength cone angle in radians.
     */
    public float getInnerConeAngle() {
        return this.innerConeAngle;
    }

    /**
     * @return The outer cone angle in radians where the light reaches zero.
     */
    public float getOuterConeAngle() {
        return this.outerConeAngle;
    }

    /**
     * @return The amount of specular response this light contributes.
     */
    public float getSpecularStrength() {
        return this.specularStrength;
    }

    /**
     * @return The amount of screen-space god-ray/scatter response this light contributes.
     */
    public float getGodRayStrength() {
        return this.godRayStrength;
    }

    /**
     * @return How strongly voxel occlusion darkens this light.
     */
    @Override
    public float getShadowIntensity() {
        return this.shadowIntensity;
    }

    @Override
    public boolean isOcclusionEnabled() {
        return this.occlusionEnabled;
    }

    public SpotLightData setPosition(Vector3dc pos) {
        return this.setPosition(pos.x(), pos.y(), pos.z());
    }

    public SpotLightData setPosition(Vector3fc pos) {
        return this.setPosition(pos.x(), pos.y(), pos.z());
    }

    public SpotLightData setPosition(Position pos) {
        return this.setPosition(pos.x(), pos.y(), pos.z());
    }

    public SpotLightData setPosition(double x, double y, double z) {
        if (Double.compare(this.position.x, x) == 0 &&
                Double.compare(this.position.y, y) == 0 &&
                Double.compare(this.position.z, z) == 0) {
            return this;
        }
        this.position.set(x, y, z);
        this.markDirty();
        return this;
    }

    public SpotLightData setDirection(Vector3fc direction) {
        return this.setDirection(direction.x(), direction.y(), direction.z());
    }

    public SpotLightData setDirection(float x, float y, float z) {
        if (Float.compare(this.direction.x, x) == 0 &&
                Float.compare(this.direction.y, y) == 0 &&
                Float.compare(this.direction.z, z) == 0) {
            return this;
        }

        if (x * x + y * y + z * z < 1.0E-6F) {
            return this;
        }

        this.direction.set(x, y, z).normalize();
        this.markDirty();
        return this;
    }

    public SpotLightData setRotation(float yaw, float pitch) {
        float yawRad = (float) Math.toRadians(yaw);
        float pitchRad = (float) Math.toRadians(pitch);
        float pitchCos = (float) Math.cos(pitchRad);
        return this.setDirection(
                (float) -Math.sin(yawRad) * pitchCos,
                (float) -Math.sin(pitchRad),
                (float) Math.cos(yawRad) * pitchCos);
    }

    public SpotLightData setRange(float range) {
        range = Math.max(0.0F, range);
        if (Float.compare(this.range, range) == 0) {
            return this;
        }
        this.range = range;
        this.markDirty();
        return this;
    }

    public SpotLightData setFalloff(float falloff) {
        falloff = Math.max(0.001F, falloff);
        if (Float.compare(this.falloff, falloff) == 0) {
            return this;
        }
        this.falloff = falloff;
        this.markDirty();
        return this;
    }

    public SpotLightData setFalloffType(LightFalloff falloffType) {
        if (this.falloffType == falloffType) {
            return this;
        }
        this.falloffType = falloffType;
        this.markDirty();
        return this;
    }

    public SpotLightData setInnerConeAngle(float innerConeAngle) {
        return this.setConeAngles(innerConeAngle, Math.max(innerConeAngle, this.outerConeAngle));
    }

    public SpotLightData setOuterConeAngle(float outerConeAngle) {
        return this.setConeAngles(Math.min(this.innerConeAngle, outerConeAngle), outerConeAngle);
    }

    public SpotLightData setConeAngles(float innerConeAngle, float outerConeAngle) {
        innerConeAngle = Math.max(0.0F, Math.min((float) Math.PI, innerConeAngle));
        outerConeAngle = Math.max(innerConeAngle + 0.001F, Math.min((float) Math.PI, outerConeAngle));
        if (Float.compare(this.innerConeAngle, innerConeAngle) == 0 &&
                Float.compare(this.outerConeAngle, outerConeAngle) == 0) {
            return this;
        }
        this.innerConeAngle = innerConeAngle;
        this.outerConeAngle = outerConeAngle;
        this.markDirty();
        return this;
    }

    public SpotLightData setSpecularStrength(float specularStrength) {
        specularStrength = Math.max(0.0F, specularStrength);
        if (Float.compare(this.specularStrength, specularStrength) == 0) {
            return this;
        }
        this.specularStrength = specularStrength;
        this.markDirty();
        return this;
    }

    public SpotLightData setGodRayStrength(float godRayStrength) {
        godRayStrength = Math.max(0.0F, godRayStrength);
        if (Float.compare(this.godRayStrength, godRayStrength) == 0) {
            return this;
        }
        this.godRayStrength = godRayStrength;
        this.markDirty();
        return this;
    }

    public SpotLightData setShadowIntensity(float shadowIntensity) {
        shadowIntensity = Math.max(0.0F, shadowIntensity);
        if (Float.compare(this.shadowIntensity, shadowIntensity) == 0) {
            return this;
        }
        this.shadowIntensity = shadowIntensity;
        this.markDirty();
        return this;
    }

    public SpotLightData setOcclusionEnabled(boolean occlusionEnabled) {
        if (this.occlusionEnabled == occlusionEnabled) {
            return this;
        }
        this.occlusionEnabled = occlusionEnabled;
        this.markDirty();
        return this;
    }

    /**
     * Applies cheap defaults for decorative cone lights where a visible glow matters more than voxel shadows.
     *
     * @return This light for chaining
     */
    public SpotLightData setDecorativeDefaults() {
        this.setOcclusionEnabled(false);
        this.setShadowIntensity(0.0F);
        this.setGodRayStrength(0.0F);
        this.setSpecularStrength(Math.min(this.specularStrength, 0.06F));
        return this;
    }

    @Override
    public SpotLightData setColor(Vector3fc color) {
        super.setColor(color);
        return this;
    }

    @Override
    public SpotLightData setColor(Colorc color) {
        this.setColor(color.red(), color.green(), color.blue());
        return this;
    }

    @Override
    public SpotLightData setColor(float red, float green, float blue) {
        super.setColor(red, green, blue);
        return this;
    }

    @Override
    public SpotLightData setColor(int color) {
        super.setColor(color);
        return this;
    }

    @Override
    public SpotLightData setBrightness(float brightness) {
        super.setBrightness(brightness);
        return this;
    }

    @Override
    public SpotLightData setTemperature(float temperature) {
        super.setTemperature(temperature);
        return this;
    }

    @Override
    public boolean isVisible(CullFrustum frustum) {
        return frustum.testSphere(this.position, this.range);
    }

    @Override
    public SpotLightData setTo(Camera camera) {
        Vec3 pos = camera.getPosition();
        this.setPosition(pos.x, pos.y, pos.z);
        this.setDirection(camera.getLookVector());
        return this;
    }

    @Override
    public LightTypeRegistry.LightType<?> getType() {
        return LightTypeRegistry.SPOT.get();
    }

    @Override
    public boolean rendersCompleteEditor() {
        return true;
    }

    @Override
    public void renderImGuiAttributes() {
        if (ImGui.checkbox("Casts shadow (voxel shadows)", this.occlusionEnabled)) {
            this.setOcclusionEnabled(!this.occlusionEnabled);
        }

        this.renderPositionControls();
        if (ImGui.button("Set to spawn point")) {
            this.setToSpawnPoint();
        }

        this.renderColorControls();
        this.renderFloatControl("Temperature (K)", this.getTemperature(), 25.0F, 1000.0F, this::setTemperature);
        this.renderFloatControl("Intensity", this.getBrightness(), 0.02F, 0.0F, this::setBrightness);
        this.renderFloatControl("Range (distance)", this.range, 0.02F, 0.0F, this::setRange);
        this.renderRotationControls();
        this.renderConeControls();
        this.renderFloatControl("Specular strength", this.specularStrength, 0.02F, 0.0F, this::setSpecularStrength);
        this.renderFloatControl("God-ray strength", this.godRayStrength, 0.02F, 0.0F, this::setGodRayStrength);
        this.renderFloatControl("Shadow intensity", this.shadowIntensity, 0.02F, 0.0F, this::setShadowIntensity);
        this.renderFloatControl("Falloff strength", this.falloff, 0.02F, 0.001F, this::setFalloff);
        this.renderFalloffControl();
    }

    private void renderPositionControls() {
        double[] editX = new double[]{this.position.x()};
        double[] editY = new double[]{this.position.y()};
        double[] editZ = new double[]{this.position.z()};

        float totalWidth = ImGui.calcItemWidth();
        float spacing = ImGui.getStyle().getItemInnerSpacingX();
        ImGui.pushItemWidth(totalWidth / 3.0F - spacing * 0.58F);
        if (ImGui.dragScalar("##x", editX, 0.02F)) {
            this.position.x = editX[0];
            this.markDirty();
        }
        ImGui.sameLine(0, spacing);
        if (ImGui.dragScalar("##y", editY, 0.02F)) {
            this.position.y = editY[0];
            this.markDirty();
        }
        ImGui.sameLine(0, spacing);
        if (ImGui.dragScalar("##z", editZ, 0.02F)) {
            this.position.z = editZ[0];
            this.markDirty();
        }
        ImGui.popItemWidth();
        label("Position");
    }

    private void renderColorControls() {
        float[] editLightColor = new float[]{this.color.red(), this.color.green(), this.color.blue()};
        ImGui.pushItemWidth(ImGui.calcItemWidth());
        if (ImGui.colorEdit3("##color", editLightColor)) {
            this.setColor(editLightColor[0], editLightColor[1], editLightColor[2]);
        }
        ImGui.popItemWidth();
        label("Color");
    }

    private void renderRotationControls() {
        float yaw = (float) Math.toDegrees(Math.atan2(-this.direction.x, this.direction.z));
        float pitch = (float) Math.toDegrees(Math.asin(-this.direction.y));
        float[] editYaw = new float[]{yaw};
        float[] editPitch = new float[]{pitch};

        float totalWidth = ImGui.calcItemWidth();
        float spacing = ImGui.getStyle().getItemInnerSpacingX();
        ImGui.pushItemWidth(totalWidth / 2.0F - spacing * 0.5F);
        boolean changed = ImGui.dragScalar("##yaw", editYaw, 0.05F);
        ImGui.sameLine(0, spacing);
        changed |= ImGui.dragScalar("##pitch", editPitch, 0.05F, -89.9F, 89.9F);
        ImGui.popItemWidth();
        if (changed) {
            this.setRotation(editYaw[0], editPitch[0]);
        }
        label("Rotation (yaw/pitch)");
    }

    private void renderConeControls() {
        float[] editInner = new float[]{(float) Math.toDegrees(this.innerConeAngle)};
        float[] editOuter = new float[]{(float) Math.toDegrees(this.outerConeAngle)};

        float totalWidth = ImGui.calcItemWidth();
        float spacing = ImGui.getStyle().getItemInnerSpacingX();
        ImGui.pushItemWidth(totalWidth / 2.0F - spacing * 0.5F);
        boolean changed = ImGui.dragScalar("##innerCone", editInner, 0.05F, 0.0F, 179.0F);
        ImGui.sameLine(0, spacing);
        changed |= ImGui.dragScalar("##outerCone", editOuter, 0.05F, 0.0F, 179.0F);
        ImGui.popItemWidth();
        if (changed) {
            this.setConeAngles((float) Math.toRadians(editInner[0]), (float) Math.toRadians(editOuter[0]));
        }
        label("Cone inner/outer (deg)");
    }

    private void renderFloatControl(String label, float value, float speed, float min, FloatSetter setter) {
        float[] edit = new float[]{value};
        ImGui.pushItemWidth(ImGui.calcItemWidth());
        if (ImGui.dragScalar("##" + label, edit, speed, min)) {
            setter.set(edit[0]);
        }
        ImGui.popItemWidth();
        label(label);
    }

    private void renderFalloffControl() {
        ImGui.pushItemWidth(ImGui.calcItemWidth());
        if (ImGui.beginCombo("##falloff", this.falloffType.getDisplayName())) {
            for (LightFalloff value : LightFalloff.values()) {
                if (ImGui.selectable(value.getDisplayName(), value == this.falloffType)) {
                    this.setFalloffType(value);
                }
            }
            ImGui.endCombo();
        }
        ImGui.popItemWidth();
        label("Falloff");
    }

    private void setToSpawnPoint() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            this.setTo(client.gameRenderer.getMainCamera());
            return;
        }

        BlockPos spawn = client.level.getSharedSpawnPos();
        this.setPosition(spawn.getX() + 0.5, spawn.getY() + 1.0, spawn.getZ() + 0.5);
    }

    private static void label(String label) {
        ImGui.sameLine(0, ImGui.getStyle().getItemInnerSpacingX());
        ImGui.text(label);
    }

    @FunctionalInterface
    private interface FloatSetter {
        void set(float value);
    }
}
