package foundry.veil.impl.client.render.light;

import foundry.veil.Veil;
import foundry.veil.api.client.color.Colorc;
import foundry.veil.api.client.render.light.data.PointLightData;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;
import org.joml.Vector3dc;
import org.joml.Vector3f;

import java.nio.ByteBuffer;

@ApiStatus.Internal
public class DeferredPointLightRenderer extends DeferredLightRenderer<PointLightData> {

    private static final ResourceLocation SHADER = Veil.veilPath("light/point");
    private static final String BLOCK_NAME = "VeilPointLights";
    private static final int LIGHT_SIZE = Float.BYTES * 12;

    private final Vector3f temperatureColor;

    public DeferredPointLightRenderer() {
        super(SHADER, BLOCK_NAME, LIGHT_SIZE);
        this.temperatureColor = new Vector3f();
    }

    @Override
    protected void store(PointLightData light, ByteBuffer buffer) {
        Vector3dc position = light.getPosition();
        Colorc color = light.getColor();
        float brightness = light.getBrightness();
        light.getTemperatureColor(this.temperatureColor);

        buffer.putFloat((float) position.x());
        buffer.putFloat((float) position.y());
        buffer.putFloat((float) position.z());
        buffer.putFloat(Math.max(0.0F, light.getRadius()));

        buffer.putFloat(color.red() * brightness * this.temperatureColor.x());
        buffer.putFloat(color.green() * brightness * this.temperatureColor.y());
        buffer.putFloat(color.blue() * brightness * this.temperatureColor.z());
        buffer.putFloat(Math.max(0.001F, light.getFalloff()));

        buffer.putFloat(Math.max(0.0F, light.getSpecularStrength()));
        buffer.putFloat(light.isOcclusionEnabled() ? Math.max(0.0F, light.getShadowIntensity()) : 0.0F);
        buffer.putFloat(Math.max(0.0F, light.getGodRayStrength()));
        buffer.putFloat(light.getFalloffType().ordinal());
    }
}
