package foundry.veil.fabric.compat.flashback;

import com.moulberry.flashback.editor.ui.ReplayUI;
import foundry.veil.api.compat.FlashbackCompat;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public class VeilFabricFlashbackCompat implements FlashbackCompat {

    private boolean projectionWasNull;
    private boolean viewWasNull;

    @Override
    public void backup(Matrix4f lastProjectionMatrix, Quaternionf lastViewQuaternion) {
        Matrix4f projection = ReplayUI.lastProjectionMatrix;
        this.projectionWasNull = projection == null;
        if (projection != null) {
            lastProjectionMatrix.set(projection);
        } else {
            lastProjectionMatrix.identity();
        }

        Quaternionf view = ReplayUI.lastViewQuaternion;
        this.viewWasNull = view == null;
        if (view != null) {
            lastViewQuaternion.set(view);
        } else {
            lastViewQuaternion.identity();
        }
    }

    @Override
    public void restore(Matrix4f lastProjectionMatrix, Quaternionf lastViewQuaternion) {
        if (this.projectionWasNull) {
            ReplayUI.lastProjectionMatrix = null;
        } else {
            if (ReplayUI.lastProjectionMatrix == null) {
                ReplayUI.lastProjectionMatrix = new Matrix4f();
            }
            ReplayUI.lastProjectionMatrix.set(lastProjectionMatrix);
        }

        if (this.viewWasNull) {
            ReplayUI.lastViewQuaternion = null;
        } else {
            if (ReplayUI.lastViewQuaternion == null) {
                ReplayUI.lastViewQuaternion = new Quaternionf();
            }
            ReplayUI.lastViewQuaternion.set(lastViewQuaternion);
        }
    }
}
