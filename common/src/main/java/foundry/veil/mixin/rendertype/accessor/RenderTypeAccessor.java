package foundry.veil.mixin.rendertype.accessor;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RenderType.class)
public interface RenderTypeAccessor {

    @Invoker("create")
    static RenderType veil$create(String name, RenderSetup state) {
        throw new AssertionError();
    }
}
