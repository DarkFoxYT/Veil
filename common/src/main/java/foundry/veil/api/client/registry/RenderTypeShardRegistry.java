package foundry.veil.api.client.registry;

import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.jetbrains.annotations.ApiStatus;

import java.util.function.Predicate;

/**
 * Compatibility entrypoint for render-type shard registration.
 */
public final class RenderTypeShardRegistry {

    private RenderTypeShardRegistry() {
    }

    public static synchronized void addShard(RenderType renderType, RenderStateShard... shards) {
    }

    public static synchronized void addShard(String name, RenderStateShard... shards) {
        if (shards.length == 0) {
            throw new IllegalArgumentException("No shards provided");
        }
    }

    public static synchronized void addGenericShard(Predicate<RenderType> filter, RenderStateShard... shards) {
        if (shards.length == 0) {
            throw new IllegalArgumentException("No shards provided");
        }
    }

    @ApiStatus.Internal
    public static void inject(RenderType renderType) {
    }
}
