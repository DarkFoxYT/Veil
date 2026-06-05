package com.mojang.blaze3d.vertex;

import foundry.veil.ext.VertexBufferExtension;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.lwjgl.system.NativeResource;

public class VertexBuffer implements NativeResource, VertexBufferExtension {

    public VertexBuffer() {
    }

    public VertexBuffer(Usage usage) {
    }

    public static void unbind() {
    }

    public void bind() {
    }

    public void draw() {
    }

    public void drawWithShader(Matrix4f modelViewMatrix, Matrix4f projectionMatrix, ShaderInstance shader) {
        shader.apply();
        this.draw();
    }

    @Override
    public void veil$drawInstanced(int instances) {
    }

    @Override
    public void veil$drawIndirect(long indirect, int drawCount, int stride) {
    }

    @Override
    public int veil$getIndexCount() {
        return 0;
    }

    @Override
    public void free() {
    }

    @Override
    public void close() {
        this.free();
    }

    public enum Usage {
        STATIC,
        DYNAMIC
    }
}
