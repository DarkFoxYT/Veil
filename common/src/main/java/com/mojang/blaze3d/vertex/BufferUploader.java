package com.mojang.blaze3d.vertex;

public final class BufferUploader {

    public static VertexBuffer lastImmediateBuffer;

    private BufferUploader() {
    }

    public static void invalidate() {
        lastImmediateBuffer = null;
    }
}
