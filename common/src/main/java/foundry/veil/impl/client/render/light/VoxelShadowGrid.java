package foundry.veil.impl.client.render.light;

import com.mojang.blaze3d.systems.RenderSystem;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.light.DDALightData;
import foundry.veil.api.client.render.light.data.AreaLightData;
import foundry.veil.api.client.render.light.data.LightData;
import foundry.veil.api.client.render.light.data.PointLightData;
import foundry.veil.api.client.render.light.data.SpotLightData;
import foundry.veil.api.client.render.light.renderer.DDALightRenderer;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import foundry.veil.api.client.render.light.renderer.LightTypeRenderer;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.ApiStatus;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector3dc;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Objects;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL12C.*;
import static org.lwjgl.opengl.GL30C.GL_R8;

@ApiStatus.Internal
public final class VoxelShadowGrid {

    public static final int GRID_SIZE = 160;
    private static final int VOXELS_PER_BLOCK = 4;
    private static final int WORLD_SIZE = GRID_SIZE / VOXELS_PER_BLOCK;
    private static final int WORLD_HALF = WORLD_SIZE >> 1;
    private static final int GRID_VOLUME = GRID_SIZE * GRID_SIZE * GRID_SIZE;
    private static final int SLICE_AREA = GRID_SIZE * GRID_SIZE;
    private static final double CELL_SIZE = 1.0 / VOXELS_PER_BLOCK;
    private static final double INV_CELL_SIZE = VOXELS_PER_BLOCK;
    private static final double SHAPE_EDGE_EPSILON = 1.0E-5;
    private static final double FULL_SHAPE_EPSILON = 1.0 / 32.0;
    private static final double MIN_SHAPE_OCCLUSION = 0.025;
    private static final double ENTITY_CELL_FEATHER = CELL_SIZE * 0.72;
    private static final int FLUID_OCCLUSION = 18;

    private static final int MAX_SLICE_UPDATES_PER_FRAME = 4;
    private static final long BUILD_BUDGET_NS = 5_000_000L;
    private static final int MAX_DIRTY_UPDATES_PER_FRAME = 512;
    private static final int MAX_DIRTY_BACKLOG = 16384;

    private static final Vector3f uniformGridPos = new Vector3f();
    private static final Vector3f focusScratch = new Vector3f();
    private static int textureId;

    private static ResourceKey<Level> gridDimension;
    private static int originX, originY, originZ;
    private static ByteBuffer gridBuffer;

    private static ResourceKey<Level> buildDimension;
    private static int buildOriginX, buildOriginY, buildOriginZ;
    private static int buildIndex;
    private static ByteBuffer buildBuffer;
    private static ByteBuffer renderBuffer;

    private static final Object DIRTY_LOCK = new Object();
    private static final LongArrayFIFOQueue DIRTY_QUEUE = new LongArrayFIFOQueue();
    private static final LongOpenHashSet DIRTY_SET = new LongOpenHashSet();
    private static final long[] DRAIN_SCRATCH = new long[MAX_DIRTY_UPDATES_PER_FRAME];
    private static boolean rebuildRequested;

    private VoxelShadowGrid() {
    }

    public static void setup() {
        setup(hasOccludedLights());
    }

    public static void setup(boolean hasOccludedLights) {
        RenderSystem.assertOnRenderThread();

        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        if (level == null) {
            return;
        }

        ensureTexture();

        if (gridDimension != null && !Objects.equals(gridDimension, level.dimension())) {
            clearLevel();
        }

        Vec3 cameraPos = client.gameRenderer.getMainCamera().getPosition();
        Vector3fc focus = hasOccludedLights ? resolveGridFocus(cameraPos) : focusScratch.set(cameraPos.x, cameraPos.y, cameraPos.z);
        int cx = (int) Math.floor(focus.x());
        int cy = (int) Math.floor(focus.y());
        int cz = (int) Math.floor(focus.z());

        if (!hasOccludedLights) {
            uniformGridPos.set(cx - WORLD_HALF, cy - WORLD_HALF, cz - WORLD_HALF);
            return;
        }

        if (rebuildRequested) {
            rebuildRequested = false;
            clearDirty();
            startFullBuild(level, cx, cy, cz);
        } else if (buildBuffer != null) {
            int maxDelta = Math.max(
                    Math.abs(cx - (buildOriginX + WORLD_HALF)),
                    Math.max(Math.abs(cy - (buildOriginY + WORLD_HALF)), Math.abs(cz - (buildOriginZ + WORLD_HALF)))
            );
            if (!Objects.equals(buildDimension, level.dimension()) || maxDelta >= WORLD_HALF) {
                startFullBuild(level, cx, cy, cz);
            }
        } else if (gridBuffer == null) {
            startFullBuild(level, cx, cy, cz);
        }

        if (buildBuffer != null) {
            continueFullBuild(level);
        } else {
            shiftTowards(level, cx, cy, cz);
        }

        if (applyDirtyUpdates(level)) {
            uploadBuffer(buildBuffer != null ? buildBuffer : gridBuffer);
        }

        ByteBuffer activeBuffer;
        int activeOriginX;
        int activeOriginY;
        int activeOriginZ;
        if (gridBuffer != null && Objects.equals(gridDimension, level.dimension())) {
            activeBuffer = gridBuffer;
            activeOriginX = originX;
            activeOriginY = originY;
            activeOriginZ = originZ;
        } else if (buildBuffer != null && Objects.equals(buildDimension, level.dimension())) {
            activeBuffer = buildBuffer;
            activeOriginX = buildOriginX;
            activeOriginY = buildOriginY;
            activeOriginZ = buildOriginZ;
        } else {
            activeBuffer = null;
            activeOriginX = cx - WORLD_HALF;
            activeOriginY = cy - WORLD_HALF;
            activeOriginZ = cz - WORLD_HALF;
        }

        if (activeBuffer != null) {
            uniformGridPos.set(activeOriginX, activeOriginY, activeOriginZ);
            uploadDynamicOcclusion(level, activeBuffer, activeOriginX, activeOriginY, activeOriginZ);
        } else {
            uniformGridPos.set(cx - WORLD_HALF, cy - WORLD_HALF, cz - WORLD_HALF);
        }
    }

    public static void markBlockDirty(BlockPos pos) {
        long packed = pos.asLong();
        synchronized (DIRTY_LOCK) {
            if (!DIRTY_SET.add(packed)) {
                return;
            }
            DIRTY_QUEUE.enqueue(packed);
            if (DIRTY_QUEUE.size() > MAX_DIRTY_BACKLOG) {
                rebuildRequested = true;
                clearDirty();
            }
        }
    }

    public static void clearLevel() {
        RenderSystem.assertOnRenderThreadOrInit();

        gridDimension = null;
        if (gridBuffer != null) {
            MemoryUtil.memFree(gridBuffer);
            gridBuffer = null;
        }

        buildDimension = null;
        buildIndex = 0;
        if (buildBuffer != null) {
            MemoryUtil.memFree(buildBuffer);
            buildBuffer = null;
        }

        clearDirty();
    }

    public static void close() {
        RenderSystem.assertOnRenderThreadOrInit();
        clearLevel();
        if (textureId != 0) {
            glDeleteTextures(textureId);
            textureId = 0;
        }
        if (renderBuffer != null) {
            MemoryUtil.memFree(renderBuffer);
            renderBuffer = null;
        }
    }

    private static void clearDirty() {
        synchronized (DIRTY_LOCK) {
            DIRTY_QUEUE.clear();
            DIRTY_SET.clear();
        }
    }

    private static void startFullBuild(ClientLevel level, int cx, int cy, int cz) {
        buildDimension = level.dimension();
        buildOriginX = cx - WORLD_HALF;
        buildOriginY = cy - WORLD_HALF;
        buildOriginZ = cz - WORLD_HALF;
        buildIndex = 0;
        if (buildBuffer == null) {
            buildBuffer = MemoryUtil.memAlloc(GRID_VOLUME);
        }
        for (int i = 0; i < GRID_VOLUME; i++) {
            buildBuffer.put(i, (byte) 0);
        }
    }

    private static void continueFullBuild(ClientLevel level) {
        if (!Objects.equals(buildDimension, level.dimension())) {
            MemoryUtil.memFree(buildBuffer);
            buildBuffer = null;
            buildDimension = null;
            buildIndex = 0;
            return;
        }

        long deadline = System.nanoTime() + BUILD_BUDGET_NS;
        int previousIndex = buildIndex;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        while (buildIndex < GRID_VOLUME && System.nanoTime() < deadline) {
            int lx = buildIndex % GRID_SIZE;
            int ly = (buildIndex / GRID_SIZE) % GRID_SIZE;
            int lz = buildIndex / SLICE_AREA;
            pos.set(buildOriginX + lx / VOXELS_PER_BLOCK, buildOriginY + ly / VOXELS_PER_BLOCK, buildOriginZ + lz / VOXELS_PER_BLOCK);
            buildBuffer.put(buildIndex, blockOccupancy(level, pos, level.getBlockState(pos),
                    lx % VOXELS_PER_BLOCK, ly % VOXELS_PER_BLOCK, lz % VOXELS_PER_BLOCK));
            buildIndex++;
        }

        if (buildIndex < GRID_VOLUME) {
            if (buildIndex != previousIndex) {
                uploadBuffer(buildBuffer);
            }
            return;
        }

        if (gridBuffer != null) {
            MemoryUtil.memFree(gridBuffer);
        }
        gridBuffer = buildBuffer;
        gridDimension = buildDimension;
        originX = buildOriginX;
        originY = buildOriginY;
        originZ = buildOriginZ;

        buildBuffer = null;
        buildDimension = null;
        buildIndex = 0;

        uploadBuffer(gridBuffer);
    }

    private static void shiftTowards(ClientLevel level, int cx, int cy, int cz) {
        if (gridBuffer == null || !Objects.equals(gridDimension, level.dimension())) {
            return;
        }

        int dx = cx - (originX + WORLD_HALF);
        int dy = cy - (originY + WORLD_HALF);
        int dz = cz - (originZ + WORLD_HALF);

        if (Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))) >= WORLD_HALF) {
            startFullBuild(level, cx, cy, cz);
            return;
        }

        int steps = 0;
        boolean changed = false;
        while (steps < MAX_SLICE_UPDATES_PER_FRAME && (dx != 0 || dy != 0 || dz != 0)) {
            int ax = Math.abs(dx), ay = Math.abs(dy), az = Math.abs(dz);

            if (dx != 0 && ax >= ay && ax >= az) {
                if (dx > 0) {
                    shiftXPositive(level);
                    dx--;
                } else {
                    shiftXNegative(level);
                    dx++;
                }
            } else if (dz != 0 && az >= ay) {
                if (dz > 0) {
                    shiftZPositive(level);
                    dz--;
                } else {
                    shiftZNegative(level);
                    dz++;
                }
            } else if (dy != 0) {
                if (dy > 0) {
                    shiftYPositive(level);
                    dy--;
                } else {
                    shiftYNegative(level);
                    dy++;
                }
            } else {
                break;
            }

            changed = true;
            steps++;
        }

        if (changed) {
            uploadBuffer(gridBuffer);
        }
    }

    private static boolean applyDirtyUpdates(ClientLevel level) {
        if (gridBuffer == null && buildBuffer == null) {
            clearDirty();
            return false;
        }

        int toDrain;
        synchronized (DIRTY_LOCK) {
            toDrain = Math.min(DIRTY_QUEUE.size(), MAX_DIRTY_UPDATES_PER_FRAME);
            for (int i = 0; i < toDrain; i++) {
                DRAIN_SCRATCH[i] = DIRTY_QUEUE.dequeueLong();
                DIRTY_SET.remove(DRAIN_SCRATCH[i]);
            }
        }

        if (toDrain == 0) {
            return false;
        }

        boolean updatedBuffer = false;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < toDrain; i++) {
            long packed = DRAIN_SCRATCH[i];
            int x = BlockPos.getX(packed);
            int y = BlockPos.getY(packed);
            int z = BlockPos.getZ(packed);
            pos.set(x, y, z);
            BlockState state = level.getBlockState(pos);

            if (buildBuffer != null && Objects.equals(buildDimension, level.dimension())) {
                updatedBuffer |= writeBlockCells(level, buildBuffer, buildOriginX, buildOriginY, buildOriginZ, pos, state);
            }

            if (gridBuffer != null && Objects.equals(gridDimension, level.dimension())) {
                updatedBuffer |= writeBlockCells(level, gridBuffer, originX, originY, originZ, pos, state);
            }
        }

        return updatedBuffer;
    }

    private static boolean writeBlockCells(ClientLevel level, ByteBuffer buffer, int bufferOriginX, int bufferOriginY, int bufferOriginZ, BlockPos pos, BlockState state) {
        int startX = (pos.getX() - bufferOriginX) * VOXELS_PER_BLOCK;
        int startY = (pos.getY() - bufferOriginY) * VOXELS_PER_BLOCK;
        int startZ = (pos.getZ() - bufferOriginZ) * VOXELS_PER_BLOCK;
        boolean updated = false;

        for (int subZ = 0; subZ < VOXELS_PER_BLOCK; subZ++) {
            int z = startZ + subZ;
            if (z < 0 || z >= GRID_SIZE) {
                continue;
            }
            int zOffset = z * SLICE_AREA;
            for (int subY = 0; subY < VOXELS_PER_BLOCK; subY++) {
                int y = startY + subY;
                if (y < 0 || y >= GRID_SIZE) {
                    continue;
                }
                int yzOffset = zOffset + y * GRID_SIZE;
                for (int subX = 0; subX < VOXELS_PER_BLOCK; subX++) {
                    int x = startX + subX;
                    if (x < 0 || x >= GRID_SIZE) {
                        continue;
                    }
                    buffer.put(yzOffset + x, blockOccupancy(level, pos, state, subX, subY, subZ));
                    updated = true;
                }
            }
        }

        return updated;
    }

    private static void shiftXPositive(ClientLevel level) {
        originX++;
        long base = MemoryUtil.memAddress(gridBuffer);
        for (int z = 0; z < GRID_SIZE; z++) {
            for (int y = 0; y < GRID_SIZE; y++) {
                long row = base + (long) z * SLICE_AREA + (long) y * GRID_SIZE;
                MemoryUtil.memCopy(row + VOXELS_PER_BLOCK, row, GRID_SIZE - VOXELS_PER_BLOCK);
            }
        }
        for (int x = GRID_SIZE - VOXELS_PER_BLOCK; x < GRID_SIZE; x++) {
            fillSliceX(level, x, base);
        }
    }

    private static void shiftXNegative(ClientLevel level) {
        originX--;
        long base = MemoryUtil.memAddress(gridBuffer);
        for (int z = 0; z < GRID_SIZE; z++) {
            for (int y = 0; y < GRID_SIZE; y++) {
                long row = base + (long) z * SLICE_AREA + (long) y * GRID_SIZE;
                MemoryUtil.memCopy(row, row + VOXELS_PER_BLOCK, GRID_SIZE - VOXELS_PER_BLOCK);
            }
        }
        for (int x = 0; x < VOXELS_PER_BLOCK; x++) {
            fillSliceX(level, x, base);
        }
    }

    private static void fillSliceX(ClientLevel level, int writeX, long base) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int worldX = originX + writeX / VOXELS_PER_BLOCK;
        int subX = writeX % VOXELS_PER_BLOCK;
        for (int z = 0; z < GRID_SIZE; z++) {
            int worldZ = originZ + z / VOXELS_PER_BLOCK;
            int subZ = z % VOXELS_PER_BLOCK;
            for (int y = 0; y < GRID_SIZE; y++) {
                int worldY = originY + y / VOXELS_PER_BLOCK;
                int subY = y % VOXELS_PER_BLOCK;
                pos.set(worldX, worldY, worldZ);
                MemoryUtil.memPutByte(base + (long) z * SLICE_AREA + (long) y * GRID_SIZE + writeX,
                        blockOccupancy(level, pos, level.getBlockState(pos), subX, subY, subZ));
            }
        }
    }

    private static void shiftYPositive(ClientLevel level) {
        originY++;
        long base = MemoryUtil.memAddress(gridBuffer);
        for (int z = 0; z < GRID_SIZE; z++) {
            long plane = base + (long) z * SLICE_AREA;
            MemoryUtil.memCopy(plane + (long) VOXELS_PER_BLOCK * GRID_SIZE, plane, (long) GRID_SIZE * (GRID_SIZE - VOXELS_PER_BLOCK));
        }
        for (int y = GRID_SIZE - VOXELS_PER_BLOCK; y < GRID_SIZE; y++) {
            fillSliceY(level, y, base);
        }
    }

    private static void shiftYNegative(ClientLevel level) {
        originY--;
        long base = MemoryUtil.memAddress(gridBuffer);
        for (int z = 0; z < GRID_SIZE; z++) {
            long plane = base + (long) z * SLICE_AREA;
            MemoryUtil.memCopy(plane, plane + (long) VOXELS_PER_BLOCK * GRID_SIZE, (long) GRID_SIZE * (GRID_SIZE - VOXELS_PER_BLOCK));
        }
        for (int y = 0; y < VOXELS_PER_BLOCK; y++) {
            fillSliceY(level, y, base);
        }
    }

    private static void fillSliceY(ClientLevel level, int writeY, long base) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int worldY = originY + writeY / VOXELS_PER_BLOCK;
        int subY = writeY % VOXELS_PER_BLOCK;
        for (int z = 0; z < GRID_SIZE; z++) {
            int worldZ = originZ + z / VOXELS_PER_BLOCK;
            int subZ = z % VOXELS_PER_BLOCK;
            long row = base + (long) z * SLICE_AREA + (long) writeY * GRID_SIZE;
            for (int x = 0; x < GRID_SIZE; x++) {
                int worldX = originX + x / VOXELS_PER_BLOCK;
                int subX = x % VOXELS_PER_BLOCK;
                pos.set(worldX, worldY, worldZ);
                MemoryUtil.memPutByte(row + x, blockOccupancy(level, pos, level.getBlockState(pos), subX, subY, subZ));
            }
        }
    }

    private static void shiftZPositive(ClientLevel level) {
        originZ++;
        long base = MemoryUtil.memAddress(gridBuffer);
        MemoryUtil.memCopy(base + (long) VOXELS_PER_BLOCK * SLICE_AREA, base, (long) SLICE_AREA * (GRID_SIZE - VOXELS_PER_BLOCK));
        for (int z = GRID_SIZE - VOXELS_PER_BLOCK; z < GRID_SIZE; z++) {
            fillSliceZ(level, z, base);
        }
    }

    private static void shiftZNegative(ClientLevel level) {
        originZ--;
        long base = MemoryUtil.memAddress(gridBuffer);
        MemoryUtil.memCopy(base, base + (long) VOXELS_PER_BLOCK * SLICE_AREA, (long) SLICE_AREA * (GRID_SIZE - VOXELS_PER_BLOCK));
        for (int z = 0; z < VOXELS_PER_BLOCK; z++) {
            fillSliceZ(level, z, base);
        }
    }

    private static void fillSliceZ(ClientLevel level, int writeZ, long base) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int worldZ = originZ + writeZ / VOXELS_PER_BLOCK;
        int subZ = writeZ % VOXELS_PER_BLOCK;
        long plane = base + (long) writeZ * SLICE_AREA;
        for (int y = 0; y < GRID_SIZE; y++) {
            int worldY = originY + y / VOXELS_PER_BLOCK;
            int subY = y % VOXELS_PER_BLOCK;
            long row = plane + (long) y * GRID_SIZE;
            for (int x = 0; x < GRID_SIZE; x++) {
                int worldX = originX + x / VOXELS_PER_BLOCK;
                int subX = x % VOXELS_PER_BLOCK;
                pos.set(worldX, worldY, worldZ);
                MemoryUtil.memPutByte(row + x, blockOccupancy(level, pos, level.getBlockState(pos), subX, subY, subZ));
            }
        }
    }

    private static byte blockOccupancy(ClientLevel level, BlockPos pos, BlockState state, int subX, int subY, int subZ) {
        if (state.isAir()) {
            return 0;
        }

        boolean fluid = !state.getFluidState().isEmpty();
        VoxelShape shape = getShadowShape(level, pos, state);
        if (shape.isEmpty()) {
            return fluid ? (byte) FLUID_OCCLUSION : 0;
        }

        double cellMinX = subX * CELL_SIZE;
        double cellMinY = subY * CELL_SIZE;
        double cellMinZ = subZ * CELL_SIZE;
        double cellMaxX = cellMinX + CELL_SIZE;
        double cellMaxY = cellMinY + CELL_SIZE;
        double cellMaxZ = cellMinZ + CELL_SIZE;

        double volume = 0.0;
        double maxFace = 0.0;
        for (AABB box : shape.toAabbs()) {
            double sx = overlap(clampShape(box.minX), clampShape(box.maxX), cellMinX, cellMaxX) * INV_CELL_SIZE;
            double sy = overlap(clampShape(box.minY), clampShape(box.maxY), cellMinY, cellMaxY) * INV_CELL_SIZE;
            double sz = overlap(clampShape(box.minZ), clampShape(box.maxZ), cellMinZ, cellMaxZ) * INV_CELL_SIZE;
            if (sx <= 0.0 || sy <= 0.0 || sz <= 0.0) {
                continue;
            }

            volume = Math.min(1.0, volume + sx * sy * sz);
            maxFace = Math.max(maxFace, Math.max(sx * sy, Math.max(sx * sz, sy * sz)));
        }

        if (volume <= 0.0 && maxFace <= 0.0) {
            return fluid ? (byte) FLUID_OCCLUSION : 0;
        }

        double silhouette = Math.sqrt(volume * maxFace);
        double occupancy = Math.max(volume, volume * 0.65 + silhouette * 0.35);
        double opacity = Math.max(fluid ? FLUID_OCCLUSION / 255.0 : 0.0, blockShadowOpacity(level, pos, state));
        occupancy *= opacity;
        if (volume >= 1.0 - FULL_SHAPE_EPSILON && opacity >= 0.98) {
            return (byte) 0xFF;
        }

        occupancy = Math.max(occupancy, Math.min(MIN_SHAPE_OCCLUSION, opacity));
        return (byte) Math.min(255, Math.round(clamp01(occupancy) * 255.0));
    }

    private static double blockShadowOpacity(ClientLevel level, BlockPos pos, BlockState state) {
        int lightBlock = Math.max(0, Math.min(15, state.getLightBlock(level, pos)));
        if (lightBlock >= 15) {
            return 1.0;
        }
        if (lightBlock > 0) {
            return 0.16 + (lightBlock / 15.0) * 0.74;
        }
        if (state.propagatesSkylightDown(level, pos)) {
            return state.canOcclude() ? 0.22 : 0.08;
        }
        return state.canOcclude() ? 0.46 : 0.24;
    }

    private static VoxelShape getShadowShape(ClientLevel level, BlockPos pos, BlockState state) {
        CollisionContext context = CollisionContext.empty();
        VoxelShape shape = state.getCollisionShape(level, pos, context);
        if (!shape.isEmpty()) {
            return shape;
        }

        shape = state.getOcclusionShape(level, pos);
        if (!shape.isEmpty()) {
            return shape;
        }

        shape = state.getVisualShape(level, pos, context);
        return !shape.isEmpty() ? shape : state.getShape(level, pos, context);
    }

    private static void uploadDynamicOcclusion(ClientLevel level, ByteBuffer sourceBuffer, int sourceOriginX, int sourceOriginY, int sourceOriginZ) {
        ensureRenderBuffer();
        int sourcePosition = sourceBuffer.position();
        int renderPosition = renderBuffer.position();
        sourceBuffer.position(0);
        renderBuffer.position(0);
        MemoryUtil.memCopy(MemoryUtil.memAddress(sourceBuffer), MemoryUtil.memAddress(renderBuffer), GRID_VOLUME);
        sourceBuffer.position(sourcePosition);
        renderBuffer.position(renderPosition);
        paintEntities(level, renderBuffer, sourceOriginX, sourceOriginY, sourceOriginZ);
        uploadBuffer(renderBuffer);
    }

    private static void ensureRenderBuffer() {
        if (renderBuffer == null) {
            renderBuffer = MemoryUtil.memAlloc(GRID_VOLUME);
        }
    }

    private static void paintEntities(ClientLevel level, ByteBuffer buffer, int sourceOriginX, int sourceOriginY, int sourceOriginZ) {
        Minecraft client = Minecraft.getInstance();
        Entity cameraEntity = client.getCameraEntity();
        boolean firstPerson = client.options.getCameraType().isFirstPerson();

        for (Entity entity : level.entitiesForRendering()) {
            if (entity.isRemoved() || entity.isSpectator() || entity.isInvisible()) {
                continue;
            }
            if (entity == cameraEntity && firstPerson) {
                continue;
            }

            AABB box = entity.getBoundingBox();
            if (box.getSize() <= 0.001) {
                continue;
            }

            if (entity instanceof Player player) {
                paintEntityCells(buffer, sourceOriginX, sourceOriginY, sourceOriginZ, box,
                        (cellX, cellY, cellZ) -> humanoidCellOccupancy(player, box, cellX, cellY, cellZ));
            } else if (entity instanceof LivingEntity) {
                paintEntityCells(buffer, sourceOriginX, sourceOriginY, sourceOriginZ, box,
                        (cellX, cellY, cellZ) -> livingEntityCellOccupancy(box, cellX, cellY, cellZ));
            } else {
                paintEntityCells(buffer, sourceOriginX, sourceOriginY, sourceOriginZ, box,
                        (cellX, cellY, cellZ) -> entityCellOccupancy(box, cellX, cellY, cellZ));
            }
        }
    }

    private static void paintEntityCells(ByteBuffer buffer, int sourceOriginX, int sourceOriginY, int sourceOriginZ, AABB box, EntityCellSampler sampler) {
        int minX = Math.max(0, (int) Math.floor((box.minX - sourceOriginX) * INV_CELL_SIZE));
        int minY = Math.max(0, (int) Math.floor((box.minY - sourceOriginY) * INV_CELL_SIZE));
        int minZ = Math.max(0, (int) Math.floor((box.minZ - sourceOriginZ) * INV_CELL_SIZE));
        int maxX = Math.min(GRID_SIZE - 1, (int) Math.floor((box.maxX - sourceOriginX - SHAPE_EDGE_EPSILON) * INV_CELL_SIZE));
        int maxY = Math.min(GRID_SIZE - 1, (int) Math.floor((box.maxY - sourceOriginY - SHAPE_EDGE_EPSILON) * INV_CELL_SIZE));
        int maxZ = Math.min(GRID_SIZE - 1, (int) Math.floor((box.maxZ - sourceOriginZ - SHAPE_EDGE_EPSILON) * INV_CELL_SIZE));
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            return;
        }

        for (int z = minZ; z <= maxZ; z++) {
            int zOffset = z * SLICE_AREA;
            double cellZ = sourceOriginZ + z * CELL_SIZE;
            for (int y = minY; y <= maxY; y++) {
                double cellY = sourceOriginY + y * CELL_SIZE;
                int yzOffset = zOffset + y * GRID_SIZE;
                for (int x = minX; x <= maxX; x++) {
                    int index = yzOffset + x;
                    byte occupancy = sampler.sample(sourceOriginX + x * CELL_SIZE, cellY, cellZ);
                    if ((buffer.get(index) & 0xFF) < (occupancy & 0xFF)) {
                        buffer.put(index, occupancy);
                    }
                }
            }
        }
    }

    private static byte humanoidCellOccupancy(Player player, AABB box, double cellX, double cellY, double cellZ) {
        double height = box.maxY - box.minY;
        double width = Math.max(0.24, Math.min(box.maxX - box.minX, box.maxZ - box.minZ));
        if (height < 1.0) {
            return livingEntityCellOccupancy(box, cellX, cellY, cellZ);
        }

        double centerX = cellX + CELL_SIZE * 0.5;
        double centerY = cellY + CELL_SIZE * 0.5;
        double centerZ = cellZ + CELL_SIZE * 0.5;
        double yaw = Math.toRadians(player.getYRot());
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);
        double dx = centerX - player.getX();
        double dz = centerZ - player.getZ();
        double localX = dx * cos - dz * sin;
        double localZ = dx * sin + dz * cos;
        double localY = centerY - box.minY;

        double legHeight = height * 0.47;
        double torsoHeight = height * 0.34;
        double headHeight = height * 0.19;
        double legY = legHeight * 0.5;
        double torsoY = legHeight + torsoHeight * 0.5;
        double headY = legHeight + torsoHeight + headHeight * 0.48;

        double legOffset = width * 0.15;
        double armOffset = width * 0.42;
        double body = 0.0;
        body = Math.max(body, roundedBoxCoverage(localX, localY, localZ, torsoY, width * 0.29, torsoHeight * 0.50, width * 0.17, ENTITY_CELL_FEATHER));
        body = Math.max(body, roundedBoxCoverage(localX, localY, localZ, headY, width * 0.22, headHeight * 0.48, width * 0.22, ENTITY_CELL_FEATHER));
        body = Math.max(body, roundedBoxCoverage(localX - legOffset, localY, localZ, legY, width * 0.105, legHeight * 0.50, width * 0.13, ENTITY_CELL_FEATHER));
        body = Math.max(body, roundedBoxCoverage(localX + legOffset, localY, localZ, legY, width * 0.105, legHeight * 0.50, width * 0.13, ENTITY_CELL_FEATHER));
        body = Math.max(body, roundedBoxCoverage(localX - armOffset, localY, localZ, torsoY, width * 0.085, torsoHeight * 0.52, width * 0.105, ENTITY_CELL_FEATHER));
        body = Math.max(body, roundedBoxCoverage(localX + armOffset, localY, localZ, torsoY, width * 0.085, torsoHeight * 0.52, width * 0.105, ENTITY_CELL_FEATHER));
        if (body <= 0.001) {
            return 0;
        }

        double coverage = cellOverlap(box, cellX, cellY, cellZ);
        double occupancy = Math.pow(clamp01(body * Math.max(coverage, 0.35)), 0.74);
        return (byte) Math.max(5, Math.min(220, Math.round(occupancy * 220.0)));
    }

    private static byte livingEntityCellOccupancy(AABB box, double cellX, double cellY, double cellZ) {
        double coverage = cellOverlap(box, cellX, cellY, cellZ);
        if (coverage <= 1.0E-5) {
            return 0;
        }

        double centerX = cellX + CELL_SIZE * 0.5;
        double centerY = cellY + CELL_SIZE * 0.5;
        double centerZ = cellZ + CELL_SIZE * 0.5;
        double halfX = Math.max((box.maxX - box.minX) * 0.5, CELL_SIZE * 0.5);
        double halfY = Math.max((box.maxY - box.minY) * 0.5, CELL_SIZE * 0.5);
        double halfZ = Math.max((box.maxZ - box.minZ) * 0.5, CELL_SIZE * 0.5);
        double normalizedX = Math.abs(centerX - (box.minX + box.maxX) * 0.5) / halfX;
        double normalizedY = Math.abs(centerY - (box.minY + box.maxY) * 0.5) / halfY;
        double normalizedZ = Math.abs(centerZ - (box.minZ + box.maxZ) * 0.5) / halfZ;
        double horizontal = Math.sqrt(normalizedX * normalizedX + normalizedZ * normalizedZ);
        double capsule = (1.0 - smoothstep(0.70, 1.08, horizontal)) * (1.0 - smoothstep(0.88, 1.08, normalizedY));
        double shapedCoverage = coverage * (0.25 + 0.75 * capsule);
        shapedCoverage = Math.pow(clamp01(shapedCoverage), 0.80);
        return (byte) Math.max(4, Math.min(190, Math.round(shapedCoverage * 205.0)));
    }

    private static byte entityCellOccupancy(AABB box, double cellX, double cellY, double cellZ) {
        double coverage = cellOverlap(box, cellX, cellY, cellZ);
        if (coverage <= 1.0E-5) {
            return 0;
        }

        double centerX = cellX + CELL_SIZE * 0.5;
        double centerY = cellY + CELL_SIZE * 0.5;
        double centerZ = cellZ + CELL_SIZE * 0.5;
        double halfX = Math.max((box.maxX - box.minX) * 0.5, CELL_SIZE * 0.5);
        double halfY = Math.max((box.maxY - box.minY) * 0.5, CELL_SIZE * 0.5);
        double halfZ = Math.max((box.maxZ - box.minZ) * 0.5, CELL_SIZE * 0.5);
        double normalizedX = Math.abs(centerX - (box.minX + box.maxX) * 0.5) / halfX;
        double normalizedY = Math.abs(centerY - (box.minY + box.maxY) * 0.5) / halfY;
        double normalizedZ = Math.abs(centerZ - (box.minZ + box.maxZ) * 0.5) / halfZ;
        double roundness = 1.0 - smoothstep(0.62, 1.18, Math.sqrt(normalizedX * normalizedX + normalizedZ * normalizedZ));
        double vertical = 1.0 - smoothstep(0.82, 1.08, normalizedY);

        double shapedCoverage = coverage * (0.38 + 0.62 * roundness * vertical);
        shapedCoverage = Math.pow(clamp01(shapedCoverage), 0.78);
        return (byte) Math.max(4, Math.min(180, Math.round(shapedCoverage * 205.0)));
    }

    private static double roundedBoxCoverage(double x, double y, double z, double centerY, double halfX, double halfY, double halfZ, double feather) {
        double qx = Math.abs(x) - halfX;
        double qy = Math.abs(y - centerY) - halfY;
        double qz = Math.abs(z) - halfZ;
        double outsideX = Math.max(qx, 0.0);
        double outsideY = Math.max(qy, 0.0);
        double outsideZ = Math.max(qz, 0.0);
        double outside = Math.sqrt(outsideX * outsideX + outsideY * outsideY + outsideZ * outsideZ);
        double inside = Math.min(Math.max(qx, Math.max(qy, qz)), 0.0);
        double distance = outside + inside;
        if (distance <= -feather) {
            return 1.0;
        }
        if (distance >= feather) {
            return 0.0;
        }
        return 1.0 - smoothstep(-feather, feather, distance);
    }

    private static double cellOverlap(AABB box, double cellX, double cellY, double cellZ) {
        double x = overlap(box.minX, box.maxX, cellX, cellX + CELL_SIZE) * INV_CELL_SIZE;
        double y = overlap(box.minY, box.maxY, cellY, cellY + CELL_SIZE) * INV_CELL_SIZE;
        double z = overlap(box.minZ, box.maxZ, cellZ, cellZ + CELL_SIZE) * INV_CELL_SIZE;
        return x * y * z;
    }

    private static double overlap(double minA, double maxA, double minB, double maxB) {
        return Math.max(0.0, Math.min(maxA, maxB) - Math.max(minA, minB));
    }

    private static double smoothstep(double edge0, double edge1, double value) {
        double x = clamp01((value - edge0) / (edge1 - edge0));
        return x * x * (3.0 - 2.0 * x);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static double clampShape(double value) {
        if (value <= SHAPE_EDGE_EPSILON) {
            return 0.0;
        }
        if (value >= 1.0 - SHAPE_EDGE_EPSILON) {
            return 1.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static void uploadBuffer(ByteBuffer buffer) {
        if (buffer == null) {
            return;
        }

        buffer.position(0);
        buffer.limit(GRID_VOLUME);

        int unpackAlignment = glGetInteger(GL_UNPACK_ALIGNMENT);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);

        glBindTexture(GL_TEXTURE_3D, textureId);
        glTexSubImage3D(GL_TEXTURE_3D, 0, 0, 0, 0, GRID_SIZE, GRID_SIZE, GRID_SIZE, GL_RED, GL_UNSIGNED_BYTE, buffer);
        glBindTexture(GL_TEXTURE_3D, 0);
        glPixelStorei(GL_UNPACK_ALIGNMENT, unpackAlignment);
    }

    private static void ensureTexture() {
        if (textureId != 0) {
            return;
        }
        textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_3D, textureId);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);
        ByteBuffer zeros = MemoryUtil.memCalloc(GRID_VOLUME);
        glTexImage3D(GL_TEXTURE_3D, 0, GL_R8, GRID_SIZE, GRID_SIZE, GRID_SIZE, 0, GL_RED, GL_UNSIGNED_BYTE, zeros);
        MemoryUtil.memFree(zeros);
        glBindTexture(GL_TEXTURE_3D, 0);

    }

    private static Vector3fc resolveGridFocus(Vec3 cameraPos) {
        double sumX = cameraPos.x * 0.15;
        double sumY = cameraPos.y * 0.15;
        double sumZ = cameraPos.z * 0.15;
        double weightSum = 0.15;
        boolean foundLightFocus = false;

        Collection<LightTypeRenderer<?>> renderers = VeilRenderSystem.renderer().getLightRenderer().getRenderers().values();
        for (LightTypeRenderer<?> renderer : renderers) {
            if (!(renderer instanceof DDALightRenderer<?>)) {
                continue;
            }

            for (LightRenderHandle<?> handle : renderer.getPreparedLights()) {
                LightData light = handle.getLightData();
                if (!(light instanceof DDALightData ddaLight) || !ddaLight.isOcclusionEnabled() || ddaLight.getShadowIntensity() <= 0.0001F) {
                    continue;
                }

                LightFocus focus = lightFocus(light);
                if (focus == null) {
                    continue;
                }

                double dx = focus.x - cameraPos.x;
                double dy = focus.y - cameraPos.y;
                double dz = focus.z - cameraPos.z;
                double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                double range = Math.max(1.0, focus.range);
                if (distance > WORLD_SIZE + range) {
                    continue;
                }

                double targetX = focus.x;
                double targetY = focus.y;
                double targetZ = focus.z;
                double weight = Math.max(1.0, Math.min(6.0, range / Math.max(4.0, distance * 0.25)));

                sumX += targetX * weight;
                sumY += targetY * weight;
                sumZ += targetZ * weight;
                weightSum += weight;
                foundLightFocus = true;
            }
        }

        if (!foundLightFocus) {
            return focusScratch.set(cameraPos.x, cameraPos.y, cameraPos.z);
        }
        return focusScratch.set(sumX / weightSum, sumY / weightSum, sumZ / weightSum);
    }

    private static LightFocus lightFocus(LightData light) {
        if (light instanceof PointLightData pointLight) {
            Vector3dc position = pointLight.getPosition();
            return new LightFocus(position.x(), position.y(), position.z(), pointLight.getRadius());
        }
        if (light instanceof SpotLightData spotLight) {
            Vector3dc position = spotLight.getPosition();
            Vector3fc direction = spotLight.getDirection();
            double distance = Math.min(Math.max(spotLight.getRange() * 0.45, 1.0), WORLD_HALF * 0.85);
            return new LightFocus(
                    position.x() + direction.x() * distance,
                    position.y() + direction.y() * distance,
                    position.z() + direction.z() * distance,
                    spotLight.getRange());
        }
        if (light instanceof AreaLightData areaLight) {
            Vector3dc position = areaLight.getPosition();
            return new LightFocus(position.x(), position.y(), position.z(), areaLight.getDistance());
        }
        return null;
    }

    private static boolean hasOccludedLights() {
        Collection<LightTypeRenderer<?>> renderers = VeilRenderSystem.renderer().getLightRenderer().getRenderers().values();
        for (LightTypeRenderer<?> renderer : renderers) {
            if (renderer instanceof DDALightRenderer<?> ddaLightRenderer && ddaLightRenderer.hasOccludedLights()) {
                return true;
            }
        }
        return false;
    }

    public static Vector3fc getUniformGridPos() {
        return uniformGridPos;
    }

    public static int getTextureId() {
        return textureId;
    }

    private record LightFocus(double x, double y, double z, double range) {
    }

    @FunctionalInterface
    private interface EntityCellSampler {
        byte sample(double cellX, double cellY, double cellZ);
    }
}
