package com.predator.client.vision;

import com.predator.Predator;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/**
 * Finds heat sources near the camera and hands them to the vision shader as boxes.
 * <h2>Why boxes, and why merged</h2> A lava pool is not a point and it is not a sphere. Earlier versions emitted one
 * point source per aggregation cell, which cost a slot per cell and — because a sphere large enough to cover a cell's
 * corners bulges past every face — turned a square pool into an oval blob spilling several blocks onto the surrounding
 * ground.
 * <p>
 * Instead, each layer of same-heat blocks is decomposed by greedy meshing into the smallest set of rectangles that
 * covers it exactly, and identical rectangles in adjacent layers are merged vertically. The shader measures distance to
 * those boxes: inside is full heat, so every lava block reads solid red in the pool's true shape, and the fade radiates
 * from its real edge. A square pool is one box, an L is two, a lone torch is a 1x1x1 — no special cases.
 * <h2>Cost control — the palette check is what makes this cheap</h2> Walking the volume block by block is hundreds of
 * thousands of lookups, which is why earlier versions had to smear a single sweep across a hundred frames and still
 * only reached 32 blocks.
 * <p>
 * ⭐⭐ Instead, ask each 16x16x16 chunk section whether its <b>palette</b> contains a heat source at all. A section
 * stores the distinct block states it holds, so that question costs a handful of comparisons rather than 4,096 block
 * reads — and almost every section near the player contains no lava, fire or torch whatsoever. Only sections that
 * actually hold something hot are read block by block.
 * <p>
 * A whole refresh therefore touches a few hundred palettes and two or three real sections, cheaply enough to run
 * outright on a timer instead of being spread over frames. Nothing runs unless the vision is being drawn.
 * <h2>Known limitation</h2> ⚠ The decomposition is exact — the boxes cover the hot blocks and nothing else — but an
 * intricate shape costs more boxes, and the cap keeps only the nearest {@link #MAX_SOURCES}. A ragged natural pool
 * typically needs a handful; a built square needs one.
 */
public final class PredatorHeatSourceScanner {

    /**
     * How far out sources are collected horizontally — the hard limit on spotting heat at a distance.
     * <p>
     * ⭐ Generous because the palette check made distance nearly free: reach costs section checks, not block reads.
     */
    private static final int RADIUS_HORIZONTAL = 96;

    /**
     * How far up and down sources are collected. Still worth keeping modest — heat sources sit near the viewer's own
     * height almost without exception, and vertical reach costs the same per block as horizontal while buying less.
     */
    private static final int RADIUS_VERTICAL = 32;

    /** How often the whole volume is re-examined. Cheap enough to be a timer rather than a per-frame slice. */
    private static final long REFRESH_INTERVAL_IN_MILLIS = 250L;

    /**
     * Boxes the shader can hold. Each takes two vec4s (min+heat, max+pad), so the shader's array is twice this.
     * <p>
     * ⚠ The shader loops every source for every pixel, so this is not free. Merging is what makes it generous: a lake
     * that used to eat a hundred slots now takes one.
     */
    public static final int MAX_SOURCES = 32;

    private static final String DEBUG_PROPERTY = "avp_predator.heatDebug";

    private static final long DEBUG_INTERVAL_IN_MILLIS = 1000L;

    private static final float[] EMPTY = new float[0];

    /** Hot blocks found by the last refresh, keyed by packed position, valued by heat rating. */
    /**
     * Hot blocks found by the current scan. <strong>⚠⚠ PRIMITIVE, AND CAPPED. Both matter, and it used to be
     * neither.</strong> It was a {@code HashMap<Long, Float>}, which boxes EVERY key and value: a Long, a Float, a Node
     * and a table slot is roughly 72 bytes per lava block. A nether lava lake inside the 193x65x193 scan volume is
     * comfortably 70,000 blocks — about 5 MB, allocated and thrown away FOUR TIMES A SECOND, with publish() then
     * building a second copy of all of it. That is the "large lava pool overwhelms something" he described.
     * <p>
     * {@code Long2FloatOpenHashMap} stores the same data in flat primitive arrays with no per-entry object at all.
     * <p>
     * ⚠ NOT final (Sep 27). {@link #reset()} swaps in a fresh map because {@code clear()} empties the entries but keeps
     * the backing arrays at their largest size — after one big lava area that is a few MB held for the rest of the
     * session. Replacing the map is what actually hands that memory back.
     */
    private static Long2FloatOpenHashMap WORKING = new Long2FloatOpenHashMap();

    /**
     * Hard ceiling on blocks collected per scan. <strong>⚠⚠ 250,000, NOT THE 60,000 I FIRST PICKED — 60k HID LAVA
     * LAKES</strong> A single full Y layer across the 193x193 scan window is 37,249 blocks, so a few levels of nether
     * lava sea pass 60,000 on their own. That ceiling truncated ORDINARY terrain, not just pathological cases, and
     * combined with the far-corner scan order it meant the lake in front of the player never entered the set at all.
     * <p>
     * ⚠ It is affordable now only because {@link #WORKING} is primitive: 250,000 entries is about 2.9 MB in a
     * {@code Long2FloatOpenHashMap} against roughly 17 MB in the boxed {@code HashMap} this replaced. The cap and the
     * primitive storage are one fix, not two.
     * <p>
     * ⚠ This is a SAFETY NET against an unbounded scan, not a tuning dial. Real terrain should never reach it; if
     * something does, the nearest-first ordering above means what survives is what is closest.
     */
    private static final int MAX_HOT_BLOCKS = 250_000;

    /**
     * Size in blocks of the grid the surface is snapped to before meshing.
     * <p>
     * ⚠ 4 is the sweet spot measured against ragged lake shapes: it takes a 160x160 lake from 317 boxes to a handful,
     * while staying below what the thermal blur can resolve. 2 is barely a saving; 8 starts to show as stepping on
     * small pools like a single campfire.
     */
    private static final int GRID = 4;

    /**
     * Blocks in a layer before it is treated as terrain and quantised.
     * <p>
     * ⚠ 64 — comfortably above any placed light source (a campfire is one block, a fireplace maybe a dozen) and far
     * below any natural pool. Below this the layer meshes at full 1-block resolution, so a torch stays a torch.
     */
    private static final int COARSEN_ABOVE = 64;

    private static List<Box> published = List.of();

    private static float[] packed = EMPTY;

    private static long lastRefreshAtMillis;

    private static int originX;

    private static int originY;

    private static int originZ;

    private static long lastDebugLogAtMillis;

    /**
     * The level the held sources were scanned in (Sep 27). <strong>⚠ WEAK, deliberately:</strong> a strong reference
     * here would keep an entire unloaded ClientLevel — chunks, entities and all — alive after the player leaves it,
     * which would be a far worse leak than the one this exists to prevent.
     */
    private static WeakReference<ClientLevel> scannedLevel = new WeakReference<>(null);

    /** Whether anything is held for {@link #scannedLevel}; needed because the weak reference can clear on its own. */
    private static boolean hasScannedLevel;

    private PredatorHeatSourceScanner() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the sources packed as pairs of vec4 — {@code (minX, minY, minZ, heat)} then {@code (maxX, maxY, maxZ, 0)}
     * — with positions relative to the camera. Pure; the refresh is driven by {@link #sourceCount()}, which the
     * framework pushes earlier in the same frame.
     */
    public static float[] packedSources() {
        return packed;
    }

    /**
     * Refreshes the sources if due and returns how many boxes are packed.
     * <p>
     * ⚠ The work lives here rather than in {@link #packedSources()} on purpose. Ordinary uniforms are pushed before the
     * shader is bound and array uniforms after it, so this runs first every frame — which means the count always
     * matches the array, and the scan keeps running even if the array uniform fails to resolve. Driving it from the
     * array supplier once let a driver quirk in the uniform lookup silently disable the whole feature.
     */
    public static int sourceCount() {
        var minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        var camera = minecraft.gameRenderer.getMainCamera();

        forgetIfLevelChanged(level);

        if (level == null || !camera.isInitialized()) {
            return 0;
        }

        refreshIfDue(level, camera.getBlockPosition());
        repack(camera.getPosition().x, camera.getPosition().y, camera.getPosition().z);
        logForDebug();

        return Math.min(packed.length / 8, MAX_SOURCES);
    }

    /**
     * Drops everything and releases the scan map's storage.
     * <p>
     * Called automatically (Sep 27) whenever the level changes — leaving a world, switching server, changing dimension
     * — via {@link #forgetIfLevelChanged}, so a new level never shows the previous one's heat for the first quarter
     * second and the last scan's memory is not held after the player leaves. Detecting it here rather than hooking a
     * loader disconnect event keeps it in common code, identical on Fabric and NeoForge.
     */
    public static void reset() {
        WORKING = new Long2FloatOpenHashMap();
        published = List.of();
        packed = EMPTY;
        lastRefreshAtMillis = 0L;
    }

    /**
     * Resets when the current level is not the one the held sources came from. Covers a null level (back at the title
     * screen), a new world or server, a dimension change (the client builds a new ClientLevel for each), and an old
     * level the weak reference has already let go of.
     */
    private static void forgetIfLevelChanged(ClientLevel level) {
        if (!hasScannedLevel && level == null) {
            return;
        }

        if (hasScannedLevel && level != null && scannedLevel.get() == level) {
            return;
        }

        if (hasScannedLevel) {
            reset();
        }

        hasScannedLevel = level != null;
        scannedLevel = new WeakReference<>(level);
    }

    /**
     * Re-examines the volume if the timer is due. Skips whole chunk sections whose palette holds nothing hot, which is
     * nearly all of them.
     */
    private static void refreshIfDue(ClientLevel level, BlockPos cameraPos) {
        var now = System.currentTimeMillis();

        if (now - lastRefreshAtMillis < REFRESH_INTERVAL_IN_MILLIS) {
            return;
        }

        lastRefreshAtMillis = now;
        originX = cameraPos.getX();
        originY = cameraPos.getY();
        originZ = cameraPos.getZ();

        WORKING.clear();

        var minX = originX - RADIUS_HORIZONTAL;
        var maxX = originX + RADIUS_HORIZONTAL;
        var minY = Math.max(level.getMinBuildHeight(), originY - RADIUS_VERTICAL);
        var maxY = Math.min(level.getMaxBuildHeight() - 1, originY + RADIUS_VERTICAL);
        var minZ = originZ - RADIUS_HORIZONTAL;
        var maxZ = originZ + RADIUS_HORIZONTAL;

        var cursor = new BlockPos.MutableBlockPos();

        // ⚠⚠ CHUNKS ARE VISITED NEAREST-FIRST, AND THAT IS NOT COSMETIC. The loops used to run from minX/minZ —
        // the CORNER of the scan box, up to 96 blocks BEHIND the player — so once MAX_HOT_BLOCKS was reached the
        // set was full of whatever happened to be at the far edge and the lava lake directly in front never got
        // in at all. The lake went invisible in thermal.
        //
        // Sorting by distance to the camera first makes the cap do what it was always described as doing: keep
        // what is nearest and drop what is far enough that its fade would be negligible.
        var chunks = new ArrayList<long[]>();

        for (var chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
            for (var chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
                var dx = (chunkX << 4) + 8 - originX;
                var dz = (chunkZ << 4) + 8 - originZ;

                chunks.add(new long[] { chunkX, chunkZ, (long) dx * dx + (long) dz * dz });
            }
        }

        chunks.sort(Comparator.comparingLong(entry -> entry[2]));

        for (var entry : chunks) {
            if (WORKING.size() >= MAX_HOT_BLOCKS) {
                break;
            }

            var chunkX = (int) entry[0];
            var chunkZ = (int) entry[1];

            // getChunkNow never loads or generates — an unloaded chunk simply has no heat, which is correct.
            var chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);

            if (chunk == null) {
                continue;
            }

            var sections = chunk.getSections();

            // ⚠ Sections nearest the camera's own height first, for the same reason: a lake underfoot must win
            // over a ceiling 32 blocks up.
            var order = new ArrayList<Integer>();

            for (var sectionY = minY >> 4; sectionY <= maxY >> 4; sectionY++) {
                order.add(sectionY);
            }

            order.sort(Comparator.comparingInt(sectionY -> Math.abs(((sectionY << 4) + 8) - originY)));

            for (var sectionY : order) {
                var index = chunk.getSectionIndexFromSectionY(sectionY);

                if (index < 0 || index >= sections.length) {
                    continue;
                }

                var section = sections[index];

                // ⭐ The whole point: a palette question, not 4,096 block reads. Skips nearly every section.
                if (section.hasOnlyAir() || !section.maybeHas(PredatorHeatSources::isHeatSource)) {
                    continue;
                }

                scanSection(section, chunkX, sectionY, chunkZ, minX, maxX, minY, maxY, minZ, maxZ, cursor);

                if (WORKING.size() >= MAX_HOT_BLOCKS) {
                    break;
                }
            }
        }

        publish();
    }

    /** Reads a section that the palette says is worth reading, clipped to the search box. */
    private static void scanSection(
        LevelChunkSection section,
        int chunkX,
        int sectionY,
        int chunkZ,
        int minX,
        int maxX,
        int minY,
        int maxY,
        int minZ,
        int maxZ,
        BlockPos.MutableBlockPos cursor
    ) {
        var baseX = chunkX << 4;
        var baseY = sectionY << 4;
        var baseZ = chunkZ << 4;

        var fromX = Math.max(minX, baseX) - baseX;
        var toX = Math.min(maxX, baseX + 15) - baseX;
        var fromY = Math.max(minY, baseY) - baseY;
        var toY = Math.min(maxY, baseY + 15) - baseY;
        var fromZ = Math.max(minZ, baseZ) - baseZ;
        var toZ = Math.min(maxZ, baseZ + 15) - baseZ;

        for (var y = fromY; y <= toY; y++) {
            for (var z = fromZ; z <= toZ; z++) {
                for (var x = fromX; x <= toX; x++) {
                    var heat = PredatorHeatSources.heatOf(section.getBlockState(x, y, z));

                    if (heat <= 0.0F) {
                        continue;
                    }

                    // ⚠⚠ SURFACE ONLY. His call, and it is the single biggest win in this whole file: you can
                    // never see the inside of a lava lake, so scanning the volume was paying eight times over for
                    // blocks that are permanently hidden behind the ones on top of them.
                    //
                    // A nether lava sea 8 deep across the scan window is ~298,000 solid blocks against ~37,000 of
                    // exposed skin. Eight times fewer blocks, eight times fewer boxes, and vertical merging drops
                    // to almost nothing because a skin is one layer thick.
                    if (!isExposed(section, x, y, z)) {
                        continue;
                    }

                    cursor.set(baseX + x, baseY + y, baseZ + z);
                    WORKING.put(cursor.asLong(), heat);

                    // ⚠ Stops the scan dead rather than letting a lava sea fill memory. Checked in the innermost
                    // loop because one section can contribute 4,096 blocks on its own.
                    if (WORKING.size() >= MAX_HOT_BLOCKS) {
                        return;
                    }
                }
            }
        }
    }

    /**
     * Turns the hot blocks found by the last refresh into the smallest set of boxes that covers them <em>exactly</em>.
     * <p>
     * A single bounding box per pool was one entry but the wrong shape — it filled the notch of an L and squared off
     * every ragged edge, so the red region was visibly a rectangle rather than the lava. Minecraft blocks are
     * axis-aligned, so a pool's real footprint is always expressible as a handful of rectangles; this finds them.
     * <p>
     * Per height layer and per heat rating, greedy meshing takes each uncovered block, extends it as far as it can
     * along x, then extends that whole row as far as it can along z, and claims the rectangle. Identical rectangles in
     * adjacent layers are then merged vertically, so a deep pool costs no more than a shallow one. A square pool comes
     * out as one box, an L as two, a natural blob as a few — and every one of them traces the blocks themselves.
     */
    /**
     * {@return whether this hot block is on the OUTSIDE of its heat volume}
     * <p>
     * Tests up and the four horizontal neighbours. Anything with only hot neighbours is buried and contributes nothing
     * a player can ever see. <strong>⚠ DOWN IS NOT TESTED, DELIBERATELY</strong> The underside of a lake is only
     * visible from inside a cave beneath it, and including it would put the whole bottom face of every pool back into
     * the set — most of the saving, for a view almost nobody has. If someone reports lava reading cold from below, this
     * is the line. <strong>⚠ SECTION EDGES ARE KEPT UNCONDITIONALLY</strong> A neighbour outside this section would
     * need a chunk lookup per block, which is exactly the per-block cost this method exists to avoid. Accepting the
     * boundary keeps a one-block seam of extra data at every section face — invisible in the render, and cheap next to
     * the alternative.
     */
    private static boolean isExposed(LevelChunkSection section, int x, int y, int z) {
        if (x == 0 || x == 15 || y == 0 || y == 15 || z == 0 || z == 15) {
            return true;
        }

        return PredatorHeatSources.heatOf(section.getBlockState(x, y + 1, z)) <= 0.0F
            || PredatorHeatSources.heatOf(section.getBlockState(x - 1, y, z)) <= 0.0F
            || PredatorHeatSources.heatOf(section.getBlockState(x + 1, y, z)) <= 0.0F
            || PredatorHeatSources.heatOf(section.getBlockState(x, y, z - 1)) <= 0.0F
            || PredatorHeatSources.heatOf(section.getBlockState(x, y, z + 1)) <= 0.0F;
    }

    private static void publish() {
        var layers = new HashMap<LayerKey, LongOpenHashSet>();

        // ⚠ Primitive iteration — no Long or Float is ever created. The old loop boxed twice per entry on top of
        // whatever the map already held.
        for (var entry : WORKING.long2FloatEntrySet()) {
            var pos = entry.getLongKey();
            var key = new LayerKey(BlockPos.getY(pos), entry.getFloatValue());

            layers.computeIfAbsent(key, k -> new LongOpenHashSet()).add(pos);
        }

        var boxes = new ArrayList<Box>();

        for (var layer : layers.entrySet()) {
            meshLayer(layer.getKey(), layer.getValue(), boxes);
        }

        mergeVertically(boxes);

        if (boxes.size() > MAX_SOURCES) {
            // Nearest first. Anything dropped is far enough that its fade would be negligible anyway.
            boxes.sort(Comparator.comparingDouble(PredatorHeatSourceScanner::distanceToOriginSquared));
            boxes = new ArrayList<>(boxes.subList(0, MAX_SOURCES));
        }

        published = List.copyOf(boxes);
    }

    /** Greedy meshing of one height layer: grow along x, then grow that row along z, then claim. */
    /**
     * Greedy meshing of one height layer, on a COARSENED grid. <strong>⚠⚠ THE COARSE GRID IS WHY THE WHOLE LAKE IS
     * VISIBLE NOW</strong> Meshing at 1-block resolution, a ragged lava surface needs far more boxes than the shader
     * can hold — a 120x120 lake wants 230 and the uniform array holds {@value #MAX_SOURCES}. Only the nearest survived,
     * which is exactly the narrow band around the player he reported.
     * <p>
     * Snapping to {@value #GRID} blocks first collapses the same lake to a handful of boxes, because the ragged edge —
     * which is what was generating all those tiny separate patches — quantises away. The thermal bloom is heavily
     * blurred by the time it reaches the screen, so a four-block step in the outline is not visible.
     * <p>
     * ⚠ It rounds OUTWARD: any cell containing hot blocks becomes fully hot. A lake reads very slightly larger than it
     * is, which is the right direction to err — a heat bloom already spills past its source.
     */
    private static void meshLayer(LayerKey key, LongOpenHashSet blocks, List<Box> into) {
        // ⚠⚠ SMALL SOURCES ARE NEVER COARSENED. A torch or a campfire is a single block, and snapping it to a
        // 4-block grid would draw it four times its real size — the fix for lakes would have broken every small
        // heat source in the game. Only layers big enough to be terrain get quantised.
        var grid = blocks.size() >= COARSEN_ABOVE ? GRID : 1;
        var coarse = new LongOpenHashSet();

        for (var pos : blocks) {
            var cellX = Math.floorDiv(BlockPos.getX(pos), grid);
            var cellZ = Math.floorDiv(BlockPos.getZ(pos), grid);

            coarse.add(BlockPos.asLong(cellX, key.y(), cellZ));
        }

        var remaining = new LongOpenHashSet(coarse);
        var ordered = new ArrayList<>(coarse);
        ordered.sort(null);

        for (var start : ordered) {
            if (!remaining.contains(start)) {
                continue;
            }

            var x = BlockPos.getX(start);
            var z = BlockPos.getZ(start);
            var width = 1;

            while (remaining.contains(BlockPos.asLong(x + width, key.y(), z))) {
                width++;
            }

            var depth = 1;

            while (rowPresent(remaining, x, key.y(), z + depth, width)) {
                depth++;
            }

            for (var dx = 0; dx < width; dx++) {
                for (var dz = 0; dz < depth; dz++) {
                    remaining.remove(BlockPos.asLong(x + dx, key.y(), z + dz));
                }
            }

            // ⚠ Back into WORLD coordinates — x and z are grid cells at this point, not blocks.
            into.add(
                new Box(
                    (double) x * grid,
                    key.y(),
                    (double) z * grid,
                    (double) (x + width) * grid,
                    key.y() + 1.0,
                    (double) (z + depth) * grid,
                    key.heat()
                )
            );
        }
    }

    private static boolean rowPresent(Set<Long> remaining, int x, int y, int z, int width) {
        for (var dx = 0; dx < width; dx++) {
            if (!remaining.contains(BlockPos.asLong(x + dx, y, z))) {
                return false;
            }
        }

        return true;
    }

    /**
     * Collapses boxes that share a footprint and heat and sit directly on top of one another. <strong>⚠⚠ THIS METHOD
     * FROZE THE GAME IN THE NETHER, AND ITS SHAPE WAS WHY</strong> It was a {@code while (merged)} loop wrapping a
     * nested i/j scan that merged ONE pair and then {@code break outer} — so every successful merge threw away the scan
     * and restarted it from i=0. That is O(merges × n²). A ragged lava lake inside the 193×193×65 scan volume meshes to
     * well over a thousand boxes, and the render thread simply never came back: no exception, no crash report, just
     * "Not Responding".
     * <p>
     * ⚠ {@link #MAX_SOURCES} did NOT protect it. That cap is applied AFTER this method runs, so this always saw the
     * full uncapped list. <strong>The fix: bucket by footprint, then sweep each stack once</strong> Two boxes can only
     * merge if their x/z footprint and heat match exactly. Grouping on that first means the comparison never has to be
     * performed at all — one pass to bucket, one sort per bucket, one linear sweep.
     */
    private static void mergeVertically(List<Box> boxes) {
        if (boxes.size() < 2) {
            return;
        }

        var stacks = new HashMap<FootprintKey, List<Box>>();

        for (var box : boxes) {
            stacks
                .computeIfAbsent(
                    new FootprintKey(box.minX(), box.maxX(), box.minZ(), box.maxZ(), box.heat()),
                    key -> new ArrayList<Box>()
                )
                .add(box);
        }

        boxes.clear();

        for (var stack : stacks.values()) {
            if (stack.size() == 1) {
                boxes.add(stack.get(0));

                continue;
            }

            // ⚠ Sorted by height so one forward sweep can absorb each neighbour in turn. Unsorted, two boxes that
            // touch could sit at opposite ends of the bucket and never be compared.
            stack.sort(Comparator.comparingDouble(Box::minY));

            var current = stack.get(0);

            for (var index = 1; index < stack.size(); index++) {
                var next = stack.get(index);

                if (next.minY() <= current.maxY() + 1.0) {
                    current = new Box(
                        current.minX(),
                        Math.min(current.minY(), next.minY()),
                        current.minZ(),
                        current.maxX(),
                        Math.max(current.maxY(), next.maxY()),
                        current.maxZ(),
                        current.heat()
                    );
                } else {
                    boxes.add(current);
                    current = next;
                }
            }

            boxes.add(current);
        }
    }

    /**
     * ⚠ Heat is part of the key — two stacked pools at different temperatures must not silently merge.
     * <p>
     * ⚠ Doubles, because {@link Box} stores doubles. They hold whole block coordinates in practice so equality is exact
     * and safe as a map key; that is simply why these are not ints.
     */
    private record FootprintKey(
        double minX,
        double maxX,
        double minZ,
        double maxZ,
        float heat
    ) {}

    private record LayerKey(
        int y,
        float heat
    ) {}

    private static double distanceToOriginSquared(Box box) {
        var dx = Math.max(Math.max(box.minX() - originX, originX - box.maxX()), 0.0);
        var dy = Math.max(Math.max(box.minY() - originY, originY - box.maxY()), 0.0);
        var dz = Math.max(Math.max(box.minZ() - originZ, originZ - box.maxZ()), 0.0);

        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Positions are rebuilt relative to the camera every frame rather than stored that way: the camera moves between
     * refreshes, and camera-relative coordinates keep the shader's arithmetic away from large world coordinates where
     * float precision starts to bite.
     */
    private static void repack(double cameraX, double cameraY, double cameraZ) {
        var boxes = published;

        if (boxes.isEmpty()) {
            packed = EMPTY;

            return;
        }

        if (packed.length != boxes.size() * 8) {
            packed = new float[boxes.size() * 8];
        }

        for (var i = 0; i < boxes.size(); i++) {
            var box = boxes.get(i);
            var base = i * 8;

            packed[base] = (float) (box.minX() - cameraX);
            packed[base + 1] = (float) (box.minY() - cameraY);
            packed[base + 2] = (float) (box.minZ() - cameraZ);
            packed[base + 3] = box.heat();

            packed[base + 4] = (float) (box.maxX() - cameraX);
            packed[base + 5] = (float) (box.maxY() - cameraY);
            packed[base + 6] = (float) (box.maxZ() - cameraZ);
            packed[base + 7] = 0.0F;
        }
    }

    /**
     * Reports what the scan is finding, independent of whether the shader ever receives it. Enable with
     * {@code -Davp_predator.heatDebug=true}.
     */
    private static void logForDebug() {
        if (!"true".equalsIgnoreCase(System.getProperty(DEBUG_PROPERTY))) {
            return;
        }

        var now = System.currentTimeMillis();

        if (now - lastDebugLogAtMillis < DEBUG_INTERVAL_IN_MILLIS) {
            return;
        }

        lastDebugLogAtMillis = now;

        if (published.isEmpty()) {
            Predator.LOGGER.info("[avp_predator] Heat sources: none (hot blocks found: {})", WORKING.size());

            return;
        }

        var hottest = published.get(0);

        for (var box : published) {
            if (box.heat() > hottest.heat()) {
                hottest = box;
            }
        }

        Predator.LOGGER.info(
            "[avp_predator] Heat sources: {} boxes from {} blocks; hottest heat={} spans {}x{}x{}",
            published.size(),
            WORKING.size(),
            String.format("%.2f", hottest.heat()),
            String.format("%.0f", hottest.maxX() - hottest.minX()),
            String.format("%.0f", hottest.maxY() - hottest.minY()),
            String.format("%.0f", hottest.maxZ() - hottest.minZ())
        );
    }

    private record Box(
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ,
        float heat
    ) {}
}
