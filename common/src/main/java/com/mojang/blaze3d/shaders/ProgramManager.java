package com.mojang.blaze3d.shaders;

import org.jetbrains.annotations.Nullable;

public final class ProgramManager {

    private ProgramManager() {
    }

    public static int createProgram() {
        return 0;
    }

    public static void glUseProgram(int program) {
    }

    public static void linkShader(Shader shader) {
    }

    public static void releaseProgram(Shader shader) {
    }

    public static @Nullable Program getProgram(Program.Type type, String name) {
        return null;
    }
}
