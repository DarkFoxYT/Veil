package com.mojang.blaze3d.shaders;

import org.jetbrains.annotations.NotNull;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.NativeResource;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public class Uniform implements NativeResource {

    public static final int UT_INT1 = 0;
    public static final int UT_INT2 = 1;
    public static final int UT_INT3 = 2;
    public static final int UT_INT4 = 3;
    public static final int UT_FLOAT1 = 4;
    public static final int UT_FLOAT2 = 5;
    public static final int UT_FLOAT3 = 6;
    public static final int UT_FLOAT4 = 7;
    public static final int UT_MAT2 = 8;
    public static final int UT_MAT3 = 9;
    public static final int UT_MAT4 = 10;

    public static final Uniform DUMMY = new Uniform("dummy", UT_INT1, 0, null);

    protected final String name;
    private final int type;
    private final IntBuffer intBuffer;
    private final FloatBuffer floatBuffer;
    protected int location = -1;

    public Uniform(String name, int type, int count, Object owner) {
        this.name = name;
        this.type = type;
        int size = Math.max(1, count * 16);
        this.intBuffer = IntBuffer.allocate(size);
        this.floatBuffer = FloatBuffer.allocate(size);
    }

    public static int glGetUniformLocation(int program, CharSequence name) {
        return org.lwjgl.opengl.GL20C.glGetUniformLocation(program, name);
    }

    public void setLocation(int location) {
        this.location = location;
    }

    public int getLocation() {
        return this.location;
    }

    public int getType() {
        return this.type;
    }

    public IntBuffer getIntBuffer() {
        return this.intBuffer;
    }

    public FloatBuffer getFloatBuffer() {
        return this.floatBuffer;
    }

    public void set(int index, float value) {
    }

    public void set(float value) {
    }

    public void set(float x, float y) {
    }

    public void set(float x, float y, float z) {
    }

    public void set(float x, float y, float z, float w) {
    }

    public void set(@NotNull Vector3f value) {
        this.set(value.x, value.y, value.z);
    }

    public void set(@NotNull Vector4f value) {
        this.set(value.x, value.y, value.z, value.w);
    }

    public void setSafe(float x, float y, float z, float w) {
        this.set(x, y, z, w);
    }

    public void set(int value) {
    }

    public void set(int x, int y) {
    }

    public void set(int x, int y, int z) {
    }

    public void set(int x, int y, int z, int w) {
    }

    public void setSafe(int x, int y, int z, int w) {
        this.set(x, y, z, w);
    }

    public void set(float[] values) {
    }

    public void setMat2x2(float m00, float m01, float m10, float m11) {
    }

    public void setMat2x3(float m00, float m01, float m02, float m10, float m11, float m12) {
    }

    public void setMat2x4(float m00, float m01, float m02, float m03, float m10, float m11, float m12, float m13) {
    }

    public void setMat3x2(float m00, float m01, float m10, float m11, float m20, float m21) {
    }

    public void setMat3x3(float m00, float m01, float m02, float m10, float m11, float m12, float m20, float m21, float m22) {
    }

    public void setMat3x4(float m00, float m01, float m02, float m03, float m10, float m11, float m12, float m13, float m20, float m21, float m22, float m23) {
    }

    public void setMat4x2(float m00, float m01, float m02, float m03, float m10, float m11, float m12, float m13) {
    }

    public void setMat4x3(float m00, float m01, float m02, float m03, float m10, float m11, float m12, float m13, float m20, float m21, float m22, float m23) {
    }

    public void setMat4x4(float m00, float m01, float m02, float m03, float m10, float m11, float m12, float m13, float m20, float m21, float m22, float m23, float m30, float m31, float m32, float m33) {
    }

    public void set(@NotNull Matrix3f value) {
    }

    public void set(@NotNull Matrix4f value) {
    }

    public void upload() {
    }

    @Override
    public void free() {
    }

    @Override
    public void close() {
        this.free();
    }
}
