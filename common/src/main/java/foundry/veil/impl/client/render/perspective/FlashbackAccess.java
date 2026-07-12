package foundry.veil.impl.client.render.perspective;

import foundry.veil.Veil;
import foundry.veil.api.compat.FlashbackCompat;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public class FlashbackAccess {

    private static boolean disabled;

    public static boolean backup(Matrix4f lastProjectionMatrix, Quaternionf lastViewQuaternion) {
        if (!disabled && FlashbackCompat.isLoaded()) {
            try {
                FlashbackCompat.INSTANCE.backup(lastProjectionMatrix, lastViewQuaternion);
                return true;
            } catch (Throwable t) {
                disabled = true;
                Veil.LOGGER.warn("Disabling Flashback compatibility after a failed state backup.", t);
            }
        }
        return false;
    }

    public static void restore(Matrix4f lastProjectionMatrix, Quaternionf lastViewQuaternion) {
        if (!disabled && FlashbackCompat.isLoaded()) {
            try {
                FlashbackCompat.INSTANCE.restore(lastProjectionMatrix, lastViewQuaternion);
            } catch (Throwable t) {
                disabled = true;
                Veil.LOGGER.warn("Disabling Flashback compatibility after a failed state restore.", t);
            }
        }
    }

}
