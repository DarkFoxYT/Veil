package foundry.veil.forge.event;

import foundry.veil.api.event.VeilRegisterFixedBuffersEvent;
import foundry.veil.api.event.VeilRenderLevelStageEvent;
import foundry.veil.platform.VeilEventPlatform;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

/**
 * <p>Registers custom fixed render operand buffers.</p>
 * <p>Use {@link RenderLevelStageEvent} or {@link VeilEventPlatform#onVeilRenderLevelStage(VeilRenderLevelStageEvent)}  to listen to level stage render events on Forge.</p>
 *
 * @author Ocelot
 * @see VeilRegisterFixedBuffersEvent
 */
public class ForgeVeilRegisterFixedBuffersEvent extends Event implements IModBusEvent {

    private final BiConsumer<Class<? extends RenderLevelStageEvent>, RenderType> registry;

    public ForgeVeilRegisterFixedBuffersEvent(BiConsumer<Class<? extends RenderLevelStageEvent>, RenderType> registry) {
        this.registry = registry;
    }

    /**
     * Registers a fixed render operand.
     *
     * @param stage      The stage the buffer should be finished after or <code>null</code> to do it manually
     * @param renderType The render operand to finish
     */
    public void register(@Nullable Class<? extends RenderLevelStageEvent> stage, RenderType renderType) {
        this.registry.accept(stage, renderType);
    }
}
