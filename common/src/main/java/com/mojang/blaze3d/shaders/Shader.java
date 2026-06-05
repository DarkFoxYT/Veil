package com.mojang.blaze3d.shaders;

import com.mojang.blaze3d.vertex.VertexFormat;
import org.jetbrains.annotations.Nullable;

public interface Shader extends AutoCloseable {

    void apply();

    void clear();

    @Nullable
    Uniform getUniform(String name);

    default Uniform safeGetUniform(String name) {
        Uniform uniform = this.getUniform(name);
        return uniform != null ? uniform : Uniform.DUMMY;
    }

    void attachToProgram();

    VertexFormat getVertexFormat();

    @Override
    void close();
}
