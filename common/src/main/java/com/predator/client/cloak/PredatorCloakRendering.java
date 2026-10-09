package com.predator.client.cloak;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.predator.PredatorResources;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector4f;

/**
 * The single seam every cloaked draw passes through.
 * <p>
 * Rather than hunting down the body render, the armour layer, the item layer and every other layer individually, the
 * render mixin swaps the {@link MultiBufferSource} argument itself. Everything downstream — body, armour, held items,
 * eyes, capes, whatever a sibling mod adds — then routes through here automatically. One injection point covers the
 * lot, which is why the armour cannot be left visibly floating the way vanilla invisibility leaves it.
 * <h2>Two modes</h2>
 * <ul>
 * <li><b>Concealed</b> — every requested render type is replaced with a translucent pass over the wearer's own texture,
 * and the vertex colours are driven down to roughly a tenth alpha. That is the faint silhouette: enough to make out an
 * outline if you are looking for it, not enough to read as a body.</li>
 * <li><b>Shorting out</b> (in water or rain) — the original render type is kept, so the wearer draws at full
 * visibility, and a second consumer is chained on with vanilla's energy-swirl pass. The geometry gets written twice
 * from one call, which is exactly how a charged creeper gets its arcing shell.</li>
 * </ul>
 * <h2>Known compromise</h2> A {@link RenderType} does not expose the texture it was built from, so the concealed pass
 * uses the wearer's body texture for every layer including armour. At a tenth alpha the difference is not perceptible —
 * but it is a real simplification, not an oversight, and it goes away when the refraction shader lands and stops
 * needing the texture for anything but the edge tint.
 */
public final class PredatorCloakRendering {

    /**
     * Vanilla's shadow pass, resolved from the same texture {@code EntityRenderDispatcher.SHADOW_RENDER_TYPE} uses.
     * {@code RenderType.entityShadow} is memoized, so this is the identical instance and can be compared by identity.
     * <p>
     * ⚠ It has to be matched explicitly. The shadow uses {@code DefaultVertexFormat.NEW_ENTITY} — the SAME format as
     * model geometry — so a format check does not separate it. An earlier note in this file claimed it did; that was
     * wrong, and the shadow was still being rerouted through the entity texture the whole time. It merely stopped
     * looking obviously broken once the silhouette dropped to a tenth alpha, which made a mis-textured shadow read as
     * an ordinary soft one.
     */
    private static final RenderType SHADOW_RENDER_TYPE =
        RenderType.entityShadow(ResourceLocation.withDefaultNamespace("textures/misc/shadow.png"));

    /** Vanilla's charged-creeper shell. Reused deliberately: it is the exact look asked for and costs no new asset. */
    private static final ResourceLocation ENERGY_SWIRL_TEXTURE =
        ResourceLocation.withDefaultNamespace("textures/entity/creeper/creeper_armor.png");

    /**
     * Alpha the silhouette is driven down to. 13/255 is about 5%.
     * <p>
     * This stacks with the refraction pass, which draws at the same level, so a concealed wearer totals roughly 10%
     * visible — the top of the intended 5-10% band. Raise this one if the OUTLINE is too hard to pick out; raise
     * RIPPLE_AMPLITUDE instead if the shape is legible but the motion is not catching the eye.
     */
    // ⚠ Aug 27 calibration, his ruling: "the pulse was a bit weak and the refraction not as noticeable as i
    // liked" — measured against his reference screenshot. First-pass bumps of roughly a third; recalibrate from a
    // screenshot, not from taste, if a second round is needed.
    // Round 2: "invisible no outline" — 8 is the rev-31 value he approved as the fallback look.
    private static final int SILHOUETTE_ALPHA = 8;

    /**
     * ⚠ Aug 28 — the concealed body's opacity UNDER AN ACTIVE SHADER PACK (0-255). Packs run our custom shimmer types
     * through their solid entity program — blend ignored, black statue — so under a pack the body draws once through
     * vanilla's entityTranslucent, which every pack maps to its translucent program with blending on. Pack authors vary
     * in how faithfully vertex alpha survives, so this is deliberately a mid value: visible ghost on faithful packs, at
     * worst more visible on careless ones — never black, never invisible.
     */
    // ⚠ His tune, Aug 28: "whispy thin" — 5%% visible. 13/255 ≈ 5%%; the mob dial scales further down from here.
    private static final int PACK_GHOST_ALPHA = 13;

    /**
     * Scrolling pattern used for the ripple shimmer.
     * <p>
     * ⚠ PURE WHITE with varying alpha — no colour in it at all. This pass blends translucently, so whatever colour the
     * sheet carries is multiplied straight onto the wearer. Vanilla's underwater.png was used here first and it is
     * BLUE, which is why the cloak rendered as a transparent blue figure everywhere, on land as much as near water. A
     * white sheet carries the same banding with nothing to tint the body.
     */
    private static final ResourceLocation RIPPLE_TEXTURE =
        PredatorResources.location("textures/effect/cloak_ripple.png");

    /**
     * Alpha for the ripple pass. Deliberately NOT clamped to {@link #SILHOUETTE_ALPHA} — that was half of why nothing
     * showed at first. The overlay is additive, so this controls how much light it ADDS, not how solid it looks.
     * <p>
     * ⚠ The usable range is narrow and low. At 90 the underwater sheet added so much white that the wearer read as a
     * solid glowing figure — brighter than being uncloaked, and the loudest possible tell. Additive passes saturate
     * fast: past roughly 40 the body starts washing to white regardless of what is behind it.
     */
    private static final int RIPPLE_ALPHA = 8;

    /**
     * Brightness of the shimmer, 0-255, applied as the vertex COLOUR rather than the alpha.
     * <p>
     * ⚠ Faintness is controlled here, NOT via {@link #RIPPLE_ALPHA}. Alpha turned out to be a near-binary switch on
     * this pass — 46 rendered clearly, 32 and 20 vanished outright — because it gates the additive contribution against
     * a texture that is already dark in places, so small reductions drop whole regions below anything visible. Colour
     * scales the light added smoothly and evenly instead, so it dims rather than disappears.
     */
    private static final int RIPPLE_TINT = 255;

    private PredatorCloakRendering() {
        throw new UnsupportedOperationException();
    }

    /**
     * @return the buffer source the entity should actually be drawn through, or {@code original} when the cloak is not
     *         a factor for this entity or this viewer.
     */
    /**
     * ⚠⚠ Aug 27 — THE REPLAY LANES APPLY TO PLAYERS ONLY; AZ-RENDERED MOBS KEEP THE ORIGINAL DIRECT WIRING. The
     * evidence sorts cleanly by renderer family and this routing follows it exactly. The "Not building!" crash occurred
     * ONLY through the vanilla player renderer (both live reports), so players draw through the recorded replay, which
     * provably cannot collide. The yautja draws through the AzureLib renderer, which ran the direct multi-consumer
     * wiring for days without one crash and produced the reference cloak look — and under the replay it recorded and
     * replayed 11,400 vertices per frame with zero GL errors while showing NOTHING on screen, a divergence the
     * diagnostics could not explain. Rather than theorise a fifth time, each family runs the path it is PROVEN on. If a
     * vanilla-model mob ever gains the cloak, it takes the player route automatically only if someone widens this check
     * — widen it deliberately, with a test.
     */
    private static boolean replayLanesForCurrentEntity;

    /** 1.0 for players; the mob dial — see the note in {@link #wrap}. */
    private static float effectIntensityForCurrentEntity = 1.0F;

    private static long lastWrapDiagMillis;

    private static long lastPlayerWrapDiagMillis;

    private static void wrapDiag(Entity entity, String decision) {
        if (!CLOAK_DIAG) {
            return;
        }
        // ⚠ Players get their OWN throttle. The old version skipped players entirely — which blinded the
        // instrument to the exact entity the armour-corruption bug lives on. Separate clocks so neither
        // starves the other.
        if (entity instanceof net.minecraft.world.entity.player.Player) {
            if (System.currentTimeMillis() - lastPlayerWrapDiagMillis < 1000L) {
                return;
            }
            lastPlayerWrapDiagMillis = System.currentTimeMillis();
        } else {
            if (System.currentTimeMillis() - lastWrapDiagMillis < 1000L) {
                return;
            }
            lastWrapDiagMillis = System.currentTimeMillis();
        }
        com.predator.Predator.LOGGER.info(
            "[CloakDiag] wrap entity={} id={} clientCloaked={} decision={}",
            entity.getType().toString(),
            entity.getId(),
            entity instanceof LivingEntity l && PredatorCloakClientState.isCloaked(l),
            decision
        );
    }

    public static MultiBufferSource wrap(MultiBufferSource original, Entity entity) {
        // ⚠⚠ Aug 27 final routing: EVERYONE replays. The player-only split assumed the az direct path was the
        // proven-visuals one; the wrap diagnostics falsified that — a cloaked yautja logged decision=REFRACTING on
        // the direct path and rendered NOTHING, while every visible cloak tonight (player on both loaders, the
        // pulsing Fabric preds) ran through the replay lanes. Replay-for-all makes a mob's cloak the player's exact
        // pipeline — same consumers, same constants, same draws — so "as visible as the player" holds by
        // construction, and the crash-proof path is the only path. Why the direct draws vanish on NeoForge remains
        // unexplained; with no code running it, it no longer needs explaining.
        // ⚠⚠ A HELD ITEM IS NOT PART OF THE BODY. This wrap covers the WHOLE entity render, so a sword or axe in
        // the yautja's hand inherited the shorting-out crackle along with the body — his bug report. Handing back
        // the untouched source while an item is drawing is the same exclusion armour already gets, for the same
        // reason: an effect authored for a body should not be inherited by what the body is carrying.
        if (renderingHeldItem) {
            return original;
        }

        replayLanesForCurrentEntity = true;

        // ⚠ Aug 28, his ruling: MOBS should be markedly harder to spot than the player's own F5 self-check —
        // "the predators arent nearly invisible enough... the player is fine its just the mobs." One knob, applied
        // to the silhouette and pulse alphas only; the refraction is what makes them invisible and stays at full.
        effectIntensityForCurrentEntity = entity instanceof net.minecraft.world.entity.player.Player ? 1.0F : 0.45F;
        // ⚠⚠ Aug 28 — SENSORS BYPASS THE OPTICAL CLOAK. While the local viewer is in ANY vision mode (or
        // transitioning between modes), the wrap stands aside and the body renders normally — because the
        // shimmer-replaced body carries no entity classification (the silhouette writes neither depth nor
        // category), so under a vision mode it read as warped background: transparent on EM where the spec says
        // SOLID, and hidden behind whatever heat the terrain reconstruction produced on thermal. With the wrap
        // out of the way, PredatorVisionClassification's already-written cloak trade owns the outcome exactly as
        // specified: thermal reads a concealed wearer as BACKGROUND (plus a zero heat tier in
        // PredatorHeatMaterials, the mud precedent), and EM reads them VISIBLE — the field's own radiation.
        // REGULAR vision never enters this branch, so the shimmer is untouched for ordinary sight.
        // (Transition deliberately NOT included: gating on it kept the plain body on screen during the fade OUT
        // of a vision mode — "visible for a moment" — when the shimmer should re-engage the instant the mode ends.)
        // ⚠⚠ Aug 28 fix: the mode alone is NOT "the visor is on". The mask STORES its mode in a component — it
        // remembers thermal/EM across wear — and the overlay itself only renders in FIRST PERSON (the post-effects
        // camera check). Gating on the stored mode alone meant that merely WEARING a mask that remembered a mode,
        // in F5, opened this gate with no overlay on screen: every cloaked pred rendered plain — his enchanted-helm
        // on/off toggle. Sensors bypass the optical cloak only while the wearer is actually LOOKING THROUGH them:
        // stored mode active AND first-person, the same pair the overlay uses to decide to draw.
        var cloakGateCamera = Minecraft.getInstance().options.getCameraType();
        if (
            com.predator.client.vision.PredatorVisionAccessor
                .currentVisionMode() != com.predator.common.gameplay.component.PredatorVisionMode.REGULAR
                && cloakGateCamera != null && cloakGateCamera.isFirstPerson()
        ) {
            return original;
        }

        if (!(entity instanceof LivingEntity living) || !PredatorCloakClientState.isCloaked(living)) {
            wrapDiag(entity, "NOT_CLOAKED_ON_CLIENT");
            return original;
        }

        if (PredatorCloakClientState.seesThrough(living)) {
            wrapDiag(entity, "OBSERVER_SEES_THROUGH");
            return original;
        }

        // ⚠ Aug 28 — SHADER PACKS GET TRUE INVISIBILITY. With an Iris pack active, the pack's entity programs
        // run our custom silhouette/ripple types and ignore their blend state — the near-transparent shimmer
        // rendered as a SOLID BLACK statue (his screenshot, Aug 28). The refraction already stands down under
        // packs for the same reason; the shimmer now joins it. The cloak's JOB is concealment — under a pack a
        // concealed wearer simply draws nothing at all, which is film-correct anyway. Sensors above are
        // unaffected; uncloaked rendering is untouched.
        if (com.blib.internal.client.posteffect.BLibIrisCompat.isShaderPackActive()) {
            // His ruling, Aug 28: not pure invisibility — "can we at least have the models be partially
            // transparent?" The body ghosts through the pack's OWN translucent program; armour has no safe
            // texture handle for the re-type (atlas UVs — the magenta lesson) and stays hidden, and the body
            // mesh is complete underneath it, so the result reads as a faint full-figure ghost.
            wrapDiag(entity, "PACK_GHOST");

            // ⚠ His ruling, Aug 28 — the SHADER TELL: packs that squash low alpha (Bliss) leave the ghost
            // invisible, so cloaked yautja carry faint white sparks under an active pack. Particles render through
            // every pack's own particle pipeline, so this tell cannot be crushed the way translucency can. Mobs
            // only — players reveal through the melee window and their own HUD. Runs per render frame; the low
            // probability keeps it a whisper of sparks, not a beacon.
            if (entity instanceof com.predator.common.gameplay.entity.living.yautja.Yautja sparkling && sparkling.level() != null) {
                var sparkRandom = sparkling.level().random;

                if (sparkRandom.nextFloat() < 0.08F) {
                    sparkling.level()
                        .addParticle(
                            net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                            sparkling.getRandomX(0.6),
                            sparkling.getY() + sparkRandom.nextDouble() * sparkling.getBbHeight(),
                            sparkling.getRandomZ(0.6),
                            0.0,
                            0.0,
                            0.0
                        );
                }
            }
            var packTexture = textureFor(entity);

            if (packTexture == null) {
                return renderType -> NoOpVertexConsumer.INSTANCE;
            }

            return new PackGhostBufferSource(original, packTexture);
        }

        if (living.isInWaterOrRain()) {
            wrapDiag(entity, "SHORTING_OUT");
            return new ShortingOutBufferSource(original);
        }

        // Refraction when a usable scene copy exists; plain silhouette otherwise. The fallback is not a failure
        // path — it is the shader-pack path, and it is why none of this needs a custom core shader.
        if (PredatorSceneColor.isAvailable() && PredatorSceneColor.isRenderingLevel()) {
            var bodyTexture = textureFor(entity);
            var camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();

            if (bodyTexture != null) {
                wrapDiag(entity, "REFRACTING");
                return new RefractingBufferSource(
                    original,
                    RenderType.entityNoOutline(PredatorSceneColor.LOCATION),
                    RenderType.entityNoOutline(bodyTexture),
                    bodyTexture,
                    (float) (entity.getX() - camera.x),
                    (float) (entity.getY() + entity.getBbHeight() * 0.5 - camera.y),
                    (float) (entity.getZ() - camera.z)
                );
            }
        }

        var texture = textureFor(entity);
        // ⚠ entityNoOutline, NOT entityTranslucent. Same NEW_ENTITY format, same lighting, same translucency — but
        // its write mask is COLOR_WRITE rather than COLOR_DEPTH_WRITE. entityTranslucent writes depth, and because
        // vanilla draws entities BEFORE translucent terrain, a near-transparent body was still filling the depth
        // buffer and rejecting the water drawn behind it. The cloaked wearer became a window: you saw the lake bed
        // through them with the water surface missing, which is worse than not being cloaked at all.
        //
        // Losing the glow-outline pass is a non-issue here; a cloaked entity should not be drawing an outline.
        wrapDiag(entity, texture == null ? "NO_BODY_TEXTURE" : "CONCEALED");
        return texture == null ? original : new ConcealedBufferSource(original, RenderType.entityNoOutline(texture), texture);
    }

    /**
     * The entity's own texture, taken from whichever renderer the dispatcher would use. Raw types are unavoidable here:
     * {@code getRenderer} is declared as {@code EntityRenderer<? super E>} and there is no way to name that bound from
     * outside the call site.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static ResourceLocation textureFor(Entity entity) {
        // ⚠ Aug 28 — variants first. The generic path below asks the renderer CONFIG, which answers with the BASE
        // texture — correct for every single-texture entity and wrong for two of three yautja variants, whose body
        // draws with a suffixed texture. The mismatch made isOwnBodyPass reject every pass and a cloaked TIGER or
        // BRUSH vanished outright (the 1-in-3 spawn-egg observation). The renderer now owns the derivation; ask it.
        if (entity instanceof com.predator.common.gameplay.entity.living.yautja.Yautja yautja) {
            return com.predator.client.render.entity.YautjaRenderer.variantTexture(yautja.getVariant());
        }

        try {
            EntityRenderer renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
            return renderer == null ? null : renderer.getTextureLocation(entity);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /**
     * {@return {@code true} when this pass is actual model geometry rather than something else the dispatcher draws}
     * <p>
     * ⚠ The buffer-source swap is indiscriminate — it sees every pass an entity render asks for, and that includes
     * {@code EntityRenderer#renderShadow}, name plates, leashes and hitbox lines. Rerouting the shadow through the
     * entity texture is what produced the flat translucent patch on the ground under a cloaked player: shadow quads
     * drawn with entity UVs.
     * <p>
     * Format is the honest discriminator. Model layers use {@code NEW_ENTITY}; the shadow uses
     * {@code POSITION_COLOR_TEX_LIGHTMAP} and the rest use their own. This also protects the water/rain path, since
     * {@code VertexMultiConsumer} requires both consumers to share a format and would otherwise be fed mismatched ones.
     */
    /**
     * Armour variant of {@link #wrap}. Keeps whatever render type the armour asked for and only drives its alpha down.
     * <p>
     * ⚠ It must NOT substitute the render type the way the body path does. The body path swaps in
     * {@code entityNoOutline(entityTexture)}, which is fine for the body because that IS the body's texture — but
     * armour geometry carries its own UVs mapped to its own texture. Feeding it the wearer's skin sampled those UVs far
     * outside anything meaningful and produced a flat magenta block where the predator mask should be.
     * <p>
     * The trade: armour keeps its own render type, so if that type writes depth it can still punch a small hole in
     * water the way the body used to. Confined to the mask it is barely noticeable, and it goes away properly when the
     * refraction shader lands and the texture is resolved from the armour renderer instead of the entity.
     */
    private static long armourDiagWindowStart;

    private static int armourDiagCalls;

    public static MultiBufferSource wrapArmour(MultiBufferSource original, Entity entity) {
        // ⚠ Aug 28 diag — counts az armour-model invocations per second. Four worn pieces should call here four
        // times per frame; if removing the HELMET does not drop the rate by about a quarter, the HEAD slot never
        // reached the az pipeline at all — which would mean the mask has NEVER drawn and was only ever visible as
        // the old vanilla magenta before the transparent veritanium layers hid the fallback too.
        if (CLOAK_DIAG) {
            armourDiagCalls++;
            var now = System.currentTimeMillis();
            if (now - armourDiagWindowStart >= 1000L) {
                var visionGateOpen = false;
                var gateCam = Minecraft.getInstance().options.getCameraType();
                if (
                    com.predator.client.vision.PredatorVisionAccessor
                        .currentVisionMode() != com.predator.common.gameplay.component.PredatorVisionMode.REGULAR
                        && gateCam != null && gateCam.isFirstPerson()
                ) {
                    visionGateOpen = true;
                }
                var branch = "ORIGINAL";
                if (visionGateOpen) {
                    branch = "VISION_GATE";
                } else if (entity instanceof LivingEntity armourWearer && PredatorCloakClientState.isCloaked(armourWearer)) {
                    branch = PredatorCloakClientState.seesThrough(armourWearer)
                        ? "SEES_THROUGH"
                        : armourWearer.isInWaterOrRain() ? "SHORTING(original)" : "CONCEALED(hidden)";
                }
                com.predator.Predator.LOGGER.info(
                    "[CloakDiag] armour-model calls in last second: {} (wearer={} branch={})",
                    armourDiagCalls,
                    entity.getType().toString(),
                    branch
                );
                armourDiagWindowStart = now;
                armourDiagCalls = 0;
            }
        }

        // ⚠⚠ Aug 28 — JOIN GRACE, the armour-corruption fix. Measured: with stored cloak state restoring at
        // login, the session's FIRST armour renders ran through this wrap's concealed swap, and afterwards az
        // dispatch was dead for most pieces ALL SESSION — 60 calls/sec (one piece) against a healthy 240, state
        // and branch logs correct throughout, F3+T useless, only a restart healing it. Something in the armour
        // pipeline's one-time init must see the REAL buffer source. So: this wrap is inert for each wearer's
        // first five seconds in a world. A cloaked-at-login wearer shows armour for that blink, then conceals.
        // If corruption EVER reproduces with this in place, the swap is exonerated and the bug is BLib-internal.
        if (entity.tickCount < 100) {
            return original;
        }

        // ⚠⚠ Aug 28, THE JOIN-WINDOW SHIELD — armour is never concealment-wrapped during the first five seconds
        // of a session. Measured background: after a cloaked-restore login, decloaking left ALL armour invisible
        // with the diag showing branch=ORIGINAL, clientCloaked=false and sixty az calls a second — the az pipeline
        // drawing through untouched consumers and producing nothing, healed only by a full restart, untouched by
        // F3+T. The cloak STATE is exonerated by that log; the surviving suspect is az's lazy first-render
        // initialization happening underneath the concealed wrapper's vertex-swallowing consumers on exactly those
        // logins. This shield guarantees the first armour renders of every session run with REAL consumers.
        // Cosmetic cost: for ~5s after joining, a cloaked wearer's armour renders plainly. If the corruption
        // still occurs WITH this shield, the theory is dead and the bug is BLib-internal — remove the shield then,
        // it will not be the cause.
        var localPlayer = Minecraft.getInstance().player;

        if (localPlayer != null && localPlayer.tickCount < 100) {
            return original;
        }

        // ⚠ Aug 28 — the SAME sensor bypass as wrap(), and the asymmetry of not having it here was a live bug:
        // in a vision mode the body rendered plain (gated) while the armour still consulted the cloak state and
        // silhouetted itself into near-nothing — a visible player wearing invisible armour. Sensors bypass the
        // whole optical system, armour included.
        // ⚠⚠ Aug 28 fix: the mode alone is NOT "the visor is on". The mask STORES its mode in a component — it
        // remembers thermal/EM across wear — and the overlay itself only renders in FIRST PERSON (the post-effects
        // camera check). Gating on the stored mode alone meant that merely WEARING a mask that remembered a mode,
        // in F5, opened this gate with no overlay on screen: every cloaked pred rendered plain — his enchanted-helm
        // on/off toggle. Sensors bypass the optical cloak only while the wearer is actually LOOKING THROUGH them:
        // stored mode active AND first-person, the same pair the overlay uses to decide to draw.
        var cloakGateCamera = Minecraft.getInstance().options.getCameraType();
        if (
            com.predator.client.vision.PredatorVisionAccessor
                .currentVisionMode() != com.predator.common.gameplay.component.PredatorVisionMode.REGULAR
                && cloakGateCamera != null && cloakGateCamera.isFirstPerson()
        ) {
            return original;
        }

        if (!(entity instanceof LivingEntity living) || !PredatorCloakClientState.isCloaked(living)) {
            return original;
        }

        // Same shader-pack rule as the body: concealed armour draws nothing rather than a black shell.
        if (com.blib.internal.client.posteffect.BLibIrisCompat.isShaderPackActive()) {
            return renderType -> NoOpVertexConsumer.INSTANCE;
        }

        if (PredatorCloakClientState.seesThrough(living)) {
            return original;
        }

        // ⚠ Armour gets NO arcing while shorting out, and this is deliberate rather than an omission.
        // Chaining the energy swirl onto armour draws the SAME geometry a second time against creeper_armor.png —
        // a 64x32 sheet — using UVs authored for the armour's own texture, which on the predator set is 256x256. The
        // UVs land far outside anything sampleable and come out as flat magenta blocks on the helmet and leggings.
        // Exactly the trap that produced the original magenta mask, and it cannot be fixed by choosing a different
        // swirl texture: the mismatch is between the model's UVs and any substitute sheet.
        // The body still arcs, which is what sells the effect; the armour simply renders normally.
        if (living.isInWaterOrRain()) {
            return original;
        }

        // Match whatever the body is doing, so the mask never reads as a solid head over a refracting torso.
        // Safe despite the texture-dimension trap that caused the magenta mask: this intercepts at the vertex
        // consumer, downstream of any UV scaling BLib applies, and overwrites the UVs outright.
        // ⚠ Armour stays on the silhouette-only path. Routing it through refraction put a magenta mask back on
        // screen, and this path is the one configuration confirmed working in-game. Reinstating refraction for armour
        // is worth doing, but only once the body path is verified stable — not blind, and not both at once.
        // ⚠ armorEntityGlint, NOT energySwirl. The glint's texturing shard SCALES AND REPEATS its UVs, so it tolerates
        // being laid over a model whose UVs were authored for a different sheet — which is precisely why vanilla can
        // put it on any armour. energySwirl has no such wrapping, so the same UVs clamp and sample garbage; that is
        // what turned the helmet and leggings magenta, not the texture size as I first claimed.
        //
        // The glint also uses EQUAL_DEPTH_TEST, which is fine here: armour draws through its own cutout render type
        // and writes depth, so there is something for the glint to test against. That is exactly why this route works
        // on armour but NOT on the cloaked body, which draws with entityNoOutline and writes no depth at all.
        return renderType -> {
            // Same immediate-source hazard as the body path — see ConcealedBufferSource#getBuffer.
            if (!PredatorSceneColor.isRenderingLevel()) {
                return new SilhouetteVertexConsumer(original.getBuffer(renderType));
            }

            return com.mojang.blaze3d.vertex.VertexMultiConsumer.create(
                new SilhouetteVertexConsumer(original.getBuffer(renderType)),
                new RippleVertexConsumer(original.getBuffer(RenderType.armorEntityGlint()))
            );
        };
    }

    /**
     * {@return {@code true} when this pass draws the entity's OWN body texture, and may therefore have its texture
     * substituted}
     * <p>
     * ⚠ The {@code NEW_ENTITY} format check is NOT sufficient on its own. Item and block models worn on an entity —
     * anything in the head slot, elytra, capes — also draw through {@code NEW_ENTITY} render types, but their UVs map
     * into the BLOCK or ITEM atlas, not the entity's skin. Substituting the body texture under those UVs samples far
     * outside anything meaningful and comes out as a flat magenta block, which is exactly what a head-slot item was
     * doing.
     * <p>
     * Render types are memoized by vanilla, so building each variant from the entity's own texture and comparing by
     * identity reliably answers "is this pass drawing that texture?" without needing to read the texture back out of a
     * {@link RenderType}, which the class does not expose.
     */
    private static boolean isOwnBodyPass(RenderType renderType, ResourceLocation texture) {
        return renderType == RenderType.entityCutoutNoCull(texture)
            || renderType == RenderType.entityCutout(texture)
            || renderType == RenderType.entityTranslucent(texture)
            || renderType == RenderType.entityTranslucentCull(texture)
            || renderType == RenderType.entitySolid(texture)
            || renderType == RenderType.entityNoOutline(texture)
            || renderType == RenderType.entitySmoothCutout(texture)
            || renderType == RenderType.entityCutoutNoCullZOffset(texture);
    }

    /**
     * The entity currently being drawn by the dispatcher, for code that sits deeper in the render and is not handed
     * one.
     * <p>
     * ⚠ Exists so the armour hook does not have to reach into BLib. The previous version @Shadow-ed a private field on
     * {@code AzArmorModel} to reach the pipeline context — and a shadowed field binds against the class as loaded at
     * startup, so hot-swapping a jar underneath a running client left that binding stale and the predator armour
     * rendered unresolved. Six other armour sets on the same BLib path were unaffected precisely because nothing mixed
     * into them. Render is single-threaded, so a plain static is safe and carries no binding to go stale.
     */
    private static Entity currentEntity;

    public static void beginEntity(Entity entity) {
        PENDING_REPLAYS.clear();
        currentEntity = entity;
    }

    /**
     * ⚠⚠⚠ Aug 27, second attempt — RECORD AND REPLAY, ONE RENDER TYPE AT A TIME.
     * <p>
     * The crash this replaces the fix for: a {@code VertexMultiConsumer} asked the level pass's buffer source for two
     * or three of the cloak's render types AT ONCE. Only vanilla's fixed types keep private builders there; the cloak's
     * types share the single fallback builder, so the second {@code getBuffer} closed the first type's batch and the
     * next vertex threw {@code IllegalStateException: Not building!} — two live reports, always a player viewing a
     * cloaked player.
     * <p>
     * ⚠ The FIRST fix (private lane {@code BufferSource}s flushed here) removed the crash and ALSO removed every cloak
     * visual on BOTH loaders — verified by a NeoForge launch where the previously-working silhouette was gone. The
     * lanes drew through their own flush point instead of the delegate, and whatever that path lacked, the result was
     * invisible geometry. The mechanism was never identified, and this design makes identifying it unnecessary: there
     * is no separate draw path anymore.
     * <p>
     * NOW: each cloak consumer writes its finished vertices into a plain in-memory {@link RecordingVertexConsumer}.
     * When the dispatcher finishes the entity (the RETURN hook that already calls {@link #endEntity()}), the recordings
     * replay through the ORIGINAL buffer source — the path the cloak has always rendered through — strictly ONE render
     * type at a time, each fully written before the next {@code getBuffer}. Sequential single-type acquisition is
     * exactly how vanilla itself uses the source, so the collision cannot occur, and the drawing is byte-for-byte the
     * delegate path that demonstrably worked.
     * <p>
     * Replay order is registration order, which the wrap sites arrange back-to-front: refraction scene, then
     * silhouette, then ripple.
     */
    private static final java.util.List<PendingReplay> PENDING_REPLAYS = new java.util.ArrayList<>();

    /**
     * ⚠ Aug 27 diagnostic — {@code -Dpredator.cloak.diag=true} logs, once per second, what the replay actually did for
     * one cloaked entity: which lanes registered, how many vertices each recorded, the GL error state after each replay
     * draw, and whether the scene-copy texture resolves. Exists because the ripple survives the replay and the
     * refraction does not, and the split between "never recorded" and "drawn invisibly" cannot be read from source —
     * two source-derived theories on this subsystem were wrong in one evening. Costs nothing when the flag is absent.
     */
    private static final boolean CLOAK_DIAG = Boolean.getBoolean("predator.cloak.diag");

    private static long lastDiagMillis;

    private record PendingReplay(
        RecordingVertexConsumer recorder,
        MultiBufferSource delegate,
        RenderType renderType
    ) {}

    /**
     * For a PLAYER, registers a recorder whose contents replay through {@code delegate} when the entity finishes. For
     * everything else, hands back the delegate's buffer directly — the original wiring. See the routing note above
     * {@link #wrap}.
     */
    private static VertexConsumer recordFor(MultiBufferSource delegate, RenderType renderType) {
        if (!replayLanesForCurrentEntity) {
            return delegate.getBuffer(renderType);
        }

        var recorder = new RecordingVertexConsumer();
        PENDING_REPLAYS.add(new PendingReplay(recorder, delegate, renderType));
        return recorder;
    }

    /**
     * Collects finished vertices in memory. Implements the six primitive methods of {@link VertexConsumer} — every
     * default method funnels into those — by keeping one open vertex and committing it when the next one begins.
     */
    private static final class RecordingVertexConsumer implements VertexConsumer {

        private static final int FLOATS_PER_VERTEX = 11;

        private float[] data = new float[FLOATS_PER_VERTEX * 64];

        private int vertexCount;

        private boolean open;

        private float x, y, z, u, v, nx, ny, nz;

        private int color = 0xFFFFFFFF;

        private int overlay = net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY;

        private int light;

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            commit();
            this.open = true;
            this.x = x;
            this.y = y;
            this.z = z;
            this.color = 0xFFFFFFFF;
            this.u = 0.0F;
            this.v = 0.0F;
            this.overlay = net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY;
            this.light = 0;
            this.nx = 0.0F;
            this.ny = 1.0F;
            this.nz = 0.0F;
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            this.color = (alpha & 0xFF) << 24 | (red & 0xFF) << 16 | (green & 0xFF) << 8 | (blue & 0xFF);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            this.u = u;
            this.v = v;
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            this.overlay = (v & 0xFFFF) << 16 | (u & 0xFFFF);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            this.light = (v & 0xFFFF) << 16 | (u & 0xFFFF);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float nx, float ny, float nz) {
            this.nx = nx;
            this.ny = ny;
            this.nz = nz;
            return this;
        }

        private void commit() {
            if (!open) {
                return;
            }
            if (data.length < (vertexCount + 1) * FLOATS_PER_VERTEX) {
                data = java.util.Arrays.copyOf(data, data.length * 2);
            }
            var base = vertexCount * FLOATS_PER_VERTEX;
            data[base] = x;
            data[base + 1] = y;
            data[base + 2] = z;
            data[base + 3] = Float.intBitsToFloat(color);
            data[base + 4] = u;
            data[base + 5] = v;
            data[base + 6] = Float.intBitsToFloat(overlay);
            data[base + 7] = Float.intBitsToFloat(light);
            data[base + 8] = nx;
            data[base + 9] = ny;
            data[base + 10] = nz;
            vertexCount++;
            open = false;
        }

        /* package-private */ int recordedVertexCount() {
            commit();
            return vertexCount;
        }

        /* package-private */ void replayTo(VertexConsumer target) {
            commit();
            for (var i = 0; i < vertexCount; i++) {
                var base = i * FLOATS_PER_VERTEX;
                target.addVertex(data[base], data[base + 1], data[base + 2])
                    .setColor(Float.floatToRawIntBits(data[base + 3]))
                    .setUv(data[base + 4], data[base + 5])
                    .setOverlay(Float.floatToRawIntBits(data[base + 6]))
                    .setLight(Float.floatToRawIntBits(data[base + 7]))
                    .setNormal(data[base + 8], data[base + 9], data[base + 10]);
            }
        }
    }

    public static void endEntity() {
        var diagThisEntity = CLOAK_DIAG
            && !PENDING_REPLAYS.isEmpty()
            && System.currentTimeMillis() - lastDiagMillis > 1000L;

        if (diagThisEntity) {
            lastDiagMillis = System.currentTimeMillis();
            var sceneTexture = -1;
            try {
                sceneTexture = Minecraft.getInstance()
                    .getTextureManager()
                    .getTexture(PredatorSceneColor.LOCATION)
                    .getId();
            } catch (Exception exception) {
                // Reported as -1 below — an unresolvable scene texture is itself a finding.
            }
            com.predator.Predator.LOGGER.info(
                "[CloakDiag] entity={} lanes={} sceneColorAvailable={} sceneTextureId={}",
                currentEntity == null ? "null" : currentEntity.getType().toString(),
                PENDING_REPLAYS.size(),
                PredatorSceneColor.isAvailable(),
                sceneTexture
            );
        }

        // Replay in registration order — back-to-front by construction of the wrap sites — through the ORIGINAL
        // source, one type at a time. See the design note above.
        for (var pending : PENDING_REPLAYS) {
            pending.recorder().replayTo(pending.delegate().getBuffer(pending.renderType()));

            if (diagThisEntity) {
                var glError = com.mojang.blaze3d.platform.GlStateManager._getError();
                com.predator.Predator.LOGGER.info(
                    "[CloakDiag]   lane type={} vertices={} glErrorAfterReplay={}",
                    pending.renderType().toString(),
                    pending.recorder().recordedVertexCount(),
                    glError
                );
            }
        }
        PENDING_REPLAYS.clear();
        currentEntity = null;
    }

    /**
     * Set while a HELD ITEM is being drawn, so the cloak leaves it alone.
     * <p>
     * ⚠⚠ THE CLOAK WRAPS THE BUFFER FOR THE WHOLE ENTITY RENDER, so everything drawn inside that pass inherits the
     * effect — the body, the armour, and the sword in its hand. The arcing crackle appearing on a held axe is that
     * working exactly as written, not a stray effect.
     * <p>
     * ⚠ Armour already had its own exclusion for the same reason ({@link #wrapArmour}), so this is the second case of
     * one rule: an effect authored for a body should not be inherited by things the body is carrying.
     * <p>
     * ⚠ Client-render only, one entity at a time, so a plain static is correct here — no thread concern.
     */
    private static boolean renderingHeldItem;

    public static void beginHeldItem() {
        renderingHeldItem = true;
    }

    public static void endHeldItem() {
        renderingHeldItem = false;
    }

    /** {@return whether the cloak should leave the current draw alone} */
    public static boolean isRenderingHeldItem() {
        return renderingHeldItem;
    }

    public static Entity currentEntity() {
        return currentEntity;
    }

    private static boolean isEntityModelPass(RenderType renderType) {
        return renderType.format() == DefaultVertexFormat.NEW_ENTITY;
    }

    /**
     * The visible ripple: a slowly scrolling shimmer laid over the body, built on the same {@code energySwirl}
     * mechanism as the charged-creeper arcing that already works here.
     * <p>
     * ⚠ This exists because the refraction alone CANNOT read at the requested opacity. At ~5% a refracted sample of the
     * background is, by construction, almost identical to the background itself — the distortion is real but there is
     * not enough contrast left in it to see. A scrolling overlay carries its own contrast and its own motion, so it
     * stays visible however faint the body is. The refraction supplies the physical bend; this supplies the tell.
     */
    /**
     * The shimmer pass.
     * <p>
     * ⚠ TRANSLUCENT, not additive. {@code energySwirl} uses {@code blendFunc(ONE, ONE)} — the source is added at full
     * strength and there is no source-alpha term at all, so an additive pass can only ever ADD light and can never let
     * more background through. That is why it always looked solid however far the colour was turned down, and why alpha
     * behaved as an on/off gate rather than a dial.
     * <p>
     * {@code entityNoOutline} blends normally, so alpha genuinely controls transparency here, and it writes colour only
     * — no depth — so the shimmer cannot punch a hole in water the way an ordinary translucent type would.
     * <p>
     * Motion comes from the pulse modulating alpha per vertex, not from a scrolling texture matrix.
     */
    private static RenderType rippleOverlay() {
        return RenderType.entityNoOutline(RIPPLE_TEXTURE);
    }

    private static RenderType energySwirl() {
        // Scroll the shell so the arcing crawls over the body instead of sitting still.
        var minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        var time = level == null ? 0.0F : (float) level.getGameTime() + minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        var scroll = time * 0.01F % 1.0F;
        return RenderType.energySwirl(ENERGY_SWIRL_TEXTURE, scroll, scroll);
    }

    private record ConcealedBufferSource(
        MultiBufferSource delegate,
        RenderType cloakType,
        ResourceLocation bodyTexture
    ) implements MultiBufferSource {

        @Override
        public VertexConsumer getBuffer(RenderType renderType) {
            // A concealed hunter casts no shadow. Without this the cloak leaves a full-opacity silhouette on the
            // ground in daylight — the single loudest tell the feature had.
            if (renderType == SHADOW_RENDER_TYPE) {
                return NoOpVertexConsumer.INSTANCE;
            }

            if (!isEntityModelPass(renderType)) {
                return delegate.getBuffer(renderType);
            }

            // ⚠ Non-body passes are DROPPED, not faded. Alpha-clamping them does nothing: capes draw through
            // entitySolid, which has NO_TRANSPARENCY, so the alpha is simply ignored and the cape renders at full
            // opacity on an otherwise invisible wearer. Their textures cannot be swapped either — atlas UVs, the
            // magenta-block problem — so the only correct answer is not to draw them.
            if (!isOwnBodyPass(renderType, bodyTexture)) {
                return NoOpVertexConsumer.INSTANCE;
            }

            // ⚠ ONE buffer at a time outside the world render. In the level pass the buffer source is batched and
            // keeps a builder alive per render type, so asking for two and writing to both is fine. The inventory and
            // creative previews go through an IMMEDIATE source, where requesting a second render type ENDS and flushes
            // the first — so the silhouette's builder was already closed by the time its first vertex arrived, and
            // BufferBuilder threw "Not building!". Hence a single pass there.
            if (!PredatorSceneColor.isRenderingLevel()) {
                return new SilhouetteVertexConsumer(delegate.getBuffer(cloakType));
            }

            // ⚠ Both types are outside vanilla's fixed map — pulled from private lanes so their builders
            // can be open simultaneously. See the lane doc above endEntity().
            return com.mojang.blaze3d.vertex.VertexMultiConsumer.create(
                new SilhouetteVertexConsumer(recordFor(delegate, cloakType)),
                new RippleVertexConsumer(recordFor(delegate, rippleOverlay()))
            );
        }
    }

    private record ShortingOutBufferSource(
        MultiBufferSource delegate
    ) implements MultiBufferSource {

        @Override
        public VertexConsumer getBuffer(RenderType renderType) {
            var normal = delegate.getBuffer(renderType);

            // Shadow draws normally here — the wearer is fully visible while shorting out — but it must never be
            // chained to the swirl. Painting the charged-creeper shell across a flat ground quad is what produced the
            // bright pattern at the wearer's feet in water.
            if (
                renderType == SHADOW_RENDER_TYPE || !isEntityModelPass(renderType)
                    || !PredatorSceneColor.isRenderingLevel()
            ) {
                return normal;
            }

            // ⚠ The body keeps the game's source (its batching and layer order are vanilla's business); only
            // the added swirl moves to a private lane so it cannot end the body's fallback builder mid-wrap.
            var swirl = recordFor(delegate, energySwirl());
            return com.mojang.blaze3d.vertex.VertexMultiConsumer.create(normal, swirl);
        }
    }

    /**
     * Passes geometry straight through but clamps alpha down to the silhouette level. Only {@code setColor} is touched
     * — positions, UVs, lightmap and normals are the model's own, unmodified.
     */
    /**
     * Draws the body by sampling the scene copy at each vertex's own screen position, nudged along the surface normal.
     * That nudge is the refraction: geometry angled away from you pulls its sample sideways, so what shows through a
     * limb is the terrain slightly beside it rather than directly behind it — light bending round a body of water.
     * <p>
     * No custom core shader anywhere in this. Screen-space coordinates are computed per vertex on the CPU and written
     * as ordinary UVs against the scene-copy texture, so the effect rides a vanilla render type already proven to work
     * here. The trade is per-vertex rather than per-pixel warping — interpolated across each face — which on cube
     * geometry is nearly invisible, and it buys immunity from every shader-pack interception problem a custom shader
     * would have introduced.
     */
    private record RefractingBufferSource(
        MultiBufferSource delegate,
        RenderType sceneType,
        RenderType outlineType,
        ResourceLocation bodyTexture,
        float originX,
        float originY,
        float originZ
    ) implements MultiBufferSource {

        @Override
        public VertexConsumer getBuffer(RenderType renderType) {
            if (renderType == SHADOW_RENDER_TYPE) {
                return NoOpVertexConsumer.INSTANCE;
            }

            if (!isEntityModelPass(renderType)) {
                return delegate.getBuffer(renderType);
            }

            // ⚠ TWO passes, not one. Refraction alone samples the background so accurately that against anything
            // uniform — water, sky, flat grass — it reproduces it exactly and the wearer disappears completely. That is
            // not a cloak, it is deletion. The faint silhouette pass rides on top so there is ALWAYS an outline to
            // catch, independent of how much contrast the background happens to have. This is the two-term
            // decomposition from the original design: refractive interior, faint edge.
            // Same rule as the silhouette path: capes and head items are dropped outright.
            if (!isOwnBodyPass(renderType, bodyTexture)) {
                return NoOpVertexConsumer.INSTANCE;
            }

            // ⚠ ONE buffer at a time outside the world render, exactly as ConcealedBufferSource and the armour path
            // already do. This source is worse than either: it opens up to THREE buffers and chains two
            // VertexMultiConsumers. In the level pass the buffer source is batched and keeps a builder alive per
            // render type, so that is fine. Inventory and F5 previews go through an IMMEDIATE source, where asking for
            // a second render type ENDS and flushes the first — so the refraction consumer's builder was already
            // closed by the time its first vertex arrived and BufferBuilder threw "Not building!".
            //
            // This guard was added to the other three paths and missed here. Same crash, third location.
            if (!PredatorSceneColor.isRenderingLevel()) {
                return new SilhouetteVertexConsumer(delegate.getBuffer(outlineType));
            }

            // ⚠ Three simultaneous non-fixed types — the worst offender for the shared fallback builder.
            // Each pulls from its own lane; endEntity() flushes scene, then silhouette, then ripple.
            return com.mojang.blaze3d.vertex.VertexMultiConsumer.create(
                com.mojang.blaze3d.vertex.VertexMultiConsumer.create(
                    new RefractionVertexConsumer(recordFor(delegate, sceneType), originX, originY, originZ),
                    new SilhouetteVertexConsumer(recordFor(delegate, outlineType))
                ),
                new RippleVertexConsumer(recordFor(delegate, rippleOverlay()))
            );
        }
    }

    /**
     * Rewrites each vertex's UV to its own screen position plus a normal-driven offset.
     * <p>
     * The combined {@code addVertex} overload is the one overridden with real work because it is the only one that
     * delivers position and normal together — in the primitive chain the normal arrives last, after the UV has already
     * been written. A caller using that chain falls through to pass-through behaviour rather than breaking.
     */
    private static final class RefractionVertexConsumer implements VertexConsumer {

        /** How far a fully side-on surface drags its sample, in screen widths. A nudge, not a lens. */
        private static final float REFRACTION_STRENGTH = 0.012F;

        /** How far the travelling ripple pushes a sample. This is the wobble, and it is the whole tell. */
        private static final float RIPPLE_AMPLITUDE = 0.010F;

        /** How tightly the ripple bands sit across the body. Higher = more, finer waves. */
        private static final float RIPPLE_FREQUENCY = 2.6F;

        /** How fast the bands travel. Slow — this is a lake surface, not a heat shimmer. */
        private static final float RIPPLE_SPEED = 0.11F;

        /**
         * ⚠ The refraction pass is FAINT, not opaque. Drawn solid it replaces the background instead of disturbing it,
         * which turns the wearer into a person-shaped mirror — a hard edge showing a displaced image, the exact
         * opposite of seeing through them. At roughly 5% the real background shows through and the sampled copy only
         * rides on top as a disturbance, so what you catch is the wobble rather than a surface.
         */
        /**
         * ⚠ HIGH on purpose, unlike every other pass here. This one samples the scene BEHIND the wearer, so at full
         * strength it does not read as a solid layer — it reads as clear glass, because it is showing you the same
         * background you would see anyway. The only thing that makes it visible is where the lens bends it.
         * <p>
         * Held low (5-14) it was self-defeating: 95% of what showed through the body was the real, undistorted
         * background, so any amount of bending was drowned out. It looked like a mirror at high values ONLY while the
         * sample axis was flipped and it was showing a mirrored scene; with sampling correct, high is right.
         */
        /**
         * ⚠ Read this as roughly DOUBLE its face value. {@code entityNoOutline} does not cull back faces, so every part
         * of the body blends the scene sample twice — once on the far surface, once on the near one — and the two
         * sample slightly different offsets because the lens displaces by position. The result compounds: 70 behaved
         * closer to 130, which is why the body read as a darker, muddier patch of terrain rather than clear glass.
         */
        private static final int REFRACTION_ALPHA = 34;

        private static final int TINT = 255;

        private final VertexConsumer delegate;

        private final float time;

        private final float centreU;

        private final float centreV;

        /** Fish-eye strength: how far the outermost part of the body drags its sample outward, as a fraction. */
        // ⚠ Aug 27 round 2, his ruling: 1.50 bent so far it sampled SKY over the model ("more sky than
        // distortion"). 1.20 keeps the asked-for bump over the original 1.10 without reaching past the terrain.
        private static final float LENS_STRENGTH = 1.20F;

        /** Screen radius over which the lens reaches full strength. Keeps the bow on the body, not the scene. */
        private static final float LENS_RADIUS = 0.06F;

        private RefractionVertexConsumer(VertexConsumer delegate, float originX, float originY, float originZ) {
            this.delegate = delegate;

            // Project the wearer's own centre once per frame. Everything lenses radially away from this point,
            // which is what makes it read as a lens over a body rather than a general screen wobble.
            var projectedCentre = new Vector4f(originX, originY, originZ, 1.0F)
                .mul(RenderSystem.getModelViewMatrix())
                .mul(RenderSystem.getProjectionMatrix());
            var centreW = projectedCentre.w <= 0.0F ? 0.0001F : projectedCentre.w;
            this.centreU = projectedCentre.x / centreW * 0.5F + 0.5F;
            this.centreV = projectedCentre.y / centreW * 0.5F + 0.5F;

            var minecraft = Minecraft.getInstance();
            var level = minecraft.level;
            this.time = level == null
                ? 0.0F
                : ((float) (level.getGameTime() % 24000L)
                    + minecraft.getTimer().getGameTimeDeltaPartialTick(false)) * RIPPLE_SPEED;
        }

        @Override
        public void addVertex(
            float x,
            float y,
            float z,
            int color,
            float u,
            float v,
            int packedOverlay,
            int packedLight,
            float normalX,
            float normalY,
            float normalZ
        ) {
            // ⚠ modelView FIRST, then projection. Entity vertices are NOT in eye space: LevelRenderer creates a
            // fresh PoseStack for the entity pass (after the "entities" marker) with no camera rotation in it, so
            // the rotation lives entirely in the modelview matrix and is applied on the GPU. Projecting without it
            // threw every UV far outside 0..1, they all clamped to the same screen corner, and the body rendered as
            // one flat colour — whatever happened to be in that corner, usually sky.
            var projected = new Vector4f(x, y, z, 1.0F)
                .mul(RenderSystem.getModelViewMatrix())
                .mul(RenderSystem.getProjectionMatrix());

            // ⚠ NEVER skip a vertex. Returning early here left the quad short: the paired consumer in the
            // VertexMultiConsumer wrote its vertex and this one did not, so the BufferBuilder desynchronised and threw
            // "Not building!" mid-quad. A degenerate vertex is always better than a missing one.
            var safeW = projected.w <= 0.0F ? 0.0001F : projected.w;
            var screenU = projected.x / safeW * 0.5F + 0.5F;
            // ⚠ V is NOT flipped. The scene copy comes from glCopyTexSubImage2D off the framebuffer, and GL texture
            // space has its origin at the BOTTOM-left — so it is already stored the same way up as the framebuffer.
            // Using the screen-space convention (0.5 - y) mirrored every sample vertically: angling the camera DOWN
            // sampled the top of the frame, which is sky, and painted the wearer sky-blue. It also meant the lens was
            // bowing a mirrored image, so the distortion never lined up with the body and read as absent.
            var screenV = projected.y / safeW * 0.5F + 0.5F;

            delegate.addVertex(x, y, z);
            delegate.setColor(TINT, TINT, TINT, REFRACTION_ALPHA);
            // The ripple. Phase is driven by the vertex's own position so different parts of the body wobble out of
            // step with each other — that is what reads as a surface rather than the whole shape sliding about. The
            // two axes use different multipliers so the motion never collapses into a straight diagonal.
            var phase = (x + y * 1.7F + z) * RIPPLE_FREQUENCY + time;
            var rippleU = Mth.sin(phase) * RIPPLE_AMPLITUDE;
            var rippleV = Mth.cos(phase * 0.77F) * RIPPLE_AMPLITUDE;

            // Fish-eye: push the sample outward from the wearer's centre, harder the further out the vertex sits.
            // Linear in radius rather than constant, so the middle stays honest and only the edges bow — which is what
            // separates a lens from a uniform smear.
            var lensU = screenU - centreU;
            var lensV = screenV - centreV;
            var radius = (float) Math.sqrt(lensU * lensU + lensV * lensV);
            var lens = LENS_STRENGTH * Math.min(1.0F, radius / LENS_RADIUS);

            delegate.setUv(
                clamp(screenU + normalX * REFRACTION_STRENGTH + rippleU + lensU * lens),
                clamp(screenV + normalY * REFRACTION_STRENGTH + rippleV + lensV * lens)
            );
            delegate.setUv1(packedOverlay & 0xFFFF, packedOverlay >> 16 & 0xFFFF);
            // Full-bright: the scene copy is already lit, so lighting it again would darken it wrongly.
            delegate.setUv2(240, 240);
            delegate.setNormal(normalX, normalY, normalZ);
        }

        private static float clamp(float value) {
            return Math.max(0.001F, Math.min(0.999F, value));
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            delegate.setColor(TINT, TINT, TINT, Math.min(alpha, REFRACTION_ALPHA));
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(240, 240);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float normalX, float normalY, float normalZ) {
            delegate.setNormal(normalX, normalY, normalZ);
            return this;
        }
    }

    /** Draws the scrolling ripple at its own fixed alpha, independent of the silhouette's. */
    private record RippleVertexConsumer(
        VertexConsumer delegate
    ) implements VertexConsumer {

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            delegate.setColor(
                RIPPLE_TINT,
                RIPPLE_TINT,
                RIPPLE_TINT,
                Math.max(1, Math.round(RIPPLE_ALPHA * effectIntensityForCurrentEntity))
            );
            return this;
        }

        /**
         * ⚠ The pulse belongs on THIS pass, not on the silhouette.
         * <p>
         * The silhouette draws the wearer's own texture, which is dark — so modulating its alpha produces a pulse of
         * DARKNESS sweeping down the body, which is what "more dark than shimmer" was describing. This pass blends
         * additively against a bright sheet, so the same wave adds light instead of subtracting it, and reads as a
         * crest of shimmer travelling downward.
         */
        @Override
        public void addVertex(
            float x,
            float y,
            float z,
            int color,
            float u,
            float v,
            int packedOverlay,
            int packedLight,
            float normalX,
            float normalY,
            float normalZ
        ) {
            var minecraft = Minecraft.getInstance();
            var level = minecraft.level;
            var time = level == null
                ? 0.0F
                : ((float) (level.getGameTime() % 24000L)
                    + minecraft.getTimer().getGameTimeDeltaPartialTick(false)) * 1.90F;

            // ⚠ Wavelength is deliberately LONGER than the body (frequency 0.9 ≈ 7 blocks per cycle). At 2.2 a full
            // cycle spanned under 3 blocks, so each limb and the head each caught their own crest and it read as parts
            // blinking independently rather than one band travelling down a figure. One long crest sweeping the whole
            // model is what "pass over the whole model" needs.
            // Wavelength stays long (1.4 ≈ 4.5 blocks) so one crest spans the figure instead of each limb catching
            // its own. The trough is held at 35% rather than near zero: with a wave this long the WHOLE body sits at
            // roughly the same phase, so a deep trough made it vanish entirely for part of every cycle instead of
            // sweeping. A high floor keeps it continuously present and lets the crest read as a brightening.
            // ⚠ Frequency raised WITH the speed, deliberately. Speed alone moves a single crest faster until it can
            // cross a limb between frames and reads as flicker rather than flow; the pulse is per-vertex, so it cannot
            // resolve a crest moving faster than the geometry it is interpolated across. More, tighter crests keep
            // each one on screen long enough to track while raising the apparent rate.
            var wave = Mth.sin(y * 2.6F + time) * 0.5F + 0.5F;
            var alpha = Math.max(1, Math.round(RIPPLE_ALPHA * effectIntensityForCurrentEntity * (0.35F + 0.65F * wave)));

            delegate.addVertex(x, y, z);
            delegate.setColor(RIPPLE_TINT, RIPPLE_TINT, RIPPLE_TINT, alpha);
            delegate.setUv(u, v);
            delegate.setUv1(packedOverlay & 0xFFFF, packedOverlay >> 16 & 0xFFFF);
            delegate.setUv2(240, 240);
            delegate.setNormal(normalX, normalY, normalZ);
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            // ⚠ FULL-BRIGHT, and it has to be. energySwirl is lit by the lightmap AND blends additively, so in shadow
            // the two multiply out to nothing — which is why the shimmer only appeared where moonlight happened to
            // catch the model. Every effect this is modelled on (charged creeper, enchanted glint) is full-bright for
            // exactly this reason. RIPPLE_ALPHA is the brightness control instead.
            delegate.setUv2(240, 240);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float normalX, float normalY, float normalZ) {
            delegate.setNormal(normalX, normalY, normalZ);
            return this;
        }
    }

    /** Swallows geometry entirely. Used to drop the shadow pass for a concealed wearer. */
    /** The shader-pack ghost: one draw of the body through vanilla entityTranslucent, everything else dropped. */
    private record PackGhostBufferSource(
        MultiBufferSource delegate,
        ResourceLocation bodyTexture
    ) implements MultiBufferSource {

        @Override
        public VertexConsumer getBuffer(RenderType renderType) {
            if (renderType == SHADOW_RENDER_TYPE) {
                return NoOpVertexConsumer.INSTANCE;
            }

            if (!isEntityModelPass(renderType)) {
                return delegate.getBuffer(renderType);
            }

            if (!isOwnBodyPass(renderType, bodyTexture)) {
                return NoOpVertexConsumer.INSTANCE;
            }

            // A single FIXED vanilla type — its builder is always available, so this is safe in the level pass
            // and the immediate-source previews alike, with no private lanes involved.
            return new PackGhostVertexConsumer(delegate.getBuffer(RenderType.entityTranslucent(bodyTexture)));
        }
    }

    /** Forces every vertex to the pack-ghost alpha; geometry, UVs and lighting pass through untouched. */
    private record PackGhostVertexConsumer(
        VertexConsumer delegate
    ) implements VertexConsumer {

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            delegate.setColor(red, green, blue, Math.max(1, Math.round(PACK_GHOST_ALPHA * effectIntensityForCurrentEntity)));
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            return this;
        }
    }

    private static final class NoOpVertexConsumer implements VertexConsumer {

        private static final NoOpVertexConsumer INSTANCE = new NoOpVertexConsumer();

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float normalX, float normalY, float normalZ) {
            return this;
        }
    }

    private record SilhouetteVertexConsumer(
        VertexConsumer delegate
    ) implements VertexConsumer {

        /** How tightly the pulse bands sit vertically, in blocks. Lower = a broader, slower-reading band. */
        private static final float PULSE_FREQUENCY = 2.2F;

        /** How fast the band travels down the body. */
        private static final float PULSE_SPEED = 0.22F;

        /** Floor of the pulse: how visible the silhouette stays at the trough. 1.0 would mean no pulse at all. */
        /**
         * Peak opacity of the pulse crest, as a multiple of {@link #SILHOUETTE_ALPHA}.
         * <p>
         * ⚠ The shimmer is now the body's OWN opacity swinging, not a second texture laid over it. Every additive
         * overlay tried here — energy swirl, glint — renders its bright regions as hard plates on whichever faces catch
         * them, so it could be faint or even but never both. Modulating the silhouette itself covers the whole model
         * uniformly, needs no second texture (so it is safe on armour), and lands exactly on the brief: the body sits
         * at {@code SILHOUETTE_ALPHA} and the crest lifts it about a quarter above that.
         */
        private static final float PULSE_PEAK = 1.0F;

        private static final float PULSE_FLOOR = 0.75F;

        /**
         * Travelling pulse, applied per vertex.
         * <p>
         * ⚠ Modulates ALPHA only — no texture is substituted anywhere in this path, which is what makes it safe on
         * armour. Every magenta block this feature has produced came from drawing geometry against a texture its UVs
         * were not authored for; a pulse never touches UVs, so it works identically on the body, the mask and any
         * armour added later, whatever their texture sizes.
         * <p>
         * Phase is driven by the vertex's Y. Entity vertices arrive with the camera rotation still in the modelview, so
         * this coordinate is a world-space vertical offset — the band therefore travels down the body and stays put
         * when the viewer turns or tilts, rather than sliding around with the camera.
         */
        @Override
        public void addVertex(
            float x,
            float y,
            float z,
            int color,
            float u,
            float v,
            int packedOverlay,
            int packedLight,
            float normalX,
            float normalY,
            float normalZ
        ) {
            var minecraft = Minecraft.getInstance();
            var level = minecraft.level;
            var time = level == null
                ? 0.0F
                : ((float) (level.getGameTime() % 24000L)
                    + minecraft.getTimer().getGameTimeDeltaPartialTick(false)) * PULSE_SPEED;

            // Downward travel: adding time to a Y-driven phase moves the crest toward lower Y as time advances.
            // Only a slight breath here. The visible shimmer is the additive ripple pass; modulating the silhouette
            // hard just darkens the body, because this pass draws the wearer's own (dark) texture.
            var wave = Mth.sin(y * PULSE_FREQUENCY + time) * 0.5F + 0.5F;
            var scale = PULSE_FLOOR + (1.0F - PULSE_FLOOR) * wave;
            var alpha = Math.max(1, Math.round(SILHOUETTE_ALPHA * effectIntensityForCurrentEntity * scale));

            delegate.addVertex(x, y, z);
            delegate.setColor(255, 255, 255, alpha);
            delegate.setUv(u, v);
            delegate.setUv1(packedOverlay & 0xFFFF, packedOverlay >> 16 & 0xFFFF);
            delegate.setUv2(packedLight & 0xFFFF, packedLight >> 16 & 0xFFFF);
            delegate.setNormal(normalX, normalY, normalZ);
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            delegate.setColor(
                red,
                green,
                blue,
                Math.min(alpha, Math.max(1, Math.round(SILHOUETTE_ALPHA * effectIntensityForCurrentEntity)))
            );
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float normalX, float normalY, float normalZ) {
            delegate.setNormal(normalX, normalY, normalZ);
            return this;
        }
    }
}
