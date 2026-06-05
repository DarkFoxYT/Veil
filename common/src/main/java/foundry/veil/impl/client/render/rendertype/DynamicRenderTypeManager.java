package foundry.veil.impl.client.render.rendertype;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import foundry.veil.Veil;
import foundry.veil.api.client.render.rendertype.layer.CompositeRenderTypeData;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@ApiStatus.Internal
public class DynamicRenderTypeManager extends SimplePreparableReloadListener<Map<Identifier, byte[]>> {

    private static final FileToIdConverter CONVERTER = FileToIdConverter.json("pinwheel/rendertypes");

    private final Map<Identifier, RenderTypeCache> renderTypes = new Object2ObjectArrayMap<>();

    @Contract(pure = true)
    public @Nullable RenderType get(Identifier id, Object... params) {
        RenderTypeCache cache = this.renderTypes.get(id);
        if (cache == null) {
            return null;
        }

        return cache.get(params);
    }

    @Override
    protected @NotNull Map<Identifier, byte[]> prepare(@NotNull ResourceManager resourceManager, @NotNull ProfilerFiller profilerFiller) {
        Map<Identifier, byte[]> data = new HashMap<>();

        Map<Identifier, Resource> resources = CONVERTER.listMatchingResources(resourceManager);
        for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
            Identifier location = entry.getKey();
            Identifier id = CONVERTER.fileToId(location);

            try (InputStream stream = entry.getValue().open()) {
                data.put(id, stream.readAllBytes());
            } catch (Exception e) {
                Veil.LOGGER.error("Couldn't read data file {} from {}", id, location, e);
            }
        }

        return data;
    }

    @Override
    protected void apply(Map<Identifier, byte[]> fileData, ResourceManager resourceManager, ProfilerFiller profilerFiller) {
        Map<Identifier, RenderTypeCache> renderTypes = new HashMap<>();

        for (Map.Entry<Identifier, byte[]> entry : fileData.entrySet()) {
            Identifier id = entry.getKey();

            try (Reader reader = new InputStreamReader(new ByteArrayInputStream(entry.getValue()))) {
                JsonElement element = JsonParser.parseReader(reader);
                DataResult<CompositeRenderTypeData> result = CompositeRenderTypeData.CODEC.parse(JsonOps.INSTANCE, element);

                if (result.error().isPresent()) {
                    throw new JsonSyntaxException(result.error().get().message());
                }

                CompositeRenderTypeData data = result.result().orElseThrow();
                if (renderTypes.put(id, new RenderTypeCache(id.toString(), data)) != null) {
                    throw new IllegalStateException("Duplicate data file ignored with ID " + id);
                }
            } catch (Exception e) {
                Veil.LOGGER.error("Couldn't parse data file {} from {}", id, CONVERTER.idToFile(id), e);
            }
        }

        this.renderTypes.clear();
        this.renderTypes.putAll(renderTypes);
        Veil.LOGGER.info("Loaded {} render types", renderTypes.size());
    }

    private static class RenderTypeCache {

        private final String name;
        private final CompositeRenderTypeData data;
        private final Cache<Integer, RenderType> objectCache;
        private RenderType defaultCache;
        private boolean defaultError;

        public RenderTypeCache(String name, CompositeRenderTypeData data) {
            this.name = name;
            this.data = data;
            this.objectCache = CacheBuilder.newBuilder()
                    .initialCapacity(4)
                    .maximumSize(64)
                    .expireAfterAccess(Duration.of(1000, ChronoUnit.SECONDS))
                    .build();
            this.defaultCache = null;
        }

        public @Nullable RenderType get(Object... params) {
            if (params.length == 0) {
                if (this.defaultError) {
                    return null;
                }

                if (this.defaultCache == null) {
                    try {
                        this.defaultCache = this.data.createRenderType(this.name);
                    } catch (Exception e) {
                        Veil.LOGGER.error("Failed to create rendertype {} with no parameters", this.name, e);
                        this.defaultError = true;
                    }
                }
                return this.defaultCache;
            }

            try {
                return this.objectCache.get(Arrays.hashCode(params), () -> this.data.createRenderType(this.name, params));
            } catch (ExecutionException e) {
                Veil.LOGGER.error("Failed to create rendertype {} with parameters: [{}]", this.name, Arrays.stream(params)
                        .map(Objects::toString)
                        .collect(Collectors.joining(", ")), e);
                return null;
            }
        }
    }
}
