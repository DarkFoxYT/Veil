package com.mojang.blaze3d.shaders;

import org.lwjgl.system.NativeResource;

public class Program implements NativeResource {

    protected final Type type;
    protected final int id;
    protected final String name;

    public Program(Type type, int id, String name) {
        this.type = type;
        this.id = id;
        this.name = name;
    }

    public int getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public Type getType() {
        return this.type;
    }

    public void attachToShader(Shader shader) {
    }

    @Override
    public void free() {
    }

    @Override
    public void close() {
        this.free();
    }

    public enum Type {
        VERTEX(".vsh"),
        FRAGMENT(".fsh");

        private final String extension;

        Type(String extension) {
            this.extension = extension;
        }

        public String getExtension() {
            return this.extension;
        }

        public String getName() {
            return this.name().toLowerCase(java.util.Locale.ROOT);
        }
    }
}
