package net.minecraft.client.renderer;

public class EffectInstance {

    public static int lastProgramId = -1;

    private final String name;
    private final int id;

    public EffectInstance() {
        this("", 0);
    }

    public EffectInstance(String name, int id) {
        this.name = name;
        this.id = id;
    }

    public String getName() {
        return this.name;
    }

    public int getId() {
        return this.id;
    }
}
