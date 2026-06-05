package foundry.veil.forge.ext;

import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.function.Function;
import java.util.function.Supplier;

public interface DeferredRegisterExtensions<T> {

    default <I extends T> DeferredHolder<T, I> register(Identifier name, Supplier<? extends I> sup) {
        return this.register(name, key -> sup.get());
    }

    <I extends T> DeferredHolder<T, I> register(Identifier name, Function<Identifier, ? extends I> func);
}
