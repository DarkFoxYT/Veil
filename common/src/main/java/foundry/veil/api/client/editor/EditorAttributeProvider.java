package foundry.veil.api.client.editor;

/**
 * Provides extra ImGui rendering details. This is used by editor panels to add/change properties with ImGui.
 *
 * @author Ocelot
 */
public interface EditorAttributeProvider {

    /**
     * Renders all ImGui attributes into the current editor panel.
     */
    void renderImGuiAttributes();

    /**
     * @return Whether this provider renders the complete editor for the object, including common attributes.
     */
    default boolean rendersCompleteEditor() {
        return false;
    }
}
