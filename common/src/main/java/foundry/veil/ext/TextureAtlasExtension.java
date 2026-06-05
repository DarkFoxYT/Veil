package foundry.veil.ext;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public interface TextureAtlasExtension {

    boolean veil$hasTexture(Identifier location);
}
