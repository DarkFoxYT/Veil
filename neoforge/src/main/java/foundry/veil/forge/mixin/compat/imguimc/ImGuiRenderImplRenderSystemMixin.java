package foundry.veil.forge.mixin.compat.imguimc;

import com.mojang.blaze3d.textures.GpuTextureView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.reflect.Field;
import java.util.List;

@Mixin(targets = "foundry.imgui.impl.renderer.v1.ImGuiRenderImplRenderSystem", remap = false)
public abstract class ImGuiRenderImplRenderSystemMixin {

    private static final Field VEIL$DATA = veil$getField("foundry.imgui.impl.renderer.v1.ImGuiRenderImplRenderSystem", "data");
    private static final Field VEIL$FONT_TEXTURE_VIEW = veil$getField("foundry.imgui.impl.renderer.v1.ImGuiRenderImplRenderSystem$Data", "fontTextureView");

    @Redirect(
            method = "renderDrawData(Limgui/ImDrawData;Lfoundry/imgui/impl/renderer/v1/ImGuiRenderImplRenderSystem$ViewportData;Ljava/util/OptionalInt;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/List;get(I)Ljava/lang/Object;", ordinal = 4)
    )
    private Object veil$safeSampler(List<?> samplers, int index) {
        if (index >= 0 && index < samplers.size()) {
            return samplers.get(index);
        }
        return null;
    }

    @Redirect(
            method = "renderDrawData(Limgui/ImDrawData;Lfoundry/imgui/impl/renderer/v1/ImGuiRenderImplRenderSystem$ViewportData;Ljava/util/OptionalInt;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/List;get(I)Ljava/lang/Object;", ordinal = 5)
    )
    private Object veil$safeTexture(List<?> textures, int index) {
        if (index >= 0 && index < textures.size()) {
            return textures.get(index);
        }

        Object data = veil$get(VEIL$DATA, this);
        if (data != null) {
            GpuTextureView fontTextureView = veil$getFontTextureView(data);
            if (fontTextureView != null) {
                return fontTextureView;
            }
        }

        return textures.get(index);
    }

    private static Field veil$getField(String className, String fieldName) {
        try {
            Field field = Class.forName(className).getDeclaredField(fieldName);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static Object veil$get(Field field, Object instance) {
        if (field == null) {
            return null;
        }
        try {
            return field.get(instance);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    private static GpuTextureView veil$getFontTextureView(Object data) {
        Object fontTextureView = veil$get(VEIL$FONT_TEXTURE_VIEW, data);
        return fontTextureView instanceof GpuTextureView view ? view : null;
    }
}
