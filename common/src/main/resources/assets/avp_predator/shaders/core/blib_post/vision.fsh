#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D entityMask;
uniform sampler2D entityLightmap;
uniform sampler2D entityDrawData;
uniform sampler2D entitySpecular;
uniform sampler2D entityMaterialId;

uniform float sunAngle;
uniform float blindness;
uniform float darkness;
uniform float nightVision;
uniform vec2 outSize;
uniform float ultrawarmAmbient;

// World-space directions for the sun and moon, derived in BLibPostEffectStdUniforms from MC's renderSky transform
// stack. Both unit length: dot(viewRay, sunDir) is cos(angle from sun) and gives a clean sun-vs-moon disambiguation
// at celestial-body pixels regardless of where the body is on screen.
uniform vec3 sunDir;
uniform vec3 moonDir;

// Captured at the top of the level pass (NOT RenderSystem.getProjectionMatrix() at post time, which is the
// orthographic GUI projection — that would produce ortho-frustum rays instead of perspective rays and the
// sun-vs-moon ray test would fail for off-axis pixels.
uniform mat4 invProjMat;
uniform mat4 gbufferModelViewInverse;

// Vision-mode wipe uniforms. Driven by PredatorVisionTransition + PredatorThermalPostEffects.
// wipeLineX moves left-to-right in [0, 1]; the band of erosion is centered on this x, with thickness
// BAND_HALF_WIDTH on each side. oldMode/newMode are PredatorVisionMode ordinals (0=REGULAR, 1=THERMAL,
// 2=ELECTROMAGNETIC) telling the shader which coloring to use on each side of the band. When both modes match (no
// transition active), the wipe logic short-circuits and the shader renders the active mode uniformly.
uniform float wipeLineX;
uniform int oldMode;
uniform int newMode;

const int MODE_REGULAR = 0;
const int MODE_THERMAL = 1;
const int MODE_ELECTROMAGNETIC = 2;

in vec2 texCoord;

out vec4 fragColor;

// JCL's `fog_amount` is a shaderpack-internal term BLib doesn't capture. Default to 1.0 so blindness/darkness still
// dim correctly; can be replaced with a captured value if/when the broader render-pipeline data is wired in.
const float BLIB_FOG_AMOUNT = 1.0;

// Cold end is a very dark blue rather than pure black so unlit areas still read as "ambient cold" — pure black
// looks like missing data / GUI clear and breaks immersion in fully-dark caves.
const vec3 BLIB_THERMAL_COLD = vec3(0.0, 0.0, 0.06);

// Hot end: mostly white with a faint hint of red retained from the previous gradient stop, instead of pure
// (1, 1, 1) — pure white reads as eye-searing on common monitors when a full lava block fills the over-1.0 range.
// Mix factor is clamped (clamp(..., 0, 1)) so heat values >> 1.0 don't extrapolate past this color into negative
// red territory (which would clamp to high green+blue = cyan).
const vec3 BLIB_THERMAL_HOT = vec3(1.0, 0.92, 0.92);

vec3 thermalGradient(float t) {
    return t < 0.25 ? mix(BLIB_THERMAL_COLD, vec3(0.0, 0.0, 1.0), t * 4.0)
        : t < 0.50 ? mix(vec3(0.0, 0.0, 1.0), vec3(0.0, 1.0, 0.0), (t - 0.25) * 4.0)
        : t < 0.75 ? mix(vec3(0.0, 1.0, 0.0), vec3(1.0, 1.0, 0.0), (t - 0.50) * 4.0)
        : t < 1.00 ? mix(vec3(1.0, 1.0, 0.0), vec3(1.0, 0.0, 0.0), (t - 0.75) * 4.0)
        : mix(vec3(1.0, 0.0, 0.0), BLIB_THERMAL_HOT, clamp((t - 1.0) * 4.0, 0.0, 1.0));
}

// Reconstructs the world-space view ray for a screen-space pixel by pushing it to the far plane in NDC, inverting
// the projection to view space, then inverse-viewing to world space. Returns a unit vector from the camera through
// the pixel. Used only for sky/celestial pixels — for non-sky pixels the actual depth would matter and we'd need
// the depth texture.
vec3 reconstructSkyRay(vec2 uv) {
    vec4 ndc = vec4(uv * 2.0 - 1.0, 1.0, 1.0);
    vec4 viewH = invProjMat * ndc;
    vec3 viewDir = viewH.xyz / viewH.w;
    vec3 worldDir = (gbufferModelViewInverse * vec4(viewDir, 0.0)).xyz;
    return normalize(worldDir);
}

// Stylized procedural thermal sky.
//
// Two regions, distinguished by the celestial-luma channel captured into entityDrawData.r by the patcher:
//   - Ambient sky (drawDetail ≈ 0 — no celestial body drawn here): blue at night → green at day, smooth dawn/dusk.
//   - Celestial body (drawDetail = SRC_ALPHA * texture_luma): the sun/moon texture, recolored via the heat gradient
//     so the texture's BRIGHTNESS drives the heat. Bright sun core → white-hot; bright moon phase → yellow; dim
//     halo regions of the texture → fade smoothly back to the ambient sky baseline.
//
// Held items are bypassed earlier in main() and rendered as their original color.
//
// Why drawDetail instead of the framebuffer's source luminance? MC draws sun/moon with ADDITIVE blending
// (SRC_ALPHA, ONE) — at sun-quad pixels where the texture itself is black (the corners and outer regions of
// vanilla sun.png), the framebuffer color attachment ends up holding the underlying SKY color (since additive
// preserves dest), so a `srcLuma` read at those pixels returns the sky luminance, not the texture darkness. That
// would push the recolor above baseHeat at every sun-quad corner and produce a hard rectangular boundary against
// the surrounding sky. The patcher captures the raw texture luma into entityDrawData.r BEFORE the modulator
// multiply, so drawDetail is genuinely 0 at black-texture pixels regardless of what the framebuffer holds — and
// the corners then read as baseHeat exactly, indistinguishable from the surrounding sky.
float computeSkyHeat(vec2 uv, float drawDetail) {
    // Time-of-day factor from sun world-space height. 1 at noon, 0 at midnight, smooth transition while the sun
    // is within ±~9° of the horizon (wider thresholds → longer twilight).
    float dayFactor = smoothstep(-0.15, 0.15, sunDir.y);

    // Ambient sky baseline: blue (heat 0.25) at night, green (heat 0.5) at day. Lerping through heat gives a clean
    // dawn/dusk pass through the gradient's natural blue→green transition rather than fading colors directly.
    float baseHeat = mix(0.25, 0.5, dayFactor);

    // Sun vs moon disambiguation: the celestial body in front of the camera is whichever direction the view ray
    // is more aligned with. cosSun > cosMoon → looking at sun's hemisphere, otherwise moon. Cheap and bullet-proof
    // since sunDir = -moonDir (always exactly opposite hemispheres in MC's celestial sphere).
    vec3 ray = reconstructSkyRay(uv);
    float cosSun = dot(ray, sunDir);
    float cosMoon = dot(ray, moonDir);
    float celestialTarget = (cosSun > cosMoon) ? 1.25 : 0.75;

    // Direct texture-brightness → heat mapping. Linear so the texture's natural luma falloff drives the radial
    // gradient across the body. drawDetail is exactly 0 at non-celestial sky pixels (cleared) and at black
    // texture pixels (corners), so heat = baseHeat at both — no rectangular boundary against the ambient sky.
    return mix(baseHeat, celestialTarget, clamp(drawDetail, 0.0, 1.0));
}

// NOTE (glsl-processor compat): these three functions used to be FORWARD-DECLARED here with their bodies at the
// bottom, so main() read top-down. Veil 4.x recompiles every vanilla-pipeline shader through glsl-processor, and
// that library (0.2.3) parses a bare prototype into a function node with a NULL body, then NPEs writing it back
// ("GlslNodeList.iterator() ... this.body is null" — the white-screen chain: skipped shader -> GL_INVALID_OPERATION
// flood -> minimap GL-error check crashes -> white screen). GLSL needs no prototypes when definitions precede use,
// so the bodies now sit here, above main(). Do not reintroduce forward declarations in any BLib post shader.

vec3 computeThermal(vec3 src, vec3 srcDim, float mask, vec4 drawData, vec4 specular, int materialId, float dimFactor, float backgroundFlag) {
    // Held items: render uniformly as cold-zone background regardless of position lighting (no IR signature). Mirrors
    // EM's "held items don't show up" behavior — without this, held items inherit terrain-heat from their wielder's
    // block-light and read as warm in lit areas. Held items aren't biological tissue and shouldn't have a thermal
    // signature, so we route them to BLIB_THERMAL_COLD + a srcLuma-driven blue underlay (matching the cold zone of
    // the gradient) instead of the position-driven heat formula.
    if (mask > 0.8125 && mask < 0.9375) {
        float heldSrcLuma = dot(src, vec3(0.299, 0.587, 0.114));
        float heldLifted = pow(clamp(heldSrcLuma, 0.0, 1.0), 0.5);
        vec3 heldDetail = vec3(0.0, 0.0, 1.0) * heldLifted * 0.5;
        return (BLIB_THERMAL_COLD + heldDetail) * dimFactor;
    }

    // Entity-mask pixels with backgroundFlag set are reclassified as terrain so they pick up the world heat formula
    // (low heat → dark blue with srcLuma-driven coldDetail underlay) instead of the foreground entity formula's
    // 0.5 baseline. Visible-tagged entities have backgroundFlag = 0 and stay classified as entity.
    float catEntityRaw = step(0.75, mask);
    float catEntity   = catEntityRaw * (1.0 - backgroundFlag);
    float catTerrain  = step(0.375, mask) - catEntity;
    float catParticle = step(0.125, mask) - step(0.375, mask);
    // Sky/celestial — everything below the particle threshold. Includes both pure sky (mask=0) and celestial
    // bodies (mask ≈ 0.0625). The two are distinguished inside computeSkyHeat by drawDetail (drawData.r):
    // 0 for pure sky, non-zero for celestial-body texture pixels.
    float catSky      = 1.0 - step(0.125, mask);

    // entityDrawData payload — all biome/dimension-independent:
    //   R = untinted Sampler0.r (texture detail, no lightmap tint)
    //   G = block-light coord — for entities, *per-bone* via the BLibPerBoneLight mixin (each bone's packedLight
    //       is sampled at the bone's actual world position, so different bones of the same mob can read different
    //       block-light values when the mob spans a lighting boundary). For terrain/particles this is the standard
    //       per-vertex UV2.x.
    //   B = sky-light coord — captured but intentionally unused in heat formulas (kept for the debug visualization
    //       at mode 6). Sun-warmed surfaces aren't measurably hotter in IR vision; including sky light made noon
    //       fields read as warm green/yellow which doesn't match thermal-vision expectations.
    //   A = face-light (Light0/Light1 dot for entities, synthetic key-light for terrain, 1.0 for particles)
    float drawDetail = drawData.r;
    // Block-light is floored by the dimension's ultrawarm ambient (≈ 0.467 in the Nether, 0 elsewhere). The Nether
    // is officially "ultra-warm" so even fully-occluded fragments register as green-ambient hot — caves still cold
    // in the overworld, but in the Nether everything reads warm.
    float blockLight = max(drawData.g, ultrawarmAmbient);
    float faceLight = drawData.a;

    // Emission term — biome-independent. specular.a holds either:
    //   (a) A LabPBR emission sample if the entity shader declared an EntitySpecular sampler, or
    //   (b) The warm-color heuristic the patcher precomputed when no PBR sampler was bound.
    // The smoothstep gate by raw block light suppresses the heuristic on warm-colored *non-emissive* fragments.
    float emission = specular.a * smoothstep(0.86, 1.0, blockLight);

    // Heat is driven entirely by block light (and emission for true heat sources). Sky light + sun terms removed:
    // sunlit terrain isn't really hotter than shaded terrain in IR, and including sun heat made daytime outdoor
    // scenes wash out as warm everywhere.
    float entityHeatNonPBR =
        0.5
        + 0.40 * blockLight
        + 0.60 * emission;

    float entityHeatPBR =
        min(1.0, emission + blockLight) * (1.0 + specular.g * (blockLight - 0.5))
        + (0.5 - 0.5 * specular.g);

    // Only formulate as PBR when real LabPBR roughness is present.
    float pbrWeight = clamp(specular.g, 0.0, 1.0);
    float entityHeat = mix(entityHeatNonPBR, entityHeatPBR, pbrWeight);

    // Terrain: block light drives heat across the full gradient range so torches/lava produce smooth radiance falloff.
    // For entity-mask pixels reclassified as terrain via backgroundFlag (catEntity zeroed out above), drawData.g
    // carries the per-bone-light-boosted block-light coord that the consumer mod pushed for visibility on the OTHER
    // side of a wipe — feeding that boost into terrainHeat would produce a spurious thermal-warm silhouette of the
    // entity on the side where it's supposed to read as world-cold (e.g. an EM-visible mob glowing as if it were
    // thermal-tagged on the thermal side of a T→EM transition). Override to 0 so those pixels render like real
    // terrain in shadow. Real terrain pixels (mask.r ≈ 0.5) have catEntityRaw = 0 and pass through unchanged.
    float isEntityReclassified = catEntityRaw * backgroundFlag;
    float terrainBlockLight = mix(blockLight, 0.0, isEntityReclassified);
    float terrainHeat = terrainBlockLight + 1.50 * emission;

    float particleHeat = blockLight + 0.80 * emission;

    // Procedural sky+celestial heat. Cheap to compute even on non-sky pixels; gated by catSky below.
    // drawDetail (= drawData.r) is the celestial-body texture luminance captured by the patcher's CELESTIAL
    // path; 0 at non-celestial sky pixels because the cleared auxiliary buffer is 0 and the passthrough shaders
    // explicitly write 0 there.
    float skyHeat = computeSkyHeat(texCoord, drawDetail);

    // JCL-style per-material heat additions.
    float materialBoost =
        (materialId == 1 ? 0.45 : 0.0)
        + (materialId == 2 ? 0.30 : 0.0)
        + (materialId == 3 ? 0.55 : 0.0)
        + (materialId == 4 ? 0.20 : 0.0)
        + (materialId == 5 ? 0.25 : 0.0);

    float heat =
        catEntity   * entityHeat
        + catTerrain  * terrainHeat
        + catParticle * particleHeat
        + catSky      * skyHeat
        + materialBoost;

    // Detail subtraction adds texture-level variation but at full strength it cancels out the heat of bright
    // emissive textures. Fade detail subtraction out as emission rises, AND skip it for sky pixels — the sky
    // procedural path already encodes texture detail via srcLuma; subtracting drawDetail from it would distort
    // the sun's bright core back toward base, defeating the purpose.
    float detailWeight = 0.30 * (1.0 - emission) * (1.0 - catSky);
    heat = max(heat - drawDetail * detailWeight, 0.0);

    vec3 heatVis = thermalGradient(heat);

    // Cold-area visibility underlay — only for non-sky pixels. At low heat the gradient color is a near-uniform
    // dark blue, which obliterates structure (walls/floor/edges) and makes navigation hard. Add a pure-blue underlay
    // scaled by source luminance so block edges and lit-side faces brighten the blue. Sky pixels are excluded
    // (catSky multiplier) — their heat already comes from a clean procedural source, and adding a luma-scaled blue
    // on top would re-introduce source-color leak through the sky region (the green ring around unloaded chunks,
    // faint banding from celestial-body texture luminance, etc.).
    float srcLuma = dot(src, vec3(0.299, 0.587, 0.114));
    float liftedLuma = pow(clamp(srcLuma, 0.0, 1.0), 0.5);
    float coldFade = (1.0 - smoothstep(0.0, 0.5, heat)) * (1.0 - catSky);
    vec3 coldDetail = vec3(0.0, 0.0, 1.0) * liftedLuma * 0.5 * coldFade;

    vec3 outColor = heatVis + coldDetail;
    return outColor * dimFactor;
}

// Electromagnetic vision: visible entities (EM_VISIBLE tag — end-realm beings by default) render with their texture
// pattern intact but tinted green; the world reads as dark green and ignores block light entirely (torches/lava do
// not brighten the world in EM). Held items pass through unchanged so they remain readable.
//
// Entity coloring strategy (mirrors thermal's "tint + texture detail" feel):
//   1. Compute source luminance and lift it via sqrt so dark texture pixels don't disappear.
//   2. Multiply the green tint by mix(0.2, 1.0, liftedLuma) — texture pattern survives, no pitch-black pixels.
//   3. Modulate by per-bone block-light + face-light so the entity has dimensional shading.
//
// World coloring strategy (mirrors thermal's BLIB_THERMAL_COLD + coldDetail underlay, in green):
//   1. Constant dark-green base so unlit / no-detail pixels still read as something.
//   2. srcLuma-driven green underlay so block edges and texture variation show up as brighter green.
//   3. Block-light / face-light deliberately NOT consumed — the world looks the same near a torch as in a dark cave.
vec3 computeEm(vec3 src, vec3 srcDim, float mask, vec4 drawData, float dimFactor, float backgroundFlag) {
    // Held items used to short-circuit to srcDim (passthrough). Now main() merges held-item state into
    // backgroundFlag so they fall through to the world coloring branch below — held items blend with the world
    // instead of giving away their wielder's position.
    float catEntity = step(0.75, mask) * (1.0 - backgroundFlag);
    float srcLuma = dot(src, vec3(0.299, 0.587, 0.114));
    float liftedLuma = pow(clamp(srcLuma, 0.0, 1.0), 0.5);

    // ENTITY: green-tinted, texture-preserving, lighting-modulated.
    float blockLight = max(drawData.g, ultrawarmAmbient);
    float faceLight = drawData.a;
    float entityLighting = 0.45 + 0.35 * blockLight + 0.20 * faceLight;
    float entityLuma = mix(0.2, 1.0, liftedLuma);
    vec3 emEntityColor = vec3(0.0, 1.0, 0.2) * entityLuma * entityLighting;

    // WORLD: dark-green base + srcLuma-driven green underlay (mirrors thermal's BLIB_THERMAL_COLD + coldDetail in
    // green). Block-light intentionally unused — torches/lava don't change EM's world appearance. Range: 0.02
    // (unlit / black source) to 0.18 (bright source pixel). The dark floor is intentional — it's what gives
    // EM-visible entities (rendered through the entity branch above) their vivid contrast against the world.
    const vec3 EM_WORLD_BASE = vec3(0.0, 0.02, 0.0);
    vec3 emWorldDetail = vec3(0.0, 1.0, 0.0) * liftedLuma * 0.16;
    vec3 emWorldColor = EM_WORLD_BASE + emWorldDetail;

    vec3 result = catEntity * emEntityColor + (1.0 - catEntity) * emWorldColor;
    return result * dimFactor;
}

// Pick the right pre-computed RGB for a given mode. Centralizes the mode-dispatch so adding a new vision is just
// "compute its rgb and add a case here + a forward decl."
vec3 selectMode(int mode, vec3 srcDim, vec3 thermalRgb, vec3 emRgb) {
    if (mode == MODE_THERMAL) return thermalRgb;
    if (mode == MODE_ELECTROMAGNETIC) return emRgb;
    return srcDim;
}

void main() {
    vec3 src = texture(DiffuseSampler, texCoord).rgb;
    vec2 maskSample = texture(entityMask, texCoord).rg;
    float mask = maskSample.r;
    // mask.g packs two background-entity lanes from BLib: lane A (oldMode side of the wipe) contributes 0.25, lane
    // B (newMode side) contributes 0.5. The four combinations land at exactly 0.0/0.25/0.5/0.75 under NEAREST
    // sampling of the RG8 attachment. Decoding via step() pairs cleanly because the thresholds (0.125/0.375/0.625)
    // sit halfway between adjacent encoded values:
    //   0.0  → laneA=0, laneB=0   (visible under both modes)
    //   0.25 → laneA=1, laneB=0   (background under old only)
    //   0.5  → laneA=0, laneB=1   (background under new only)
    //   0.75 → laneA=1, laneB=1   (background under both)
    // Each side of the wipe applies its own lane's flag below so an entity visible under exactly one mode is
    // correctly foregrounded on that side and backgrounded on the other — without the union "background wins"
    // compromise that the previous single-flag scheme required.
    float maskG = maskSample.g;
    float bgLaneA = step(0.125, maskG) - step(0.375, maskG) + step(0.625, maskG);
    float bgLaneB = step(0.375, maskG);
    vec4 drawData = texture(entityDrawData, texCoord);
    vec4 specular = texture(entitySpecular, texCoord);
    int materialId = int(round(texture(entityMaterialId, texCoord).r * 255.0));

    float dimFactor = 1.0 - max(blindness, darkness) * BLIB_FOG_AMOUNT;
    vec3 srcDim = src * dimFactor;

    // Category breakdown of the mask byte. Values written by BLibEntityShaderPatcher.Category, plus the special
    // 0.875 "held item" written when BlibHeldItem is set during first-person hand or third-person ItemInHandLayer
    // draws. Held-item fragments are routed through the background-entity branch so they blend with the world
    // (dark blue thermal / dark green EM) instead of standing out as foreground entities — held items shouldn't
    // give away your position in the predator vision.
    //   0.875  = held item     → forced through world-coloring branch (treated as background)
    //   1.000  = entity        → body heat + lighting (or world coloring if backgroundFlag set)
    //   0.500  = terrain       → lighting only (no body heat)
    //   0.250  = particle      → ambient block light only
    //   0.0625 = celestial     → sun/moon texture recolored via heat gradient + ambient-sky fade
    //   0.000  = sky/passthrough → ambient sky baseline only
    bool isHeldItem = mask > 0.8125 && mask < 0.9375;

    // Held items are always background (no IR / EM signature); force both lanes high regardless of mask.g so each
    // side's coloring routes through the world branch in both computeThermal (catEntity zeroed → terrain heat
    // formula) and computeEm (catEntity zeroed → emWorldColor).
    float heldBoost = isHeldItem ? 1.0 : 0.0;
    float effectiveBgOld = max(bgLaneA, heldBoost);
    float effectiveBgNew = max(bgLaneB, heldBoost);

    // Compute each side's coloring with its own per-lane flag — old uses laneA (right of wipe), new uses laneB
    // (left of wipe). That's two extra computeThermal/computeEm calls compared to the old single-flag layout but
    // it's the only way to give an entity that's visible under exactly one mode the right routing on each side
    // simultaneously. When oldMode == newMode (no transition active) bgLaneA == bgLaneB and the two halves
    // collapse to the same result, so the wipe short-circuits below.
    vec3 thermalRgbOld = computeThermal(src, srcDim, mask, drawData, specular, materialId, dimFactor, effectiveBgOld);
    vec3 emRgbOld = computeEm(src, srcDim, mask, drawData, dimFactor, effectiveBgOld);
    vec3 oldRgb = selectMode(oldMode, srcDim, thermalRgbOld, emRgbOld);

    vec3 thermalRgbNew = computeThermal(src, srcDim, mask, drawData, specular, materialId, dimFactor, effectiveBgNew);
    vec3 emRgbNew = computeEm(src, srcDim, mask, drawData, dimFactor, effectiveBgNew);
    vec3 newRgb = selectMode(newMode, srcDim, thermalRgbNew, emRgbNew);

    if (oldMode == newMode) {
        fragColor = vec4(oldRgb, 1.0);
        return;
    }

    // BAND_HALF_WIDTH: half-thickness of the erosion band in normalized screen-X. 0.05 → band spans 10% of screen.
    // Reasonably thick so the dislocation/red-tint/dissolve has room to develop visually as the line moves.
    const float BAND_HALF_WIDTH = 0.05;
    float distFromLine = texCoord.x - wipeLineX;

    if (distFromLine > BAND_HALF_WIDTH) {
        // Right of the band — pure old vision.
        fragColor = vec4(oldRgb, 1.0);
        return;
    }

    if (distFromLine < -BAND_HALF_WIDTH) {
        // Left of the band — pure new vision.
        fragColor = vec4(newRgb, 1.0);
        return;
    }

    // Inside the band: erosion. phase = 0 just inside the right edge, 1 just inside the left edge.
    float phase = (BAND_HALF_WIDTH - distFromLine) / (2.0 * BAND_HALF_WIDTH);

    // Per-pixel deterministic noise: stable across frames so a given pixel always behaves the same way as the band
    // sweeps over it. Two independent samples for 2D displacement direction.
    float pixelNoise = fract(sin(dot(texCoord * vec2(343.0, 191.0), vec2(12.9898, 78.233))) * 43758.5453);
    float pixelNoise2 = fract(sin(dot(texCoord * vec2(217.0, 451.0), vec2(45.164, 91.456))) * 21345.789);

    // Dislocation: peaks in the middle of the band, smoothly tapers to 0 at the edges. The displaced sample comes
    // from DiffuseSampler — the unaltered scene color. For REGULAR↔THERMAL that's the correct "old vision" source
    // when going REGULAR→THERMAL; going THERMAL→REGULAR the displacement is technically against the wrong source,
    // but the heavy red tint dominates the visible band so the discrepancy isn't noticeable.
    float dislocateAmount = sin(phase * 3.14159) * 0.025;
    vec2 displacedCoord = clamp(
        texCoord + vec2(pixelNoise - 0.5, pixelNoise2 - 0.5) * dislocateAmount,
        vec2(0.001), vec2(0.999)
    );
    vec3 displacedSrc = texture(DiffuseSampler, displacedCoord).rgb * dimFactor;

    // Red tint envelope: 0 at the edges of the band, peaks at the middle. Pixels in the center of the band read as
    // bright red ash; pixels just inside the right edge are barely tinted; pixels just inside the left edge are
    // already dissolving (see step() below).
    vec3 ashRed = vec3(1.0, 0.05, 0.02);
    float redness = sin(phase * 3.14159) * 0.95;
    vec3 ashy = mix(displacedSrc, ashRed, redness);

    // Per-pixel dissolve: each pixel has a stable threshold; once phase exceeds the threshold, the pixel "dies"
    // and is replaced with the new vision. Low-noise pixels die early, high-noise pixels survive longer — the
    // result is a granular, stippled erosion frontier rather than a hard edge.
    float dissolved = step(pixelNoise, phase);
    vec3 finalRgb = mix(ashy, newRgb, dissolved);

    fragColor = vec4(finalRgb, 1.0);
}

