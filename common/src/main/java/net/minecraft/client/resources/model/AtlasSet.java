package net.minecraft.client.resources.model;

import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.Map;

public class AtlasSet {

    private final Map<Identifier, AtlasEntry> atlases = Collections.emptyMap();

    public Map<Identifier, AtlasEntry> getAtlases() {
        return this.atlases;
    }

    public record AtlasEntry(TextureAtlas atlas, Identifier atlasInfoLocation) {
    }
}
