package foundry.veil.api.client.property;

import net.minecraft.client.renderer.texture.AbstractTexture;
import org.joml.Matrix3fc;
import org.joml.Matrix4fc;
import org.joml.Vector4fc;

public interface ShaderPropertySink {

    void setFloat(String name, float value);

    void setInt(String name, int value);

    void setVec2(String name, float x, float y);

    void setVec3(String name, float x, float y, float z);

    void setVec4(String name, Vector4fc value);

    void setMat3(String name, Matrix3fc value);

    void setMat4(String name, Matrix4fc value);

    void setSampler(String name, AbstractTexture texture);
}
