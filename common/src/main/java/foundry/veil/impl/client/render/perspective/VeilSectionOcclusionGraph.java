package foundry.veil.impl.client.render.perspective;

import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;

import java.util.List;

public class VeilSectionOcclusionGraph {

    public void update(ViewArea viewArea, boolean smartCull, LevelPerspectiveCamera camera, Frustum frustum, List<SectionRenderDispatcher.RenderSection> sections) {
        for (SectionRenderDispatcher.RenderSection section : viewArea.sections) {
            if (section != null && frustum.isVisible(section.getBoundingBox())) {
                sections.add(section);
            }
        }
    }

    public void reset() {
    }
}
