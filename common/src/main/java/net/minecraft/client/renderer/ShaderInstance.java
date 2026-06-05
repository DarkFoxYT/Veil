package net.minecraft.client.renderer;

import com.mojang.blaze3d.shaders.Shader;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4fc;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class ShaderInstance implements Shader {

    public static int lastProgramId = -1;

    protected final String name;
    protected final VertexFormat vertexFormat;
    protected final Map<String, Uniform> uniformMap = new HashMap<>();
    protected int id;

    public ShaderInstance(Function<String, Optional<Resource>> resourceProvider, String name, VertexFormat vertexFormat) throws IOException {
        this.name = name;
        this.vertexFormat = vertexFormat;
    }

    public ShaderInstance(ResourceProvider resourceProvider, Identifier id, VertexFormat vertexFormat) throws IOException {
        this(name -> resourceProvider.getResource(Identifier.parse(name)), id.toString(), vertexFormat);
    }

    @Override
    public void apply() {
    }

    @Override
    public void clear() {
    }

    @Override
    public @Nullable Uniform getUniform(@NotNull String name) {
        return this.uniformMap.get(name);
    }

    public @NotNull Uniform safeGetUniform(@NotNull String name) {
        return this.uniformMap.computeIfAbsent(name, unused -> new Uniform(name, Uniform.UT_INT1, 0, this));
    }

    public void setSampler(@NotNull String name, Object value) {
    }

    public void markDirty() {
    }

    public void setDefaultUniforms(VertexFormat.Mode mode, Matrix4fc modelViewMatrix, Matrix4fc projectionMatrix, Window window) {
    }

    @Override
    public void attachToProgram() {
    }

    @Override
    public @NotNull VertexFormat getVertexFormat() {
        return this.vertexFormat;
    }

    public String getName() {
        return this.name;
    }

    public int getId() {
        return this.id;
    }

    @Override
    public void close() {
    }
}
