package foundry.veil.forge;

import foundry.veil.Veil;
import foundry.veil.VeilClient;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.forge.event.ForgeVeilRegisterFixedBuffersEvent;
import foundry.veil.forge.event.ForgeVeilRendererAvailableEvent;
import foundry.veil.forge.impl.ForgeRenderTypeStageHandler;
import foundry.veil.impl.VeilReloadListeners;
import foundry.veil.impl.client.imgui.VeilImGuiCompat;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
@Mod(value = Veil.MODID, dist = Dist.CLIENT)
public class VeilForgeClient {

    public VeilForgeClient(IEventBus modEventBus) {
        VeilClient.init();

        modEventBus.addListener(VeilForgeClient::registerKeys);
        modEventBus.addListener(VeilForgeClient::registerListeners);
    }

    private static void registerListeners(AddClientReloadListenersEvent event) {
        VeilRenderSystem.init();
        VeilReloadListeners.registerListeners((type, id, listener) -> event.addListener(Veil.veilPath(id), listener));
        ModLoader.postEvent(new ForgeVeilRendererAvailableEvent(VeilRenderSystem.renderer()));
        ModLoader.postEvent(new ForgeVeilRegisterFixedBuffersEvent(ForgeRenderTypeStageHandler::register));
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        if (Veil.IMGUIMC) {
            event.register(VeilImGuiCompat.EDITOR_KEY);
        }
    }

}
