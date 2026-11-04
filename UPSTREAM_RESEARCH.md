# Upstream compatibility and API research

Research snapshot: 2026-11-04. This is a bounded source review for Minecraft 1.21.1 / NeoForge; it is not evidence that gameplay integration works. Sable checks below were repeated against the exact release tag `mc1.21.1-2.0.6-neoforge`, commit `ad8b4d377741fbd923f4d863b2cf9e462daff9a6`, rather than relying only on `main`.

## Version compatibility

| Component | Pinned build | Evidence and result |
|---|---:|---|
| Minecraft | `1.21.1` | Sable 1.21.1 Maven metadata contains a current stable artifact; Create publishes the `create-1.21.1` artifact. |
| NeoForge | `21.1.228` | Sable `gradle.properties` at `ryanhcode/sable` main commit `227077f7f528c9beab1336a9fad2e27b754e4e70` pins `neoforge_version=21.1.228`; the official NeoForge Maven POM for that exact version exists. |
| Sable | `2.0.6` | Same Sable revision sets `minecraft_version=1.21.1` and `sable_companion_version=1.6.0`; `sable-common-1.21.1` and `sable-neoforge-1.21.1` Maven metadata both list `2.0.6` as latest/release (updated 2026-10-03). Exact common POM exists. |
| Create | `6.0.10-280` | Sable's same properties pin this exact Create build. The official Create Maven POM for `create-1.21.1:6.0.10-280` exists. The POM identifies Registrate `MC1.21-1.3.0+67` and Ponder `[1.0.82+mc1.21.1,)`; Sable pins Registrate and Ponder accordingly. |
| Sable Companion | `1.6.0` transitively | Sable `2.0.6` common POM declares the exact `sable-companion-common-1.21.1:1.6.0` compile dependency. Its README describes it as the stable/default API boundary with implementations overridden when Sable is present. The addon's first pass does not call this API, but Sable's compile dependency supplies it. |
| Flywheel | `1.0.6` | Pinned by Sable for this target; the matching NeoForge artifact is a Create runtime component. It is included in the development dependency graph, not bundled into this addon's output. |

The build pins versions rather than dynamic `+` selectors. Compatible Maven artifacts exist at the reviewed sources; this proves artifact/version availability, not that the current addon builds or passes an in-game compatibility test.

## Capability boundary: Create vs Sable vs Create: Interactive

| Capability | Create 6.0.10-280 provides | Sable 2.0.6 adds to that pairing | Create: Interactive feature established as absent here |
|---|---|---|---|
| Contraption lifecycle | Owns standard Create contraption assembly, movement/entity lifecycle, and disassembly; this remains Create's lifecycle, not an addon-owned second copy. | Adds physics/plot integration to Create's `AbstractContraptionEntity`, not an independent conversion/duplication of contraption blocks into a new Sable sub-level. | No independent lifecycle replacement is established from the reviewed 1.20.1-only reference. |
| Moving structure physics and collision | Create's ordinary contraption behavior. | Sable's exact-release `AbstractContraptionEntityMixin` integrates the existing entity with its kinematic physics pipeline and containing plot; `ContraptionColliderMixin` adapts collision handling. | None identified; this behavior already exists upstream in Sable. |
| Ray tracing / projected interaction | Create's ordinary interaction path. | `ContraptionHandlerClientMixin` projects Create ray-hit positions/distances to account for Sable sub-level poses. | A separately missing interaction capability has not been demonstrated. The raytrace mixin alone does not prove every placement/use/break case works. |
| Train carriage visuals | Create's train/contraption visual behavior. | `CarriageContraptionVisualMixin` adjusts transforms for Sable. | None identified from the available reference. |
| Per-contraption interaction toggle/markers, nested-bearing transfer, derailment/buffer stop, portable/fluid interface transfer | Exact 6.0.10-280 behavior has not yet been individually source-traced in this pass. | No matching addon-facing implementation/API was confirmed in the reviewed Sable mixins. | Not established: the only reference reviewed is the `1.20.x/main` branch, targeting Minecraft 1.20.1. Treating these as missing or copying them into the 1.21.1 pairing would be speculation. |

Conclusion: Sable 2.0.6 already owns the confirmed physics, collision and projected-raytrace compatibility layer. There is no confirmed missing feature plus verified extension point among the addon-specific candidates above, so implementing one now would risk duplicating Sable or inventing a lifecycle/API. No gameplay change is made until exact Create lifecycle and an absent feature are independently confirmed.

## API/integration findings

| Function | API or integration point | Target constraints | Verification still needed |
|---|---|---|---|
| Locate and transform positions in sub-levels | Sable Companion `SableCompanion.INSTANCE.getContaining(...)`, `projectOutOfSubLevel(...)`, `distanceSquaredWithSubLevels(...)`; methods documented in `sable-companion` README at main commit `652ecf0e051846f3a433dd20126dd4d64ec3793a`. Sable `SubLevel` implements `SubLevelAccess`. | Must use plot-local vs global coordinates correctly; sub-level transforms are pose-dependent. Direct coordinates/distances can be invalid across plots. | Compile against exact artifacts; test movement, collision, and interactions while mounted/in a sub-level. |
| Existing Create contraption simulation | Sable NeoForge's `AbstractContraptionEntityMixin` (SHA `36da64b6af8f11da02eee6857f3a75b946d96531`) implements its `KinematicContraption`; derives mass/bounds from Create, inserts the entity into its containing plot and Sable physics pipeline, and handles projection/removal. `ContraptionColliderMixin` (SHA `4b77a68c320caa4932dbdd997a4c696f55f5942b`) adjusts collisions. | Sable's own Create mixins perform physics integration; they are version-sensitive internals, not an external registration API. Create: Interactive's reviewed `1.20.x/main` is only a functional reference (Minecraft 1.20.1; All Rights Reserved license); do not port its source/design assumptions. | Inspect the exact Create/Sable 1.21.1 source entry points and lifecycle, then design an adapter. No integration claimed. |
| Plot interaction/render projection | Sable `ContraptionHandlerClientMixin` (SHA `0604b5471165edb177d82f786b42733ac5db7056`) projects Create's raytrace positions/distances for sub-level poses; `CarriageContraptionVisualMixin` (SHA `98c8ab8f3595c08d5f52f126c24dc07f9a2448e9`) fixes train visual transforms. | Sable's version-specific mixins are implementation internals, not addon APIs. | Test actual block interaction and train visuals on exact pinned versions. |
| Persistence and block/entity transfer | The Sable mixin routes existing Create contraption entities into physics/plots; it does not prove a supported addon hook for converting a contraption into an independently owned sub-level or persisting arbitrary addon state. | Copying a contraption into a second storage container risks duplicated/lost blocks, BlockEntities, inventories, or kinetics. | Do not create a duplicate conversion layer. Trace exact save/load/disassembly and test before attaching addon-specific state. |
| Train derailment, bearings, interfaces | No 1.21.1 API or safe behavior confirmed in this pass. | Cannot assume Create: Interactive 1.20.1 behavior applies to Create 6.x or Sable 2.x. | Inspect exact version sources and prove behavior with server/client tests before classifying any as implemented. |

## Sources reviewed

- Sable repository release tag `mc1.21.1-2.0.6-neoforge`, commit `ad8b4d377741fbd923f4d863b2cf9e462daff9a6`: exact `gradle.properties` confirms Java 21, MC 1.21.1, NeoForge 21.1.228, Create 6.0.10-280, Ponder 1.0.82, Flywheel 1.0.6, Registrate `MC1.21-1.3.0+67`, Companion 1.6.0. Exact release source files `neoforge/src/main/java/dev/ryanhcode/sable/neoforge/mixin/compatibility/create/contraptions/AbstractContraptionEntityMixin.java` (blob `36da64b6af8f11da02eee6857f3a75b946d96531`) and `ContraptionColliderMixin.java` (blob `4b77a68c320caa4932dbdd997a4c696f55f5942b`) were fetched at that tag. Tag-specific source confirms the main branch references were not merely assumed. `SubLevel` and API guidance at main are secondary context only; no API conclusion below depends on main being identical to the release.
- Sable Companion repository main, commit `652ecf0e051846f3a433dd20126dd4d64ec3793a`: README and `common/src/main/java/dev/ryanhcode/sable/companion/SableCompanion.java` / default implementation. Docs describe no-op default compatibility utilities replaced by Sable behavior.
- Create repository `mc1.21.1/dev`: `gradle.properties`; Maven POM for `6.0.10-280`. The exact version is also the version Sable itself pins, with matching dependency versions.
- Create: Interactive branch `1.20.x/main`: README and `gradle.properties`; its metadata targets 1.20.1 and `LICENSE` is All Rights Reserved. It informs the problem domain only; its content is not reused.
- Official Maven POM and metadata queried for NeoForge `21.1.228`, Create `6.0.10-280`, Sable `2.0.6`, and their versioned artifacts.

## Next research steps

Trace Create 6.0.10-280 exact source for contraption construction, movement, disassembly, block entity/kinetic persistence, train handling, and Portable/Fluid Interface behavior. Then compare concrete gaps to the exact Sable 2.0.6 release source and identify an external public callback/API (or document why a minimal mixin is unavoidable). Keep compatibility opt-in and server authoritative. Do not add addon feature code until the missing behavior and safe hook are evidenced. Build and runtime validation are currently blocked by Gradle distribution network failures.
