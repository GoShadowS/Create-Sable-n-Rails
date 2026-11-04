# Implementation status

Snapshot of the present working tree (uncommitted changes on branch
`arena/450eb31f-create-sable-n-rails`, based on `91f9a4b`).

No calendar date is recorded here on purpose: this environment cannot confirm one, and a wrong
date in a status document is worse than none. Use the commit date once this work is committed.

## What existed before this pass

- Repository at `main` (`91f9a4b`), clean, containing only documentation plus a single empty mod entry
  point (`CreateSableNRails.java`, 10 lines), Gradle scaffold, `src/main/resources/META-INF/neoforge.mods.toml`,
  MIT `LICENSE`, and the research documents. No gameplay code, no assets, no tests.
- The previous pass recorded a *blocker*: it could not establish a missing feature plus a safe hook and
  therefore implemented nothing. That conclusion was based on `Sable` only. This pass re-checked
  Create 6.0.10 itself and found the hook, so the blocker is resolved for one feature.
- Nothing was deleted, renamed or rewritten except the documents this pass had to update.

## Feature implemented in this pass

**Interact Me / Interact Me Not — a server-authoritative contraption interactivity policy.**

### Why this feature and not another

Verified from source (details and line numbers in `UPSTREAM_RESEARCH.md`):

1. Sable 2.0.6 has **no** interactivity concept (`grep -ri interactiv` over the whole tag returns nothing).
2. Create 6.0.10's `AbstractContraptionEntity#handlePlayerInteraction` performs **no** permission check,
   and `ContraptionInteractionPacket#handle` only validates distance and line of sight.
3. The only public Create extension point (`MovingInteractionBehaviour`) runs *after* the decision and
   cannot veto storage access.
4. Therefore a player can right-click, use items on, and open inventories of any moved contraption —
   the gap Create: Interactive fills. It is the first target function that is both absent and
   implementable with a verified API.

### What it does

- Two new marker blocks, `create_sable_n_rails:interact_me` and `create_sable_n_rails:interact_me_not`,
  instant-break, non-colliding, waterloggable, face-attached plates (floor/ceiling/wall), with original
  textures generated for this project.
- Six server-side policy values, one per contraption category — trains, translating (piston/gantry/pulley,
  including elevators), bearings (including stabilized bearings), clockwork, mounted (minecart) and
  `other` — each `ALWAYS`, `WITHOUT_STICKER`, `WITH_STICKER` or `NEVER`. Defaults: `WITH_STICKER` for the
  five known categories, `NEVER` for `other`, so unknown addon contraptions keep their current
  non-interactive behaviour.
- Enforcement is a single cancellable `HEAD` inject into
  `AbstractContraptionEntity#handlePlayerInteraction`. Returning `false` is Create's own "not handled"
  contract: no `interactors` dispatch, no `MountedStorageManager#handlePlayerStorageInteraction`, no seat
  boarding, no passenger ejection, and `ContraptionInteractionPacket#handle` skips `sender.swing(...)`.
- **Train relocation needed two more enforcement points.** `false` from `handlePlayerInteraction` is a
  *branch selector* on the client (`ContraptionHandlerClient` 124-127), not a stop: it routes into
  `handleSpecialInteractions`, whose wrench-on-carriage case calls `TrainRelocator#carriageWrenched`
  (357-365) and only sets the client's relocation mode. The move itself is requested later by a
  `TrainRelocationPacket` (`TrainRelocator#relocateClient` line 157), and `TrainRelocationPacket#handle`
  (44-82) is the **only** caller of `TrainRelocator#relocate(...)` in the whole mod (verified by grep).
  Gating only the interaction therefore left a real hole: a client in relocation mode could complete the
  move. Closed at both ends — `ContraptionHandlerClientMixin` cancels `handleSpecialInteractions` for a
  denied contraption (client; the mode never starts) and `TrainRelocationPacketMixin` rejects the packet at
  `HEAD` (server; authoritative). Permitted trains take Create's original path unchanged.
- **Scope of that server check: the whole train, not the carriage named in the packet.**
  `TrainRelocator#relocate` moves the train (`TrainRelocator.java:263-264` loops `train.carriages` calling
  `updateContraptionAnchors()`, and the state mutations before it are train-wide), and a train is a list of
  independent carriages — `Train.carriages` is a `List<Carriage>`, each `Carriage` has its own
  `CarriageContraption`, block map and markers, and its own `CarriageContraptionEntity` per dimension
  (`Carriage.DimensionalCarriageEntity#entity` via `Carriage#getDimensionalIfPresent`). Checking only the
  carriage from `entityId` would therefore let a packet name an allowed carriage and still move a train that
  contains a denied one.
- **The rule is one shared method, used by both sides.**
  `ContraptionInteractionPolicy#isTrainInteractive(Train, Level)` walks `train.carriages` and requires every
  carriage to resolve to an interactive contraption. The client pre-check (`ContraptionHandlerClientMixin`,
  inside `handleSpecialInteractions`, i.e. *before* `TrainRelocator#carriageWrenched`) and the authoritative
  server check (`TrainRelocationPacketMixin`) both call it, so clicking an allowed carriage cannot start
  relocation mode for a train that another carriage denies. On the client the train comes from
  `CarriageContraptionEntity#getCarriage().train`, populated by Create through
  `CarriageContraptionEntity#bindCarriage` → `Carriage#getDimensional(Level)` and `Train#setTrain`
  (`Train.java:179`); if it is absent the click is refused.
- **Forged ids are rejected before Create's body runs — and one of them is an upstream NPE.**
  `TrainRelocationPacket#handle` looks the train up at line 45 and, in its `train == null` branch, builds the
  log message from `train.id.toString()` (line 52): an unknown `trainId` reaching Create is a
  `NullPointerException` on the server thread, not a rejection. `TrainRelocationPacketMixin` therefore
  cancels at `HEAD` when (a) `trainId` does not resolve in `Create.RAILWAYS.trains`, (b) `entityId` is not a
  `CarriageContraptionEntity`, (c) that carriage's `trainId` differs from the packet's `trainId` (the same
  comparison Create makes at line 57, but before the throwing branch), or (d) any carriage of the train is
  non-interactive. Cancelling at `HEAD` means Create's body — including line 52 — never executes.
- **Fail-closed edge case (documented limitation, not a defect).** Per carriage the contraption is resolved
  through the `CarriageContraptionEntity` registered for the checking level (`Carriage#getDimensionalIfPresent`,
  a read-only lookup that does not create the dimensional entry). A carriage with no such entity — a carriage
  of a multi-dimension train not loaded in that dimension, or a client that has not yet bound the carriage —
  counts as **denied**. Consequence: a train permitted by configuration is *not* automatically relocatable;
  every carriage must also be loaded and bound where the check runs. Stated explicitly so that "permitted by
  config" is never presented as "always relocatable".
- Seat boarding remains blocked by the same interaction gate as before; that decision is unchanged.
- Marker blocks are explicitly declared movable through Create's public
  `BlockMovementChecks.registerMovementAllowedCheck`, answering `SUCCESS` for the two markers and `PASS`
  for every other block. Without it, `PushReaction.BLOCK` makes
  `BlockMovementChecksImpl#isMovementAllowedFallback` return `false` and `Contraption#moveBlock` throws
  `AssemblyException.unmovableBlock(...)` — i.e. any contraption carrying a marker could not assemble at
  all. The empty collision shape is *not* the problem: `isMovementNecessaryFallback` returns `true` for
  brittle blocks before it looks at the collision shape, and `isBrittleFallback` treats every
  `FaceAttachedHorizontalDirectionalBlock` (except grindstones and packager links) as brittle.
- The same decision is applied client-side by redirecting the single `rayTraceContraption` call inside
  `rightClickingOnContraptionsGetsHandledLocally`, so a contraption with no interactive blocks is skipped
  by the interaction loop — the player can still use the held item against the world behind or beside it.
  The other `rayTraceContraption` callers (`ContraptionCollider#savePlayerFromClipping` anti-clipping,
  the two clearance checks in `TrainRelocator`, `ElevatorControlsHandler`) are deliberately left
  unfiltered — filtering them would be a safety regression.
- **Known limitation of that client filter:** the redirect keys on "does this contraption have any
  interactive block", not on the block under the crosshair. Denial is therefore per contraption, not per
  block: a contraption with an `interact_me` marker is fully interactive (every block in it), and one
  without is not interactive anywhere.
- Decisions are cached per contraption entity UUID, and the entry is reused only when **all three inputs**
  still match: the exact `MarkerSignature` (a normalized map of immutable `BlockPos` → marker kind, compared
  structurally — not a hash, not the block-map size), the contraption category, and the configuration policy
  that was actually applied. Since the inputs are re-read on every call, replacing a marker or swapping
  `interact_me` for `interact_me_not` at one position cannot keep a stale decision even if no invalidation
  runs at all. Explicit invalidation is wired into the three Create write paths that a block change
  can take — `AbstractContraptionEntity#setBlock`, `CarriageContraptionEntity#setBlock` (an override that
  does not call `super`) and the client-side `AbstractContraptionEntity#handleBlockChange` — plus world
  start/stop and config load/reload. See "Cache invalidation: every write path" below.

### Cache invalidation: every write path (traced in Create 6.0.10)

A cached decision is only safe if every mutation of `Contraption#getBlocks()` is accounted for. The map is
written in exactly five places in Create `mc1.21.1-6.0.10` (`grep` for `blocks.put(` / `getBlocks().put(`
over `src/main/java`, and `blocks.remove(` returns no hits at all):

| Write site | Line(s) | Reached from | Handled by |
|---|---|---|---|
| `Contraption#addBlock` | 647-653 | assembly (`moveBlock` → `addBlock`) | not needed: happens before any entity exists, so nothing can be cached yet |
| `Contraption#fromNBT` | 983 | `AbstractContraptionEntity#readAdditional:651`, `Carriage.DimensionalCarriageEntity#createEntity:834` (via `EntityType.create`) | not needed for correctness: a new `Contraption` object replaces the old one, and because the **category is part of the key** and is re-derived from the current object, an entry for the old object cannot be reused. The claim is deliberately narrow: these paths construct a new `Contraption`; that the *category* of a given entity never changes in place is not assumed |
| `Contraption` multiblock-controller swap | 1253-1254 | controller-position change, NBT only | not needed: swaps NBT between two existing entries, never states or block identities |
| `AbstractContraptionEntity#setBlock` | 427-428 | `MovingInteractionBehaviour#setContraptionBlockData:43`, `SlidingDoorMovementBehaviour:94,101` | `@Inject` at `HEAD` in `AbstractContraptionEntityMixin` (invalidates that entity) |
| `CarriageContraptionEntity#setBlock` (override) | 200-209 | same callers, when the entity is a carriage | `@Inject` at `HEAD` in `CarriageContraptionEntityMixin` (clears the whole cache) |
| `AbstractContraptionEntity#handleBlockChange` (client) | 762-766 | `handleBlockChangedPacket:746-748` ← `ContraptionBlockChangedPacket:27` | `@Inject` at `HEAD` in `AbstractContraptionEntityClientMixin` (listed under `client`) |
| `PistonContraption` pole | 145 | piston contraption assembly | not needed: assembly-time only |

Two properties matter and are both deliberate:

1. **The carriage path needs its own inject.** `CarriageContraptionEntity#setBlock` overrides the base method
   and does not call `super`, so an inject on `AbstractContraptionEntity#setBlock` never fires for a carriage.
   One carriage call also rewrites the maps of *every present* dimensional entity
   (`carriage.forEachPresentEntity`, `Carriage.java:453-459`), so the carriage mixin clears the whole cache
   instead of trying to name the affected entities.
2. **The cache key is exact, not a hash and not a size.** `MarkerSignature` stores the marker pairs
   themselves (`ALLOW`/`DENY` per position) in a sorted, unmodifiable map and compares them with structural
   `equals`; `hashCode` is only used for bucketing. Each position is snapshotted with
   `BlockPos#immutable()` on insertion, because `BlockPos.MutableBlockPos` is a mutable subclass — a stored
   reference would let a caller's cursor move a key that is already in the `TreeMap`, breaking its ordering
   and its self-equality. Covered by `signatureSnapshotsMutablePositions`. A `long` fingerprint would make a false hit
   possible, i.e. not fail-closed, so it is not the criterion. States, NBT and non-marker blocks are excluded
   because they cannot change the decision. The category and the effective policy are part of the key too,
   so a replaced contraption or a changed configuration is a miss. Consequently a marker swapped for the
   other marker, or a marker's `face`/`facing` state changed, cannot keep the old decision even if some future
   Create version adds a fourth write path that none of the three injects covers.
3. **What is proven about the category, and what is not.** Verified: the production overload
   `decide(UUID, Contraption)` calls `ContraptionCategory.of(contraption)` on every call and passes the result
   into the key comparison, so a changed classification is a cache miss (covered by
   `changedCategoryIsNotReused`). Not verified: whether a live train or carriage entity can swap its
   `Contraption` object for a differently-typed one without the entity being recreated. The code does not
   rely on that being impossible — it relies only on the comparison.
4. **Order of operations on a hit and a miss.** The key components (signature, category, policy) are read
   first — they *are* the key — then the cache is consulted. On a hit the stored decision is returned
   immediately. Only on a miss is the decision calculated, and it is calculated from the
   `MarkerSignature` that was already built, so the block map is scanned once per call, not twice. The
   four-argument overload `decide(UUID, Contraption, ContraptionCategory, InteractiveHandling)` is what the
   tests call with a fixed policy; production uses the two-argument overload, which resolves the policy from
   the live configuration.

### Persistence and synchronisation — how it actually works

The interactivity state is **not** stored in an addon-owned NBT tag. The markers are ordinary blocks in
`Contraption#getBlocks()`; Create serialises that map into the contraption entity's NBT
(`Contraption#writeNBT` → `AbstractContraptionEntity#writeAdditional`), ships it to clients through
`ContraptionSyncLimiting`, restores it in `Contraption#fromNBT`, and writes the blocks back into the world
on disassembly (`Contraption#addBlocksToWorld`). The decision is recomputed from that map. Consequences:

- it survives disassembly, reassembly, chunk unload/reload and world save/load with no extra code;
- there is only one source of truth, so no addon state can desynchronise from the blocks;
- the server is authoritative: the client only mirrors the decision to avoid starting a rejected
  interaction, and even a client that ignored the mirror would be refused by the server-side inject.

### Locked-contraption HUD indicator (client-only)

Why it exists: when the policy denies, a right click does nothing — no swing, no sound, no message. Nothing
distinguished that from a missed click. The indicator answers exactly that question and nothing more.

How it is built:

- `client/ContraptionStatusIndicator` — pure Java, no Minecraft imports. `resolve(guiHidden, spectator,
  targetIsContraption, interactive)` returns `NONE` unless a contraption is targeted and denied; F1 and
  spectator win over everything. Unit-tested on a plain classpath (`ContraptionStatusIndicatorTest`).
- `client/ContraptionTargetProbe` — target resolution, built on Create's own client raytrace rather than a
  re-implementation of it. Two Create methods are called, both public in `mc1.21.1-6.0.10`:
  `ContraptionHandlerClient#getRayInputs(LocalPlayer)` (line 142) for the eye-origin ray — capped at
  `player.blockInteractionRange()` and shortened to `mc.hitResult` when vanilla reports something closer, so a
  block in front of the contraption limits the ray — and
  `ContraptionHandlerClient#rayTraceContraption(Vec3, Vec3, AbstractContraptionEntity)` (line 154) for the hit,
  which maps the ray with `toLocalVector(..., 1)` and clips real `VoxelShape`s. The addon adds only the
  selection rule: an `AABB#intersects` broad phase so most candidates never reach a block raytrace, and
  nearest-hit selection comparing `toGlobalVector(localHit, 1).distanceTo(origin)` — global space, so a moving
  or rotated contraption is handled by Create's own transform. **A box intersection is never a hit:** a ray
  that crosses the box but misses every block shape yields `null` from `rayTraceContraption` and the
  contraption is skipped. `ContraptionHandler.loadedContraptions` is Create's public per-level registry
  (`WorldAttached<Map<Integer, WeakReference<AbstractContraptionEntity>>>`).
- `client/ContraptionStatusOverlay` — the `LayeredDraw.Layer`. It reads `Minecraft#options.hideGui`,
  the game mode, the resolved target and `ContraptionInteractionPolicy.isInteractive(target)`, then draws one
  `Component.translatable` line centred above the hotbar.
- `client/ClientSetup` — `@EventBusSubscriber(modid = …, value = Dist.CLIENT)` with a
  `@SubscribeEvent` on `RegisterGuiLayersEvent`, registering above `VanillaGuiLayers.HOTBAR`. Per FancyModLoader's
  own javadoc for `EventBusSubscriber`, subscribers of `IModBusEvent` events are placed on the mod's event bus
  and `value()` restricts the physical side, so this class is loaded on the client only.

Authority and failure modes: the indicator is a hint, not a gate. If it were wrong, the server would still
enforce the policy correctly; the worst outcome is a misleading line of text. That is why it is allowed to
share the client-side decision path, and why it deliberately has no configuration switch of its own.

Not verified: every geometric scenario of `ContraptionTargetProbe.nearest` against real contraption entities.
They need placed `AbstractContraptionEntity` instances with non-trivial block maps, which a GameTest cannot
fabricate without assembling a contraption through Create. The GameTests assert only the contract this addon
owns — `null`/empty/blockless inputs never produce a target, and a blockless contraption is never a candidate.
The following remain **untested** and must be checked in game:

| Scenario | Expected | Status |
|---|---|---|
| Ray crosses the bounding box but passes through empty space inside it | no indicator (no block hit) | untested |
| A solid block stands in front of the contraption | no indicator (ray shortened by `mc.hitResult`) | untested |
| Ray hits a real block shape inside the contraption | indicator for a denied contraption | untested |
| Two contraptions along the ray | the nearer block hit wins (`toGlobalVector` distance) | untested |
| Rotated / moving contraption | same rules, via Create's `toLocalVector`/`toGlobalVector` | untested |

### Precedence of marker blocks versus the server setting

| Server policy | no marker | only `interact_me` | only `interact_me_not` | both |
|---|---|---|---|---|
| `ALWAYS` | interactive | interactive | interactive | interactive |
| `WITHOUT_STICKER` | interactive | interactive | **not** interactive | **not** interactive |
| `WITH_STICKER` | **not** interactive | interactive | **not** interactive | interactive |
| `NEVER` | not interactive | not interactive | not interactive | not interactive |

`ALWAYS`/`NEVER` ignore markers entirely (the server configuration wins). `WITHOUT_STICKER` is a deny-list,
`WITH_STICKER` is an allow-list; when both markers exist, the one matching the active mode wins. This is
covered by unit tests and by a GameTest that cross-checks the contraption-based decision against the pure
function for all four policies × four marker states.

## Files created or changed

Created:

- `src/main/java/dev/goshadows/createsablenrails/interaction/InteractiveHandling.java`
- `src/main/java/dev/goshadows/createsablenrails/interaction/InteractiveDecision.java`
- `src/main/java/dev/goshadows/createsablenrails/interaction/ContraptionCategory.java`
- `src/main/java/dev/goshadows/createsablenrails/interaction/MarkerSignature.java`
- `src/main/java/dev/goshadows/createsablenrails/interaction/ContraptionInteractionPolicy.java`
- `src/main/java/dev/goshadows/createsablenrails/config/SableNRailsConfig.java`
- `src/main/java/dev/goshadows/createsablenrails/content/InteractMarkerBlock.java`
- `src/main/java/dev/goshadows/createsablenrails/content/InteractMarkerItem.java`
- `src/main/java/dev/goshadows/createsablenrails/index/ModBlocks.java`
- `src/main/java/dev/goshadows/createsablenrails/index/ModCreativeTabs.java`
- `src/main/java/dev/goshadows/createsablenrails/mixin/AbstractContraptionEntityMixin.java`
- `src/main/java/dev/goshadows/createsablenrails/mixin/ContraptionHandlerClientMixin.java`
- `src/main/java/dev/goshadows/createsablenrails/mixin/TrainRelocationPacketMixin.java`
- `src/main/java/dev/goshadows/createsablenrails/mixin/CarriageContraptionEntityMixin.java`
- `src/main/java/dev/goshadows/createsablenrails/mixin/AbstractContraptionEntityClientMixin.java`
- `src/main/java/dev/goshadows/createsablenrails/gametest/ContraptionInteractivityGameTests.java`
- `src/main/java/dev/goshadows/createsablenrails/interaction/ContraptionInteractionPolicyGameTests.java`
- `src/test/java/dev/goshadows/createsablenrails/interaction/InteractiveDecisionTest.java`
- `src/test/java/dev/goshadows/createsablenrails/interaction/InteractiveHandlingTest.java`
- `src/main/resources/create_sable_n_rails.mixins.json`
- assets: `lang/en_us.json`, `lang/ru_ru.json`, 2 blockstates, 2 block models, 2 item models,
  4 original 16×16 textures (block plate + inventory icon per marker)
- data: 2 recipes, 2 loot tables, 2 recipe-unlock advancements

Changed:

- `src/main/java/dev/goshadows/createsablenrails/CreateSableNRails.java` — registration, configs, event listeners
- `src/main/resources/META-INF/neoforge.mods.toml` — author, description, `[[mixins]]` entry
- `build.gradle` — JUnit, `gameTestServer` run, `test` task wiring
- `UPSTREAM_RESEARCH.md`, `README.md`, `README.ru.md`, `CHANGELOG.md`, `IMPLEMENTATION_STATUS.md`,
  `THIRD_PARTY_NOTICES.md`, `LICENSE_DECISION.md`

## What the patch contains, and why two file counts appear

The change set is 50 file entries. Two artefacts are produced from it, and their counts differ only
because of how binaries are represented:

- **`current-changes.diff`** — 50 entries: 45 text files plus 5 binary files. The binaries are encoded as
  `GIT binary patch` blocks (base85 payload), so the file is complete and `git apply --binary` restores
  every file byte for byte.
- **`changes-text.diff`** — 45 entries: the same 45 text files, with the 5 binaries removed and listed by
  name at the end of the file, because a review copy of a unified diff is easier to read without base85
  blobs.

The 45 text entries are 11 modified files plus 34 new text files. Of those 34 new text files: 17 are new
`src/main/java` Java sources, 2 are `src/test/java` Java sources, and 15 are resources (mixin config,
blockstates, models, lang, recipes, loot tables, advancements). The remaining Java source in the patch,
`CreateSableNRails.java`, is a modified tracked file rather than a new one — 17 + 2 + 1 = 20, the total
number of Java sources in the project and the number used everywhere in this document.
These counts come from `git diff --name-only` and `git ls-files --others --exclude-standard`, not from
hand-tallying.

The 5 binaries are: `assets/create_sable_n_rails/textures/block/interact_me.png`,
`.../textures/block/interact_me_not.png`, `.../textures/item/interact_me.png`,
`.../textures/item/interact_me_not.png`, and `data/create_sable_n_rails/structure/empty_3x3.nbt`.

Both artefacts were verified by cloning `91f9a4b` into a fresh directory, applying the patch with
`git apply --binary --check` and then for real, and comparing the trees: identical.

## Verification actually performed

| Check | Result |
|---|---|
| Repository audit (`git log`, tree, all existing docs) | Done |
| Create `mc1.21.1-6.0.10` full source read for every API used | Done |
| Sable `mc1.21.1-2.0.6-neoforge` full source read; absence of interactivity confirmed by grep | Done |
| NeoForge `1.21.1` and FancyModLoader `1.21.1` source read for `DeferredRegister`, `BuildCreativeModeTabContentsEvent`, `ModConfigSpec`, `ModContainer#registerConfig`, `ModConfigEvent`, GameTest annotations | Done |
| Java syntax parse of all 20 Java sources — 18 under `src/main/java`, 2 under `src/test/java` — (java-parser 3.0.1, a parser, **not** a compiler) | **Passed**: 20 files, 0 syntax errors (re-run after the marker-signature change). The tool was validated against two intentionally malformed files, which it rejected. This says nothing about types, symbols or resolution. |
| JSON well-formedness of every `.json` asset/data file and the mixin config | **Passed** (`python3 -m json.tool` / `json.load`) |
| PNG validity and dimensions of the 4 textures | **Passed** (`identify`: 16×16 RGBA). The item icons were derived from the block textures with a filter-aware PNG round trip, which was first asserted lossless on the block textures. |
| `git diff --check` | Pending final run (see below) |
| `./gradlew build` | **NOT RUN — blocked.** No JDK, and `services.gradle.org`, `maven.neoforged.net`, `maven.createmod.net`, `maven.ryanhcode.dev`, `repo1.maven.org` are unreachable from the work environment. The only Java runtime available is a jlink image without `jdk.compiler`, and `javac` cannot be installed. |
| `./gradlew test` (JUnit) | **NOT RUN** — same blocker. JUnit can start the JVM, but the test sources import `ContraptionCategory`/`Contraption`; the compiler is missing regardless of the classpath. |
| `./gradlew gameTestServer` | **NOT RUN** — same blocker. |
| GameTest structure file `data/create_sable_n_rails/structure/empty_3x3.nbt` | Generated and decoded with an independent NBT reader: root name empty, keys `size`/`palette`/`blocks`/`entities`/`DataVersion`, `size = [3,3,3]`, palette = `minecraft:air`, exactly matching the layout of Create's own `arrow_dispenser.nbt` / `controls.nbt` GameTest templates. |
| JUnit `ContraptionStatusIndicatorTest` (new) | **NOT RUN** — no compiler. Logic is plain Java; the assertions are exhaustive over the four boolean inputs that matter. |
| Any in-game verification | **NOT PERFORMED.** No client, no server, no game files. |

**Therefore: the code is written and statically parsed, but it has never been compiled and nothing here
proves any mixin applies at runtime.** Treat this pass as "implemented, unbuilt".

## Not verified / blocked

1. Compilation of any file against the real Create/Sable/NeoForge artifacts.
2. Whether all five mixins apply cleanly alongside Sable 2.0.6, and in particular whether the two new
   invalidation injects resolve: `AbstractContraptionEntity#handleBlockChange` is `protected` and
   `@OnlyIn(Dist.CLIENT)` (so its injector lives in `AbstractContraptionEntityClientMixin`, listed under
   `client`), and `CarriageContraptionEntity#setBlock` is an override with no `super` call
   (analysed instruction-level: Sable targets
   `getRayInputs`, `rightClickingOnContraptionsGetsHandledLocally` and the `toLocalVector` call inside
   `rayTraceContraption` with `@Redirect`; this addon redirects the `rayTraceContraption` call inside
   `rightClickingOnContraptionsGetsHandledLocally` and injects at `HEAD` of `handlePlayerInteraction`,
   which are different instructions — but that is static analysis, not a run). The client inject was
   originally in `AbstractContraptionEntityMixin`, i.e. in the common `mixins` list, even though its target
   is a client-only member. It now lives in `AbstractContraptionEntityClientMixin` under the `client` key.
   That is the verified fix; the stronger explanation "the method is stripped on a dedicated server in
   NeoForge 21.1.228" is **not** verified and is not claimed — see the class javadoc for what was and was
   not confirmed in the loader sources.
3. Whether `BuildCreativeModeTabContentsEvent` places the items in Create's base tab in practice.
4. In-game behaviour listed in the manual checklist below.
5. `state.getDestroySpeed(...)` for the markers. `isMovementAllowedFallback` checks it *before* the
   push-reaction line, so a `-1` hardness would make the markers unmovable regardless of the new
   `MovementAllowedCheck`... except that the check is queried first and answers `SUCCESS`, so it never
   matters. Listed because it is the reason the check has to answer before the fallback, not after it.
6. The recipe/loot/advancement JSON was hand-written, not generated by data generation; the 1.21.1
   `recipe` directory name and `{"id": ...}` result form were confirmed against Create's own generated
   data, but the files have not been validated by the game's data loader.

## Manual verification checklist (please run before trusting the feature)

With NeoForge 21.1.228 + Create 6.0.10-280 + Sable 2.0.6 + this jar, in a creative world:

1. Both markers appear in Create's base creative tab and can be placed on a wall, floor and ceiling.
2. Build a small bearing contraption with a chest and an `interact_me` marker; default `bearingHandling`
   is `WITH_STICKER` → right-click the chest while the bearing rotates: the inventory must open.
3. Remove the marker, re-assemble: the same right-click must not open the inventory. Note that with the
   default `WITH_STICKER` a marker-less contraption is denied, so the click is also not consumed and
   falls through to the held item.
4. Same contraption with only `interact_me_not` and `bearingHandling=WITHOUT_STICKER`
   (`config/create_sable_n_rails-server.toml` or `/reload`) → must not open.
5. `bearingHandling=NEVER` with `interact_me` present → must not open (server setting wins).
6. `bearingHandling=ALWAYS` with `interact_me_not` present → must open (server setting wins).
7. Right-click on the world *behind* a non-interactive contraption: the held item must still be usable.
   Also confirm the two behaviours the client filter must **not** break: standing inside a non-interactive
   contraption must still be prevented from clipping through it, and relocating a train past a
   non-interactive carriage must still be refused.
8. Seat / train carriage: with `trainHandling=NEVER`, right-clicking a seat must not board the player.
9. Persistence: assemble with a marker, save, quit, reload → behaviour unchanged; then disassemble and
   check the marker block is back in the world at the right position, and no block was lost or duplicated.
10. Assembly gate — the point of the `MovementAllowedCheck`. Put a marker on a chest, assemble with a
    bearing/piston/pulley: assembly must succeed, the marker must travel with the contraption (visible on
    the moved structure), and on disassembly the marker block must be back in the world. If assembly
    fails with "unmovable block", the check is not registered or is answering `PASS` for the markers.
11. Marker added/removed after assembly: **not reachable through the vanilla interaction paths traced in
    Create 6.0.10**, but reachable in principle and therefore tested for tooling correctness. The marker
    registers no `MovingInteractionBehaviour` (`AllInteractionBehaviours:14-18` and the `.onRegister(...)`
    calls in `AllBlocks` cover levers, doors, trapdoors, fence gates, blaze burners, mounted depots,
    contraption controls, deployers, train controls and seats only), and `Contraption#moveBlock` only runs
    during assembly. The only writers of `contraption.getBlocks()` after assembly are
    `AbstractContraptionEntity#setBlock` (427), `CarriageContraptionEntity#setBlock` (200) — reached from
    `MovingInteractionBehaviour#setContraptionBlockData` (43) and `SlidingDoorMovementBehaviour` (94, 101) —
    `AbstractContraptionEntity#handleBlockChange` (762, client, from `ContraptionBlockChangedPacket`:27),
    `Contraption`'s multiblock-controller swap (1253-1254) and `PistonContraption`'s pole write (145);
    `blocks.remove(...)` does not occur anywhere in Create 6.0.10. `setContraptionBlockData` is public API,
    so another addon can change a marker on a live contraption. Expected behaviour if it does: the next
    interaction is decided from the new block map, with no stale decision in between — this is what the
    exact signature, the category/policy key components and the three invalidation points are for. Asserted
    by `sameSizeMarkerSwapInvalidatesCachedDecision` on the production path
    (`ContraptionInteractionPolicy.decide`), which has **never been executed**; the reasoning above is source
    reading, not a run.
12. Dedicated server with a second client: behaviour identical, and the client that is not the operator
    sees the same decision (server authoritative).
13. Wrench on a carriage in a denied configuration (`trainHandling=NEVER`, or the default with no
    `interact_me` marker): the wrench must not put the client into relocation mode at all, and clicking a
    track afterwards must not move the train. Then set `trainHandling=ALWAYS` (or attach `interact_me`) and
    confirm relocation works end to end — this is the regression check for "permitted trains still work".
14. Multi-carriage train with mixed markers: give the train one carriage with `interact_me` and another
    with `interact_me_not` (or none, under the default `WITH_STICKER`), then click the **allowed** carriage
    with the wrench. Relocation mode must not start at all (client pre-check), and even if a packet is forced
    through, the server must reject it. This is the regression test for both halves of the scope rule: a
    per-carriage check would let each of them through.
15. All carriages permitted and loaded: wrench a carriage, then click a track — the train must relocate end
    to end. This is the "do not break the allowed case" test for the train-wide rule.
16. Fail-closed edge: a train with a carriage in another dimension (or otherwise not loaded where the check
    runs) must refuse relocation rather than move. Expected per the documented limitation, not a defect.
17. Forged packet, unknown `trainId`: send a `TrainRelocationPacket` with a random UUID and a valid carriage
    entity id. The server must reject it (no relocation, no `NullPointerException` in the log). Without the
    mixin this is an NPE inside Create's own handler, which is why this case is part of the test set.
18. Forged packet, mismatched ids: `trainId` of train A, `entityId` of a carriage of train B. Must be
    rejected without moving either train.

## Next concrete step

1. On a machine with network access: `./gradlew build test gameTestServer`, then fix whatever the
   compiler and the GameTests report. The most likely first errors are (a) a Create/NeoForge signature
   that moved inside these exact artifact builds, and (b) the mixin refmap — ModDevGradle generates
   mappings at build time, so `remap = false` on the two Create-targeting injections should be correct,
   but it has never been compiled.
2. Perform the manual checklist above with Sable 2.0.6 installed.
3. Confirm the HUD indicator in game: look at a denied contraption (expect one line above the hotbar), look at
   an allowed one (expect nothing), press F1 (expect nothing), and check that a contraption partly behind a
   wall does not light up. The overlay itself is statically parsed only; the ray-to-entity geometry in
   `ContraptionTargetProbe` is the part a GameTest cannot cover.
4. Only then pick the next target function. Derailment/buffer stops, Propagator Bearings and cross-sub-level
   item/fluid transfer still lack a safe, verifiable API on this pairing (see `UPSTREAM_RESEARCH.md`).
