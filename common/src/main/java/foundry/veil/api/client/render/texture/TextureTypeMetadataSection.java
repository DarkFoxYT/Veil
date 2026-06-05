package foundry.veil.api.client.render.texture;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

public record TextureTypeMetadataSection(TextureType type) {

    public static final Serializer SERIALIZER = new Serializer();
    public static final Codec<TextureTypeMetadataSection> CODEC = Codec.STRING.optionalFieldOf("type", "2d")
            .flatXmap(type -> parseType(type).map(TextureTypeMetadataSection::new), section -> DataResult.success(typeName(section.type())))
            .codec();
    public static final MetadataSectionType<TextureTypeMetadataSection> TYPE = new MetadataSectionType<>("veil:texture_type", CODEC);

    public enum TextureType {
        TEXTURE_2D,
        TEXTURE_2D_ARRAY,
        TEXTURE_CUBE_MAP;

        private static final TextureType[] VALUES = TextureType.values();
    }

    public static class Serializer implements MetadataSectionSerializer<TextureTypeMetadataSection> {

        @Override
        public TextureTypeMetadataSection fromJson(@NotNull JsonObject json) {
            String type = GsonHelper.getAsString(json, "type", "2d");
            return new TextureTypeMetadataSection(parseTypeOrThrow(type));
        }

        @Override
        public @NotNull String getMetadataSectionName() {
            return "veil:texture_type";
        }
    }

    private static DataResult<TextureType> parseType(String type) {
        for (TextureType value : TextureType.VALUES) {
            if (typeName(value).equalsIgnoreCase(type)) {
                return DataResult.success(value);
            }
        }
        return DataResult.error(() -> "Unknown texture type: " + type + ". Expected one of " + Arrays.stream(TextureType.VALUES)
                .map(TextureTypeMetadataSection::typeName)
                .collect(Collectors.joining(", ")));
    }

    private static TextureType parseTypeOrThrow(String type) {
        for (TextureType value : TextureType.VALUES) {
            if (typeName(value).equalsIgnoreCase(type)) {
                return value;
            }
        }
        throw new JsonSyntaxException("Unknown texture type: " + type + ". Expected one of " + Arrays.stream(TextureType.VALUES)
                .map(TextureTypeMetadataSection::typeName)
                .collect(Collectors.joining(", ")));
    }

    private static String typeName(TextureType type) {
        return type.name().substring(8).toLowerCase(Locale.ROOT);
    }
}
