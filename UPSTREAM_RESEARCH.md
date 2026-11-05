# Upstream compatibility and API research

Research snapshot of the present working tree (no calendar date recorded — it cannot be confirmed in this
environment). This is a bounded source review for Minecraft 1.21.1 / NeoForge; it is
not evidence that gameplay integration works on a live server. Every claim below names the repository,
the exact revision and the file that was read.

## How this research was performed in this session

- `raw.githubusercontent.com` and the `services.gradle.org` / `maven.neoforged.net` /
  `maven.createmod.net` / `maven.ryanhcode.dev` hosts are **not reachable** from the work environment.
  Whole repositories were therefore fetched as source archives through
  `https://codeload.github.com/<owner>/<repo>/tar.gz/<ref>` and read locally.
- No Minecraft jar, Create jar or Sable jar could be downloaded, and no Java compiler is installed in
  the environment (`java` is absent, and the only available runtime image lacks `jdk.compiler`).
  Consequently **no Gradle build, unit test run or GameTest run has been executed**. See the
  "Verification actually performed" section of `IMPLEMENTATION_STATUS.md` for what was done instead.

## Sources reviewed

| Repository | Revision read | Purpose |
|---|---|---|
| `Creators-of-Create/Create` | tag `mc1.21.1-6.0.10` (full tree) | Exact target build; all Create API/signature claims below |
| `Creators-of-Create/Create` | branch `mc1.21.1/dev` | Checked to be byte-identical for `AbstractContraptionEntity.java` and `ContraptionHandlerClient.java` |
| `ryanhcode/sable` | tag `mc1.21.1-2.0.6-neoforge` (full tree) | Exact target build; physics/collision/raytrace integration and configs |
| `ValkyrienSkies/Create-Interactive` | branch `1.20.x/main` | Functional reference only (Minecraft 1.20.1, All Rights Reserved) |
| `neoforged/NeoForge` | branch `1.21.1` | `DeferredRegister`, `BuildCreativeModeTabContentsEvent`, `ModConfigSpec`, GameTest annotations |
| `neoforged/FancyModLoader` | branch `1.21.1` | `ModContainer#registerConfig`, `ModConfigEvent`, mod-constructor injection |
| `neoforged/ModDevGradle` | `main` | Development-run/mixin conventions |

Nothing was copied from these repositories. The addon re-implements the behaviour independently; the
sources were used to establish API existence, exact signatures and the shape of the problem.

## Version compatibility

| Component | Pinned build | Evidence and result |
|---|---:|---|
| Minecraft | `1.21.1` | Sable 2.0.6 `gradle.properties` (`minecraft_version=1.21.1`); Create `mc1.21.1-6.0.10` tag exists. |
| NeoForge | `21.1.228` | Sable 2.0.6 `gradle.properties` pins `neoforge_version=21.1.228`. |
| Sable | `2.0.6` | `gradle.properties` `version=2.0.6`, `java_version=21`, `minecraft_version_range=[1.21.1]`. |
| Create | `6.0.10-280` | Sable 2.0.6 `gradle.properties` pins `create_version=6.0.10-280`; the Create tag `mc1.21.1-6.0.10` was read in full. |
| Ponder / Flywheel / Registrate | `1.0.82` / `1.0.6` / `MC1.21-1.3.0+67` | Same Sable `gradle.properties`; Create `mc1.21.1-6.0.10` `gradle.properties` agrees on `flywheel_version = 1.0.6`. |
| Sable Companion | `1.6.0` | `sable_companion_version=1.6.0` in Sable; transitively available, not bundled. |

Note: Create `mc1.21.1-6.0.10`'s own `gradle.properties` says `neo_version = 21.1.219`, while Sable
2.0.6 pins `21.1.228`. This addon uses `21.1.228` because that is the version Sable — the stricter of
the two runtime dependencies — was built and tested against. `neoforge.mods.toml` therefore declares
`[21.1.228,)`.

## Does Sable already provide the interactivity feature?

**No.** Sable 2.0.6 contains no interactivity/authorisation concept at all:

- `grep -ri "interactiv"` over the whole Sable 2.0.6 tag returns **no** matches.
- `grep -ri "InteractMe"` returns **no** matches.
- Sable's Create compatibility mixin list
  (`neoforge/src/main/resources/sable-neoforge.mixins.json`, keys `mixins`/`client`) includes
  `compatibility.create.contraptions.AbstractContraptionEntityMixin`,
  `...ContraptionColliderMixin`, `...ContraptionHandlerClientMixin`,
  `...ContraptionVisualMixin`, `...VisualizationEventHandlerMixin` and
  `...trains.CarriageContraptionVisualMixin`. None of them injects into
  `handlePlayerInteraction`.
- Sable's configuration (`SableConfig`, `SableServerConfig`, `SableClientConfig`) has no
  contraption-interaction options.

What Sable *does* own, and what this addon therefore must not re-implement:

| Capability | Where in Sable 2.0.6 | Consequence for this addon |
|---|---|---|
| Physics/plot integration for Create contraption entities | `neoforge/.../compatibility/create/contraptions/AbstractContraptionEntityMixin.java`, implementing `dev.ryanhcode.sable.api.sublevel.KinematicContraption`; adds the entity to its containing plot (`ServerSubLevel#getPlot#addContraption`) and the physics pipeline, and removes it in `setRemoved` | Do not create ship objects or a second sub-level representation |
| Collision adaptation | `neoforge/.../contraptions/ContraptionColliderMixin.java` | Do not touch collider logic |
| Projected raytrace for sub-level poses | `neoforge/.../contraptions/ContraptionHandlerClientMixin.java` (three `@Redirect`s: `Vec3#distanceTo` in `getRayInputs` and `rightClickingOnContraptionsGetsHandledLocally`, `getBoundingBox`, and `toLocalVector` in `rayTraceContraption`) | Raytracing already works across sub-levels; only *authorisation* is missing |
| Train carriage visuals | `neoforge/.../trains/CarriageContraptionVisualMixin.java` | Do not touch rendering |

## Does Create 6.0.10 already gate interaction?

**No.** Read at `Create` tag `mc1.21.1-6.0.10`:

- `src/main/java/com/simibubi/create/content/contraptions/AbstractContraptionEntity.java:287-327`
  — `handlePlayerInteraction(Player, BlockPos, Direction, InteractionHand)` checks only whether the
  position is a seat and dispatches to `contraption.interactors` / `contraption.storage`. There is no
  per-contraption or per-player permission check anywhere in the method.
- `src/main/java/com/simibubi/create/content/contraptions/sync/ContraptionInteractionPacket.java:44-56`
  — `handle(ServerPlayer)` validates only distance (`BLOCK_INTERACTION_RANGE + 10 + bounding box`) and
  line of sight, then calls `handlePlayerInteraction` directly.
- `src/main/java/com/simibubi/create/content/contraptions/ContraptionHandlerClient.java:124` — the
  client calls the same method locally and only sends the packet when it returns `true`.
- `src/main/java/com/simibubi/create/api/behaviour/interaction/MovingInteractionBehaviour.java` — the
  public extension point is consulted *inside* `handlePlayerInteraction`, i.e. after the decision, and
  only for blocks registered with a behaviour. It cannot veto
  `MountedStorageManager#handlePlayerStorageInteraction`.
- No override of `handlePlayerInteraction` exists in any Create contraption entity subclass (verified
  by grep over the whole `src/main/java`), and no NeoForge event wraps it.

So the behaviour Create: Interactive implements as "is this contraption interactive at all" is absent
from the 1.21.1 pairing, both in Create and in Sable. That is the gap this addon fills.

## Reference behaviour (Create: Interactive, functional reference only)

Read at `ValkyrienSkies/Create-Interactive` branch `1.20.x/main` (Minecraft 1.20.1; `LICENSE` is
All Rights Reserved, so no code, text or assets were reused):

- `common/src/main/java/org/valkyrienskies/create_interactive/config/InteractiveHandling.java` —
  enum `ALWAYS, WITHOUT_STICKER, WITH_STICKER, NEVER`.
- `.../config/CServer.java` — six server-config values, one per contraption category:
  `trainHandling`, `translatingHandling`, `bearingHandling`, `clockworkHandling`, `mountedHandling`,
  `otherHandling`; defaults `WITH_STICKER` ×5 and `NEVER` for `other`.
- `.../CreateInteractiveUtil.kt` — `hasInteractMeSticker` / `hasInteractMeNotSticker` scan the
  contraption block map; the decision is
  `ALWAYS -> true`, `WITHOUT_STICKER -> !hasInteractMeNot`, `WITH_STICKER -> hasInteractMe`,
  `NEVER -> false`; category selection by `instanceof` on `CarriageContraption`,
  `BearingContraption`, `ClockworkContraption`, `TranslatingContraption`, `MountedContraption`.
- `.../content/interact_me/InteractMeBlock.kt` — an instant-break
  `FaceAttachedHorizontalDirectionalBlock`; the "Not" variant is the same shape with opposite meaning.

This addon reproduces the observable semantics (the four policies, six categories with the same
defaults, and the same marker idea) with an independent implementation, plus two deliberate
differences documented in `IMPLEMENTATION_STATUS.md`: markers are waterloggable like Create's own
face-attached decoration blocks, and recognition of the blocked interaction happens client-side as
well so a denied right click does not swallow the click.

## API/integration findings for the implemented feature

| Function | API or integration point | Target constraints | Verification still needed |
|---|---|---|---|
| Veto a player interaction with a moved contraption | Mixin `@Inject(method = "handlePlayerInteraction", at = @At("HEAD"), cancellable = true, remap = false)` on `AbstractContraptionEntity`; returning `false` is Create's own "not handled" contract (`AbstractContraptionEntity.java:287`) | The method signature is stable across `mc1.21.1-6.0.10` and `mc1.21.1/dev`, but it is a Mojang-mapped name inside a Create class, so it must be re-checked on every Create update (and the GameTests/GameTestServer run is the cheap way to notice a rename). `remap = false` is set explicitly: the target class is a Create class, not a Minecraft class, so its member names are not in the mapping data. Sable 2.0.6 uses the same convention (`AbstractContraptionEntityMixin.java:97` passes `remap = false` when injecting into Create's `tick`). | Compile + run; see manual checklist |
| Cache invalidation on contraption block change | Three write paths, all mixed in at `HEAD`: `AbstractContraptionEntity#setBlock` (`AbstractContraptionEntity.java:427-428`), `CarriageContraptionEntity#setBlock` (`CarriageContraptionEntity.java:200-209`, an override that does **not** call `super` and writes `cce.contraption.getBlocks()` for every present entity via `Carriage#forEachPresentEntity`, `Carriage.java:453-459`), and the client-side `AbstractContraptionEntity#handleBlockChange` (`:762-766`, from `handleBlockChangedPacket:746-748`). Cache entries additionally carry an exact `MarkerSignature` (normalized `BlockPos` → marker kind, structural equality), the contraption category and the effective policy — not a hash and not a map size | `CarriageContraptionEntity#setBlock` silently drops `super`'s behaviour, so an inject on the base method cannot see carriage mutations — a separate mixin is mandatory. The multiblock-controller swap (`Contraption.java:1253-1254`) and `PistonContraption`'s pole write (`:145`) touch NBT/assembly state only and cannot change which markers exist; `blocks.remove(...)` does not occur in 6.0.10 | Compile + run |
| Client-side raytrace filter | `@Redirect` on the single `rayTraceContraption` **call site** inside `rightClickingOnContraptionsGetsHandledLocally` (`ContraptionHandlerClient.java:104`), returning `null` for a denied contraption so the loop `continue`s. Forced by the fact that `rayTraceContraption` is shared: `ContraptionCollider.java:412` (anti-clipping), `TrainRelocator.java:340,350` (clearance) and `ElevatorControlsHandler.java:86` also call it, and filtering any of those would be a safety regression — so a `RETURN` inject in the callee is not an option | Sable also mixins this class, but with `@Redirect`s on `Vec3#distanceTo`, `AbstractContraptionEntity#getBoundingBox()` and, inside `rayTraceContraption`, `toLocalVector`; different instructions, so no overlap | Compile + run, and verify against Sable installed |
| Contraption classification | `instanceof` against `CarriageContraption`, `ClockworkContraption`, `BearingContraption`, `StabilizedContraption`, `MountedContraption`, `TranslatingContraption` | `StabilizedContraption` is not a `BearingContraption` in 6.0.10, so it is checked explicitly and grouped with bearings. `ElevatorContraption extends PulleyContraption extends TranslatingContraption`, so elevators classify as TRANSLATING. | GameTest `contraptionClassification` |
| Reading markers | `Contraption#getBlocks()` returns the live `Map<BlockPos, StructureBlockInfo>` (`Contraption.java:1411`), populated during assembly and deserialised from NBT in `Contraption#fromNBT` | None; this is the same map Create itself scans in `Contraption#addBlocksToWorld` | GameTest `policyReadsMarkersFromContraptionBlocks` |
| Server config | `ModContainer#registerConfig(ModConfig.Type.SERVER, ModConfigSpec)` (`FancyModLoader` `ModContainer.java:102`), `ModConfigSpec.Builder#defineEnum` (`NeoForge` `ModConfigSpec.java:633`); `SERVER`-type configs live in `serverconfig/` and are synced to clients by NeoForge | The spec `isLoaded()` must be checked before reading, otherwise `ConfigValue#get()` throws (`ModConfigSpec.java:1225`) | Compile + run |
| Registration | `DeferredRegister.createBlocks/createItems` (`DeferredRegister.java:142/155`), `DeferredRegister#register(IEventBus)` (`:315`), `Items#register(name, Supplier)` (`:29…`) | — | GameTest `markersAreRegistered` |
| Letting a marker ride a contraption | `BlockMovementChecks.registerMovementAllowedCheck(MovementAllowedCheck)` (`api/contraption/BlockMovementChecks.java:32`), evaluated in `BlockMovementChecksImpl#isMovementAllowed` (`:122`) before the fallback. Returning `CheckResult.SUCCESS` for the two markers and `PASS` otherwise overrides only `isMovementAllowedFallback`'s final line `return state.getPistonPushReaction() != PushReaction.BLOCK;` (`:216`) | The check is queried before the fallback and registration is `add(0, check)` (`BlockMovementChecksImpl.java:94`), i.e. newest first; returning `PASS` for everything else leaves Create's and other addons' answers untouched. `DeferredBlock#get()` must not be called at registration time, only inside the check body | Compile + the assembly step of the manual checklist |
| Blocking wrench relocation | `@Inject(method = "handle", at = @At("HEAD"), cancellable = true, remap = false)` on `TrainRelocationPacket`; decision from `ContraptionInteractionPolicy.isInteractive(CarriageContraptionEntity)` | `TrainRelocationPacket#handle(ServerPlayer)` is `void` and is the sole caller of `TrainRelocator#relocate(...)` (`TrainRelocationPacket.java:70`); the packet names the carriage by entity id, so the contraption — and therefore the policy decision — is available on the server. `handleSpecialInteractions` is `private static` in `ContraptionHandlerClient`, so the client-side start can only be cancelled by targeting its descriptor | Compile + the relocation steps of the manual checklist |
| Creative tab injection | `BuildCreativeModeTabContentsEvent` (`NeoForge` `event/BuildCreativeModeTabContentsEvent.java`), tab key from `AllCreativeModeTabs.BASE_CREATIVE_TAB.getKey()` (`Create` `AllCreativeModeTabs.java:64`) | — | Manual (client only) |

## Contraption assembly: what decides whether a block is carried (Create 6.0.10)

This is the part the first implementation got wrong, recorded here because it is easy to get wrong again.
Read at `mc1.21.1-6.0.10`, `Contraption#moveBlock` lines 310-327, in this order:

1. `if (!BlockMovementChecks.isMovementNecessary(state, world, pos)) return true;` — the block is skipped
   **silently**; assembly still succeeds, the block just stays in the world.
2. `if (!movementAllowed(state, world, pos)) throw AssemblyException.unmovableBlock(pos, state);` — the
   whole assembly fails for the player.

`isMovementNecessaryFallback` (`impl/contraption/BlockMovementChecksImpl.java:164`) checks, in order:
brittle → `true`; `MOVABLE_EMPTY_COLLIDER` tag → `true`; empty collision shape → `false`; otherwise
`!canBeReplaced()`. `isBrittleFallback` (`:219`) returns `true` for **every**
`FaceAttachedHorizontalDirectionalBlock` except `GrindstoneBlock` and `PackagerLinkBlock`. A face-attached
marker is therefore "necessary" *because it is brittle*, before the empty collision shape is ever
consulted — the missing collision shape does not decide anything here.

`isMovementAllowedFallback` (`:176`) checks destroy speed, the `RELOCATION_NOT_SUPPORTED` block tag, the
`NON_MOVABLE` tag, `ContraptionMovementSetting.UNMOVABLE`, then special cases, and ends at `:216` with
`return state.getPistonPushReaction() != PushReaction.BLOCK;`. So a marker with `PushReaction.BLOCK`
(correct for piston behaviour) is reported **unmovable** and assembly throws.

Conclusion: `isMovementNecessary` needs no rule for the markers (`brittle` already answers correctly);
`isMovementAllowed` does, and it is fixed with a registered `MovementAllowedCheck` rather than by changing
the push reaction, because the push reaction is the piston contract and nothing else.

## Train relocation: the only action that completes outside `handlePlayerInteraction`

Traced in `mc1.21.1-6.0.10`, because it is the one place where gating the interaction is not sufficient:

1. `ContraptionHandlerClient.java:124-127` — `false` from `handlePlayerInteraction` **selects a branch**,
   it does not stop anything: `handleSpecialInteractions(...)` is called next.
2. `ContraptionHandlerClient.java:133-139` — `handleSpecialInteractions` is
   `private static boolean handleSpecialInteractions(AbstractContraptionEntity, Player, BlockPos, Direction,
   InteractionHand)` and its only non-trivial branch is a wrench on a `CarriageContraptionEntity`:
   `TrainRelocator#carriageWrenched`. That method (`TrainRelocator.java:357-365`) sets `relocatingOrigin`,
   `relocatingTrain`, `relocatingEntityId` and returns `true`. No packet is sent.
3. `TrainRelocator#onClicked` (`:75-108`, subscribed from `InputEvents.java:108`) → `relocateClient`
   (`:109-162`) → on a successful simulated relocation sends
   `TrainRelocationPacket(relocatingTrain, blockPos, lookAngle, relocatingEntityId, direction, hoveredBezier)`
   (`:157`).
4. `TrainRelocationPacket#handle(ServerPlayer)` (`TrainRelocationPacket.java:44-82`) checks only distance to
   the clicked track position and to the carriage entity, then calls
   `TrainRelocator.relocate(train, ..., simulate = false)` (`:70`) — the only call site of that method in
   the mod — and mutates the train.

5. `TrainRelocator#relocate` (`:164-268`) touches the train, not one carriage: at `:263-264` it loops
   `for (Carriage carriage : train.carriages) carriage.updateContraptionAnchors();`, and the mutations before
   that (`leaveStation`, `graph`, `speed`, `derailed`, every `TravellingPoint`) are train-wide.
6. A train's carriages are independent contraptions: `Train.carriages` is a `public List<Carriage>` (`:127`),
   `Carriage.train` is public (`:68`), each `Carriage` has its own `CarriageContraption` (its own
   `blocks` map, hence its own markers) and its own `CarriageContraptionEntity` per dimension
   (`Carriage.DimensionalCarriageEntity#entity`, `:543-546`, read through `getDimensionalIfPresent`, `:144`).

7. The client can perform the same train-wide check, because Create populates the carriage link there too:
   `CarriageContraptionEntity#bindCarriage` (`:319-332`) resolves the train from
   `Create.RAILWAYS.sided(level()).trains.get(trainId)` and registers itself through
   `carriage.getDimensional(level())`, while `Carriage#train` is set by `Train#setTrain` from the train
   constructor (`Train.java:179`) using the client's mirrored railway data. So
   `CarriageContraptionEntity#getCarriage().train.carriages` is available client-side when
   `handleSpecialInteractions` runs.

8. `TrainRelocationPacket#handle` is not hardened against forged ids. Line 45 resolves the train from
   `Create.RAILWAYS.trains`, and the null branch at lines 51-55 builds its log message from
   `train.id.toString()` — dereferencing the very reference it just found to be `null`. An unknown `trainId`
   therefore throws a `NullPointerException` on the server thread. The `train.id.equals(cce.trainId)` guard
   from the *other* half of line 51 is only reached when the train exists (line 57), so a mismatched pair
   returns silently, but the same line can already have thrown.

Consequences for the addon: the interaction gate is necessary but not sufficient, and the authoritative
point is the packet handler. The client-side start is also cancelled so that a denied contraption never
enters relocation mode at all. Because the move applies to `train.carriages` while both the click and the
packet name a single carriage, the policy is evaluated over **all** carriages of the train on both sides,
through one shared method — otherwise naming an allowed carriage would relocate a denied one. The decision
per carriage is the ordinary policy decision (category `TRAIN` → `trainHandling`, plus that contraption's
markers); the combination is a conjunction, and a carriage with no contraption entity in the checking level
counts as denied.

## HUD overlay API on this pairing (checked for the status indicator)

| Question | Evidence | Verdict |
|---|---|---|
| Can an addon register a HUD layer on NeoForge 1.21.1? | `net.neoforged.neoforge.client.event.RegisterGuiLayersEvent` on branch `1.21.1`; class javadoc: "Allows users to register custom layers for GUI rendering … fired on the mod-specific event bus, only on the logical client". Methods verified by reading the file: `registerBelowAll`, `registerBelow`, `registerAbove`, `registerAboveAll`, `replace`. | Yes, `registerAbove(VanillaGuiLayers.HOTBAR, id, layer)` |
| Is the event a mod-bus event, and can it be restricted to the physical client? | The same file declares `extends Event implements IModBusEvent`. FancyModLoader's `EventBusSubscriber` javadoc states that `IModBusEvent` subscribers "will be registered to the mod's event bus" and that `value()` "Specify targets to load this event subscriber on". Default is both sides. | Yes: `@EventBusSubscriber(modid = …, value = Dist.CLIENT)` |
| Do the layer entry points exist in 1.21.1? | `net.minecraft.client.gui.LayeredDraw.Layer` is the interface NeoForge's event expects, and `VanillaGuiLayers.HOTBAR` is `ResourceLocation.withDefaultNamespace("hotbar")`. Both are used by Create itself: `ClientEvents#registerGuiOverlays(RegisterGuiLayersEvent)` at `foundation/events/ClientEvents.java:373` registers `TrainHUD.OVERLAY`, `TrackPlacementOverlay.INSTANCE` and others the same way, and `TrackPlacementOverlay` calls `guiGraphics.drawString(mc.font, text, x, y, color.getRGB(), false)`. | Yes; the exact overload used here is the one Create uses |
| Does the exact NeoForge build 21.1.228 contain it? | **Not verified.** The NeoForge repository publishes no tag or release named `21.1.228` (tag and release APIs return no match for `21.1.2x`), so the check was done against branch `1.21.1`. The `RegisterGuiLayersEvent` file exists there with two commits, the later one dated 2025-07-10 ("Backport to 1.21.1: Add method to wrap gui layers"), i.e. before the 1.21.1 line moved on from 21.1.228. | Present on the branch; version-exact confirmation requires a build |
| Does Sable 2.0.6 register GUI layers, or provide interactivity feedback? | No match for `RegisterGuiLayersEvent`/`LayeredDraw` anywhere in the Sable `mc1.21.1-2.0.6-neoforge` tree, and no match for `interactiv` in any Java file. | No overlap, nothing duplicated |
| Is Create's own contraption raytrace reusable instead of duplicating it? | `ContraptionHandlerClient` is a public class without `final`; `getRayInputs(LocalPlayer)` (line 142) and `rayTraceContraption(Vec3, Vec3, AbstractContraptionEntity)` (line 154) are `public static`, documented `@Nullable`/`@OnlyIn(Dist.CLIENT)` on the class. `AbstractContraptionEntity#toLocalVector(Vec3, float)` and `toGlobalVector(Vec3, float)` are `public` (lines 339/353). The class is client-only, which is exactly where the HUD renders. | Reused; the addon contributes only the broad-phase filter and nearest-hit selection |
| Does Create already show such an indicator? | Create's registered layers are air, train HUD, value settings, track placement, goggle info, blueprint, linked controller, schematic and toolbox. None reports interactivity. Its interaction path (`ContraptionHandlerClient#rightClickingOnContraptionsGetsHandledLocally`) cancels the click without any feedback. | Absent upstream |

## Deliberately not implemented, with the reason

| Feature | Why not |
|---|---|
| Derailment / Train Buffer Stop | Requires changing `Carriage`/`Train` motion and `TrackGraph` behaviour. `CarriageContraptionEntity` is a `Sable`-managed kinematic body; abruptly removing it from a running train risks duplicating or losing carriage blocks and is not verifiable in this environment. No 1.21.1 Create API is established for a safe buffer stop. |
| Propagator Bearings / disjointed variant | A new kinetic block needs `KineticBlockEntity`, rotation propagation and a contraption that transfers rotation into another contraption's kinetics. Nothing in Create 6.0.10 or Sable 2.0.6 provides a documented cross-contraption kinetic-transfer API, and the reference implementation is a 1.20.1 All-Rights-Reserved codebase built on VS2 ship objects. Implementing it would mean inventing an API. |
| Nested contraptions | Create already supports stabilised sub-contraptions (`Contraption#stabilizedSubContraptions`, `AbstractContraptionEntity#disassemble` handling of `OrientedContraptionEntity` passengers). No missing behaviour was identified, so nothing was added. |
| Portable Storage / Fluid Interfaces | Sable ships `compatibility/create/fluid_handling/*`, `frogports/*` and `inventory_manipulation/*` mixins, and Create's `MountedStorageManager` already moves inventories within a contraption. Whether a *cross-sub-level* transfer works is a runtime question that cannot be answered without the game; claiming it would be unfounded. |

## Next research steps

1. Build on a machine with network access, then run `./gradlew gameTestServer` to execute the GameTests.
   There are three GameTest classes in the sources — `gametest/ContraptionInteractivityGameTests` (6 tests),
   `interaction/ContraptionInteractionPolicyGameTests` (5) and `client/ContraptionTargetProbeGameTests` (3),
   14 `@GameTest` methods in total — but **none of them has ever been executed**, so their existence says
   nothing about passing.
2. Play-test the manual checklist in `IMPLEMENTATION_STATUS.md` with Sable 2.0.6 installed, focusing on
   a bearing contraption and a train carriage while the contraption is in motion, on a non-interactive
   contraption obscuring a normal block, on clipping into a non-interactive contraption (must still be
   prevented), and on train relocation past a non-interactive carriage (must still be blocked).
3. The client-side status indicator is no longer an open question: the code exists (`client/ClientSetup`,
   `client/ContraptionStatusOverlay`, `client/ContraptionStatusIndicator`, `client/ContraptionTargetProbe`) and
   is registered through `RegisterGuiLayersEvent`. **Existing code is not the same as verified behaviour** — it
   has never been compiled or seen in game, so what remains is verification, not a decision.
