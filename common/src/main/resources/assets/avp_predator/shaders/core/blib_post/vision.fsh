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

// Block heat sources, supplied by PredatorHeatSourceScanner: xyz = position relative to the camera, w = heat rating
// on the same scale as thermalGradient's input. Authored per block rather than derived from light level, so a sea
// lantern can be bright and cold while a lit furnace is hot behind a door. Under Sodium there is no terrain block
// light in the auxiliary attachments at all, so this is the only source of world heat.
uniform sampler2D depthtex0;
uniform vec4 heatSources[64];
uniform int heatSourceCount;
uniform float heatSourceRange;
uniform int heatDebugView;
uniform int cameraSubmerged;
uniform float rainLevel;

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
    // Submerged there is no sky to read — what reaches the mask as "sky" is water, unloaded distance, or the surface
    // seen from below, none of which are warm. Ambient sky heat is green in daylight, so without this the waterline
    // and any loading chunks blaze through an otherwise cold scene.
    if (cameraSubmerged != 0) {
        return 0.0;
    }

    // Time-of-day factor from sun world-space height. 1 at noon, 0 at midnight, smooth transition while the sun
    // is within ±~9° of the horizon (wider thresholds → longer twilight).
    float dayFactor = smoothstep(-0.15, 0.15, sunDir.y);

    // Ambient sky baseline: blue (heat 0.25) at night, green (heat 0.5) at day. Lerping through heat gives a clean
    // dawn/dusk pass through the gradient's natural blue→green transition rather than fading colors directly.
    float baseHeat = mix(0.25, 0.5, dayFactor);

    // Overcast sky reads cool: cloud cover sits far colder than open daylight sky, so a downpour should mute the
    // ambient rather than leave it blazing green. This also settles the rain streaks themselves — weather draws with a
    // passthrough shader and arrives here as sky, so it takes this same value.
    //
    // 0.28 is deliberately a shade above the night baseline of 0.25: a rained-out sky should read like night, or a
    // touch brighter, rather than darker than one. Raise toward 0.35 for a paler overcast, drop to 0.25 to match night
    // exactly.
    baseHeat = mix(baseHeat, 0.28, clamp(rainLevel, 0.0, 1.0));

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
    // ⭐⭐ THE CELESTIAL RECOLOUR IS GATED ON ACTUALLY POINTING AT THE SUN OR MOON. drawDetail alone was not enough:
    // anything the sky stage draws with texture luma — the horizon haze band among it — inherited the 1.25/0.75
    // celestial target and produced a warm strip hugging the horizon in EVERY compass direction, which is the last
    // thing left over from the blend hunt and reads as an immersion break rather than a heat source.
    //
    // The body's own quad subtends roughly 8-9 degrees, so alignment inside ~0.955 is generously the disc plus its
    // glow. ⚠ RAISE 0.955 IF THE BAND SURVIVES; LOWER IT IF THE SUN STOPS READING RED — that pair is the whole dial.
    float celestialAlignment = max(cosSun, cosMoon);
    float celestialGate = smoothstep(0.955, 0.975, celestialAlignment);

    return mix(baseHeat, celestialTarget, clamp(drawDetail, 0.0, 1.0) * celestialGate);
}

// NOTE (glsl-processor compat): these three functions used to be FORWARD-DECLARED here with their bodies at the
// bottom, so main() read top-down. Veil 4.x recompiles every vanilla-pipeline shader through glsl-processor, and
// that library (0.2.3) parses a bare prototype into a function node with a NULL body, then NPEs writing it back
// ("GlslNodeList.iterator() ... this.body is null" — the white-screen chain: skipped shader -> GL_INVALID_OPERATION
// flood -> minimap GL-error check crashes -> white screen). GLSL needs no prototypes when definitions precede use,
// so the bodies now sit here, above main(). Do not reintroduce forward declarations in any BLib post shader.

// Camera-relative world position of a pixel, from its depth. Unlike reconstructSkyRay this uses the real depth, so it
// lands on the surface actually drawn rather than on the far plane.
vec3 reconstructWorldOffset(vec2 uv, float depth) {
    vec4 ndc = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 view = invProjMat * ndc;
    view /= view.w;

    return (gbufferModelViewInverse * vec4(view.xyz, 1.0)).xyz;
}

// Heat contributed by nearby block sources. Sources combine by MAX, never by sum: a lava pool is exactly as hot as
// lava, not hotter in the middle, and two torches side by side do not read hotter than one.
float blockHeatAt(vec3 worldOffset) {
    float best = 0.0;

    // ⚠⚠ RANGE GUARD, AND IT IS NOT DEFENSIVE — IT FIXES A VISIBLE BUG. worldOffset comes from reconstructing a
    // pixel's position out of its DEPTH, so at the horizon it lands at the far plane: hundreds or thousands of blocks
    // out. The scanner only ever gathers sources within HEAT_SOURCE_RANGE (32 blocks) of the camera, so no point that
    // far away can legitimately sit inside any box — but at that magnitude the box-distance arithmetic below loses
    // precision, and `max(lo - p, p - hi)` collapses toward zero for a point nowhere near a source.
    //
    // ⭐ MEASURED SIGNATURE: a smooth heat ramp hugging the horizon, brightest dead ahead (furthest, worst precision)
    // and fading symmetrically toward the screen edges (nearer, better behaved) — in EVERY compass direction, over
    // open ocean with the nearest heat source far out of range. A misclassification would paint flat colour; only a
    // continuous distance term produces a gradient.
    //
    // 64 is double the scan radius, so a source at the very edge of range with a large merged box still resolves
    // normally. Squared compare to skip the square root.
    if (dot(worldOffset, worldOffset) > 64.0 * 64.0) {
        return 0.0;
    }

    // Sources arrive in PAIRS: [min.xyz, heat] then [max.xyz, unused]. Each is the bounding box of a merged run of
    // connected same-heat blocks, so a whole lava pool is one entry however large it is, and a torch is a 1x1x1 box.
    for (int i = 0; i < heatSourceCount; i++) {
        vec4 lo = heatSources[i * 2];
        vec4 hi = heatSources[i * 2 + 1];

        // Distance from the point to the box: zero anywhere inside it. That is what makes every block of a pool read
        // at full heat while the fade radiates from the pool's real surface rather than from a point in its middle.
        vec3 outside = max(max(lo.xyz - worldOffset, worldOffset - hi.xyz), vec3(0.0));
        float dist = length(outside);

        float falloff = clamp((heatSourceRange - dist) / heatSourceRange, 0.0, 1.0);

        // Squared, so heat drops away sharply instead of washing a warm haze over everything within range.
        best = max(best, lo.w * falloff * falloff);
    }

    return best;
}

// How deep inside a creature's silhouette this pixel sits, from 0 at the outline to 1 in the thick of the body.
//
// Real thermal imagery shows a hot core cooling toward the extremities: the torso and head glow while legs, arms, ears
// and tails run visibly cooler, because thin limbs shed heat and have little mass to hold it. Without this every pixel
// of a creature reads the same temperature and the brightest parts end up wherever the texture happens to be lightest
// — which is how a cow came out uniformly molten with white hooves, exactly inverted from life.
//
// Measuring the silhouette gets there without needing skeletal data: a torso has depth in every direction, an arm is
// thin whichever way you look. Two rings of eight taps, and the search radius scales with how close the creature is —
// a fixed pixel radius vanishes against a creature filling the screen and swallows one at distance.
float entityCoreness(vec2 uv, float viewDistance) {
    vec2 texel = 1.0 / vec2(textureSize(entityMask, 0));

    // The reach has to span the BODY, not trace a rim. A radius smaller than the creature's half-width leaves
    // everything but a thin outline reading as full interior, which is what kept a cow uniformly red with a yellow
    // edge. ~90px at 3 blocks down to ~13px at 20.
    float radius = clamp(260.0 / max(viewDistance, 1.0), 8.0, 90.0);
    float hits = 0.0;

    for (int ring = 0; ring < 3; ring++) {
        float r = radius * (ring == 0 ? 0.4 : (ring == 1 ? 0.7 : 1.0));
        float d = r * 0.7071;

        hits += step(0.75, texture(entityMask, uv + vec2( r, 0.0) * texel).r);
        hits += step(0.75, texture(entityMask, uv + vec2(-r, 0.0) * texel).r);
        hits += step(0.75, texture(entityMask, uv + vec2(0.0,  r) * texel).r);
        hits += step(0.75, texture(entityMask, uv + vec2(0.0, -r) * texel).r);
        hits += step(0.75, texture(entityMask, uv + vec2( d,  d) * texel).r);
        hits += step(0.75, texture(entityMask, uv + vec2( d, -d) * texel).r);
        hits += step(0.75, texture(entityMask, uv + vec2(-d,  d) * texel).r);
        hits += step(0.75, texture(entityMask, uv + vec2(-d, -d) * texel).r);
    }

    float raw = hits / 24.0;

    // ⚠⚠ THE REMAP IS THE WHOLE TRICK. On a convex body an edge pixel has half its ring outside, so the raw score
    // bottoms out near 0.5 and never reaches 0 — which compressed the entire gradient into the top half of its range
    // and left everything red-to-orange. Rescaling 0.5..1.0 onto 0..1 is what actually lets a limb read cold.
    return smoothstep(0.5, 0.95, raw);
}

// The heat band a tier is allowed to occupy, as (min, max) on the same scale as thermalGradient's input
// (0.25 blue, 0.5 green, 0.75 yellow, 1.0 red).
//
// ⚠⚠ THIS IS THE DIAL THAT STOPS BODY SHAPE FROM OVERRIDING BIOLOGY. Without it the core-to-extremity falloff scales
// the base heat by how deep in the body a pixel sits, and the tier bonus is only added afterwards — so a solid blob
// climbs to the top of the scale whatever it is made of, and a thin body collapses to the bottom. Measured: a squid
// (tier 4, +0.20) reached 0.98 at its core and rendered RED, while a chicken (tier 1, +0.45) sat at 0.55 and rendered
// green. A cold-blooded animal must not get hotter for being fat, and a warm-blooded one must not go cold for being
// skinny.
//
// ⭐ TIER 4 WAS TIGHTENED FROM [0.26, 0.52] TO [0.22, 0.40] — [stated] "can we make the squid specifically more on the
// bluer side". 0.52 is exactly green, so a solid squid sat right on it; 0.40 keeps the whole band below the blue-green
// transition, so even a squid's mantle core reads as the coldest living thing in view. Fish, guardians and glow squid
// share the tier and move with it, which is correct — they are all invertebrates or cold-blooded and should read
// colder than the turtles and frogs in tier 5. ⚠ Do not push the ceiling below ~0.30 or the tier stops being
// distinguishable from unclassified background.
//
// ⭐ The large-mammal band is deliberately wide enough to be a NO-OP on what already looks right: a well-lit cow spans
// roughly 0.65 at the hooves to 1.33 at the shoulder today, and both sit inside 0.62..1.40 untouched.
//
// Tier 0 (ambient — undead, arthropods, constructs, xenomorphs) is left effectively unclamped: it has no bonus to
// contain, and those entities are normally routed to the background branch anyway.
vec2 tierBand(int tier) {
    if (tier == 1) return vec2(0.55, 1.15);   // warm-blooded
    if (tier == 2) return vec2(0.45, 0.95);   // warm-blooded small
    if (tier == 3) return vec2(0.62, 1.40);   // warm-blooded large — today's range, unchanged
    if (tier == 4) return vec2(0.22, 0.40);   // cold-blooded aquatic — the coldest thing alive in the scene
    if (tier == 5) return vec2(0.30, 0.60);   // cold-blooded
    if (tier == 6) return vec2(1.10, 3.00);   // burning — white-hot floor, no ceiling worth having

    return vec2(0.0, 3.00);
}

// Forces the classification byte to a value this shader actually defines, and rejects anything else.
//
// ⚠⚠ THE BANDS BELOW ARE WIDE, AND THAT IS WHAT TURNED A CORRUPT BYTE INTO A VISIBLE BUG. Only six values are ever
// written -- 1.0 entity, 0.875 held item, 0.5 terrain, 0.25 particle, 0.0625 celestial, 0.0 sky -- but the reader
// accepts whole RANGES, so a stray byte lands in whichever range it happens to fall in and is then trusted completely.
//
// ⭐⭐ MEASURED, NOT GUESSED: the horizon band reads 88/255 = 0.345, which sits inside the PARTICLE range
// (0.125..0.375). Particle heat is `0.80 * emission + blockHeat`, so those pixels were being handed BLOCK HEAT. Their
// depth is 1.0, so the world position feeding that lookup lands at the FAR PLANE along the view ray -- which sweeps
// through the world as the camera turns, dragging a glow across the sky near the horizon and letting it settle a
// moment later. One corrupt byte, three symptoms.
//
// ⭐ Unknown values resolve to SKY (0.0), the only category that carries no block heat, no emission and no material
// bonus -- so a byte we do not recognise can produce nothing worse than a cold pixel. The tolerance is generous: the
// mask is an R8 attachment written with exact constants, so a real category never drifts more than a byte or two, and
// the nearest pair of categories (0.0 and 0.0625) are 16 bytes apart.
//
// ⚠ This does NOT fix whatever writes the bad byte. It contains the damage. Chase the writer separately.
float sanitizeMask(float mask) {
    const float TOLERANCE = 0.02;   // ~5 bytes

    if (abs(mask - 1.0)    < TOLERANCE) return 1.0;      // entity
    if (abs(mask - 0.875)  < TOLERANCE) return 0.875;    // held item
    if (abs(mask - 0.5)    < TOLERANCE) return 0.5;      // terrain
    if (abs(mask - 0.25)   < TOLERANCE) return 0.25;     // particle
    if (abs(mask - 0.0625) < TOLERANCE) return 0.0625;   // celestial

    return 0.0;                                          // sky, and the safe answer for anything unrecognised
}

vec3 computeThermal(vec3 src, vec3 srcDim, float mask, vec4 drawData, vec4 specular, int materialId, float dimFactor, float backgroundFlag, float blockHeat) {
    mask = sanitizeMask(mask);

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

    // Core-to-extremity falloff. An outline pixel keeps 60% of the body's heat, an interior pixel all of it — enough
    // to walk a torso down through orange to green at the legs without losing the creature against the background.
    // Skipped entirely for background-reclassified entities, which have no business having a body shape here.
    // The core-to-extremity falloff. 0.30 at the outline against 1.0 in the body is a wide enough spread to walk a
    // torso from red down through orange and yellow to green at the hooves, which is what the reference footage shows.
    // Milder than this and the creature reads as one flat colour, which is the state this replaced.
    float coreDistance = length(reconstructWorldOffset(texCoord, texture(depthtex0, texCoord).r));
    float coreness = entityCoreness(texCoord, coreDistance);

    // The material byte carries TWO dials: heat tier in the low nibble, body size in the high nibble. See
    // PredatorHeatMaterials — the attachment is R8 and only six tier values were ever used, so the second dial rides
    // along for free with no new attachment, uniform or BLib change.
    int heatTier = materialId & 15;
    int sizeStep = (materialId >> 4) & 15;
    // ⚠ Step 0 means NOTHING WAS PUSHED — an unpatched draw, or a consumer that never set an ID. It must read as a
    // large body so those pixels keep exactly the behaviour they had before this dial existed.
    float sizeFactor = sizeStep == 0 ? 1.0 : float(sizeStep - 1) / 14.0;

    // 0.15 at the extremities against a slight boost at the core: in real thermography a hand or a foot fades into the
    // background entirely while the chest runs past red into white. Anything narrower than this reads as one colour.
    //
    // ⭐⭐ BUT THAT SPREAD IS ONLY RIGHT FOR A LARGE BODY. A chicken is extremity almost everywhere, so a 0.15 floor
    // collapsed it to a uniform blue-green whatever tier it was in. Small bodies get a higher floor — thermally
    // correct too, since a bird simply has no resolvable core-to-limb structure at that scale. 0.60 rather than
    // something flatter is deliberate: it keeps a chicken warm without letting it out-glow a player's torso.
    float extremityFloor = mix(0.60, 0.15, sizeFactor);
    entityHeat *= mix(extremityFloor, 1.12, coreness);

    // Terrain: block light drives heat across the full gradient range so torches/lava produce smooth radiance falloff.
    // For entity-mask pixels reclassified as terrain via backgroundFlag (catEntity zeroed out above), drawData.g
    // carries the per-bone-light-boosted block-light coord that the consumer mod pushed for visibility on the OTHER
    // side of a wipe — feeding that boost into terrainHeat would produce a spurious thermal-warm silhouette of the
    // entity on the side where it's supposed to read as world-cold (e.g. an EM-visible mob glowing as if it were
    // thermal-tagged on the thermal side of a T→EM transition). Override to 0 so those pixels render like real
    // terrain in shadow. Real terrain pixels (mask.r ≈ 0.5) have catEntityRaw = 0 and pass through unchanged.
    float isEntityReclassified = catEntityRaw * backgroundFlag;
    // Kept for the entity-reclassified case below and for the debug paths; no longer feeds terrain heat.
    float terrainBlockLight = mix(blockLight, 0.0, isEntityReclassified);

    // ⚠⚠ Emission has to be zeroed for reclassified entities for the same reason block light is, and forgetting it was
    // a real bug: a xenomorph is correctly pushed to background under thermal, but its specular still fed this term,
    // so the drone glowed green wherever its block light crossed the smoothstep threshold. The same build showed the
    // alien to one player and hid it from another purely because of how well lit it happened to be.
    float terrainEmission = mix(emission, 0.0, isEntityReclassified);
    // Block light is deliberately NOT a heat term for terrain. Light and heat are different things: a glowstone wall is
    // bright and cold, and a lightmap cannot say so. World heat comes from the authored block map instead, which also
    // makes Sodium and vanilla agree — terrainBlockLight is always zero under Sodium, so including it would have made
    // the same wall read differently depending on which renderer drew it.
    //
    // Emission survives: that is a genuinely emissive TEXTURE rather than ambient light falling on the surface.
    float terrainHeat = 1.50 * terrainEmission + blockHeat;

    // Same rule for particles, and it is the visible one: block dust inherited its heat from how brightly lit the block
    // was, so breaking cold stone under a torch produced warm-reading debris. Particles now take their heat from WHERE
    // THEY ARE — flame particles sit on a torch, lava pops sit in lava, smoke sits in a campfire's core radius, and
    // dust from a stone wall reads cold unless something hot is actually near it.
    float particleHeat = 0.80 * emission + blockHeat;

    // Procedural sky+celestial heat. Cheap to compute even on non-sky pixels; gated by catSky below.
    // drawDetail (= drawData.r) is the celestial-body texture luminance captured by the patcher's CELESTIAL
    // path; 0 at non-celestial sky pixels because the cleared auxiliary buffer is 0 and the passthrough shaders
    // explicitly write 0 there.
    float skyHeat = computeSkyHeat(texCoord, drawDetail);

    // Per-material heat additions. Tiers 1-5 span ordinary biology, from large-mammal warmth down to a fish.
    //
    // Tier 6 is not biology at all: blazes, magma cubes and ghasts are made of fire, and a hunter should read them the
    // way it reads lava rather than the way it reads a cow. 1.10 puts them past the top of the gradient, so they come
    // out white-hot against a world where a player is merely red.
    float materialBoost =
        (heatTier == 1 ? 0.45 : 0.0)
        + (heatTier == 2 ? 0.30 : 0.0)
        + (heatTier == 3 ? 0.55 : 0.0)
        + (heatTier == 4 ? 0.20 : 0.0)
        + (heatTier == 5 ? 0.25 : 0.0)
        + (heatTier == 6 ? 1.10 : 0.0);

    // The creature's own reading: its body heat plus its tier offset, held inside the band its tier allows.
    //
    // ⚠⚠ THE BONUS IS NOW SCOPED TO THE ENTITY BRANCH, AND IT WAS NOT BEFORE. It used to be added to the total for
    // every category, so an entity reclassified to background under this mode still received its tier bonus through
    // the terrain formula — the same oversight that had already been fixed for block light and emission a few lines
    // up, one term short. Terrain and particles never carry a material ID of their own, so nothing else changes.
    vec2 band = tierBand(heatTier);
    float entityTotal = clamp(entityHeat + materialBoost, band.x, band.y);

    float heat =
        catEntity   * entityTotal
        + catTerrain  * terrainHeat
        + catParticle * particleHeat
        + catSky      * skyHeat;

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
    mask = sanitizeMask(mask);

    // Held items used to short-circuit to srcDim (passthrough). Now main() merges held-item state into
    // backgroundFlag so they fall through to the world coloring branch below — held items blend with the world
    // instead of giving away their wielder's position.
    float catEntity = step(0.75, mask) * (1.0 - backgroundFlag);
    float srcLuma = dot(src, vec3(0.299, 0.587, 0.114));
    float liftedLuma = pow(clamp(srcLuma, 0.0, 1.0), 0.5);

    // ENTITY: green-tinted, texture-preserving, lighting-modulated.
    float blockLight = max(drawData.g, ultrawarmAmbient);
    float faceLight = drawData.a;
    // ⚠⚠ THE FLOORS ARE LOAD-BEARING, NOT COSMETIC. An EM-visible entity must out-read the world it stands against
    // no matter how dark its own texture is — and xenomorphs are near-black, so liftedLuma is ~0 for most of their
    // body. With the old floors (0.2 luma, 0.45 lighting) a drone rendered at ~0.09 green while a lit wall reaches
    // 0.18: the alien was literally darker than its background, which read as "EM does not show aliens" even though
    // the tag, the classification and the branch were all correct.
    //
    // These floors put an unlit black entity at ~0.36 against a world maximum of 0.18 — a 2x contrast in the worst
    // case, with brighter textures and better lighting rising from there.
    float entityLighting = 0.55 + 0.30 * blockLight + 0.15 * faceLight;
    float entityLuma = mix(0.65, 1.0, liftedLuma);
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

    // Translucent terrain — water, ice, glass and LAVA — is drawn after the terrain-mask fixup runs, so under Sodium
    // nothing ever writes a category for it and it arrives here reading as sky. The sky branch then computes ambient
    // sky heat and never looks at blockHeat, which is why a lava pool rendered exactly like water while the heat value
    // for those pixels was being computed correctly and discarded.
    //
    // The depth buffer knows better than the mask does: anything nearer than the far plane is geometry, whatever the
    // mask says. Reclassify those pixels as terrain. Celestial bodies sit at the far plane so they are untouched, and
    // entities, particles and held items are all above this threshold and keep their own categories.
    float geometryDepth = texture(depthtex0, texCoord).r;

    if (geometryDepth < 1.0 && mask < 0.125) {
        mask = 0.5;
    }
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

    // Sky pixels sit at the far plane and have no surface to warm, so the source loop is skipped there entirely.
    float pixelDepth = geometryDepth;
    float blockHeat = pixelDepth >= 1.0 ? 0.0 : blockHeatAt(reconstructWorldOffset(texCoord, pixelDepth));

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
    vec3 thermalRgbOld = computeThermal(src, srcDim, mask, drawData, specular, materialId, dimFactor, effectiveBgOld, blockHeat);
    vec3 emRgbOld = computeEm(src, srcDim, mask, drawData, dimFactor, effectiveBgOld);
    vec3 oldRgb = selectMode(oldMode, srcDim, thermalRgbOld, emRgbOld);

    vec3 thermalRgbNew = computeThermal(src, srcDim, mask, drawData, specular, materialId, dimFactor, effectiveBgNew, blockHeat);
    vec3 emRgbNew = computeEm(src, srcDim, mask, drawData, dimFactor, effectiveBgNew);
    vec3 newRgb = selectMode(newMode, srcDim, thermalRgbNew, emRgbNew);

    // Debug view (-Davp_predator.heatDebug=true). Three independent signals in one image, so a single screenshot says
    // which link of the chain is broken instead of another round of guessing:
    //   RED   = blockHeat at this pixel. Any red at all means sources are reaching the shader AND the falloff resolves.
    //   GREEN = reconstructed distance from the camera / 32. Should rise smoothly with depth. Flat or black means the
    //           world-position reconstruction is wrong, which would make every falloff evaluate to zero.
    //   BLUE  = 1 when heatSourceCount > 0. Black screen means the count uniform never arrived.
    if (heatDebugView != 0) {
        vec3 dbgOffset = reconstructWorldOffset(texCoord, pixelDepth);
        float dbgDistance = pixelDepth >= 1.0 ? 0.0 : clamp(length(dbgOffset) / 16.0, 0.0, 1.0);

        fragColor = vec4(clamp(blockHeat, 0.0, 1.0), dbgDistance, heatSourceCount > 0 ? 1.0 : 0.0, 1.0);

        return;
    }

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

