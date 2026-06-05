package foundry.veil.forge.mixin.client.command;

import foundry.veil.ext.VeilClientSuggestionProvider;
import foundry.veil.impl.command.VeilClientSuggestions;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.ClientCommandSourceStack;
import org.spongepowered.asm.mixin.Mixin;

import java.util.stream.Stream;

@Mixin(ClientCommandSourceStack.class)
public class ClientCommandSourceStackMixin implements VeilClientSuggestionProvider {

    @Override
    public Stream<Identifier> veil$getPostPipelineNames() {
        return VeilClientSuggestions.getPostPipelineNames();
    }
}
