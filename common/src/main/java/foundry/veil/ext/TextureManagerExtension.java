package foundry.veil.ext;

import foundry.veil.api.client.render.texture.VeilPreloadedTexture;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public interface TextureManagerExtension {

    <T extends AbstractTexture & VeilPreloadedTexture> CompletableFuture<?> veil$registerPreloadedTexture(Identifier path, T texture, Executor executor);
}
