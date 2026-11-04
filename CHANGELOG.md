# Changelog

## 0.2.0 - Unreleased

Implemented the first gameplay feature: a server-authoritative contraption interactivity layer.

### Fixed (audit pass)

- **Contraptions carrying a marker could not assemble at all.** `PushReaction.BLOCK` makes
  `BlockMovementChecksImpl#isMovementAllowedFallback` report the marker as unmovable, and
  `Contraption#moveBlock` turns that into `AssemblyException.unmovableBlock(...)`. Fixed by registering a
  `MovementAllowedCheck` that answers `SUCCESS` only for the two marker blocks and `PASS` for everything
  else. `PushReaction.BLOCK` is unchanged, so piston behaviour is unchanged.
- **Removed an invalid `@Override` in the GameTests.** `ClockworkContraptionHack` overrode `codec()`, which
  does not exist on `Contraption` or `ClockworkContraption` in Create 6.0.10 (no `MapCodec` anywhere in
  those classes). The class only needs to be constructible for the `instanceof` test.
- **Corrected the documented role of `PushReaction.BLOCK`** (it is a piston rule, not a contraption rule)
  and the documented scope of a denied interaction. Cancelling `handlePlayerInteraction` blocks storage,
  seats, wrench handling and actor behaviours, but on the client `false` routes into
  `handleSpecialInteractions`, and train relocation completes through a separate packet — so the previous
  claim that a denied carriage simply could not be relocated was wrong.
- **Closed the train-relocation hole, twice over.** A denied carriage can no longer start relocation
  (`handleSpecialInteractions` is cancelled for a denied contraption on the client) and can no longer
  complete it (`TrainRelocationPacket#handle` is rejected at `HEAD` on the server, the authoritative point
  and the only caller of `TrainRelocator#relocate`). Both the client pre-check and the server check evaluate
  **every carriage of the moved train**, not just the carriage that was clicked or named by `entityId`,
  because `relocate` moves the train and carriages carry independent contraptions. The rule is one shared
  method (`ContraptionInteractionPolicy#isTrainInteractive`), so the client cannot enter relocation mode for
  a train the server would refuse. Permitted trains are untouched, with one documented caveat: a carriage
  with no contraption entity loaded in the checking level counts as denied (fail-closed), so a
  config-permitted train still has to be fully loaded to be relocatable.
- **Malformed relocation packets are rejected before Create's code runs.** `TrainRelocationPacket#handle`
  dereferences `train.id` inside its `train == null` branch (`TrainRelocationPacket.java:51-53`), so an
  unknown `trainId` reaching it raises a `NullPointerException` on the server thread. The packet mixin now
  cancels at `HEAD` for an unresolvable `trainId` and for a `trainId`/`entityId` pair naming different
  trains, in addition to the policy check. Nothing in Create is patched; the packet is simply stopped before
  its body executes.
- **Cache invalidation covers all three Create write paths; the cache key is now exact.** The
  previous design cached a decision per contraption entity and reused it while `blocks.size()` matched. That
  is unsound in two ways, both verified in Create 6.0.10 source: `CarriageContraptionEntity#setBlock`
  (line 200) overrides `AbstractContraptionEntity#setBlock` (line 427) without calling `super`, so a carriage
  mutation never reached the invalidation inject; and `AbstractContraptionEntity#handleBlockChange`
  (line 762, client) rewrites the map entry directly. Either way, replacing `interact_me` with
  `interact_me_not` at the same position — or changing a marker's `face`/`facing` — left the map size
  unchanged and the old decision in place. Now: a new `CarriageContraptionEntityMixin` invalidates on the
  carriage override (clearing the cache, because one call updates every present dimensional entity),
  a new `AbstractContraptionEntityClientMixin` (listed under `client`) invalidates on `handleBlockChange`,
  and a cached decision is reused only when the exact marker signature, the contraption category and the
  configuration policy that produced it all still match. The signature is a normalized map of
  (immutable `BlockPos` → marker kind) with structural equality, not a hash and not the map size, so an
  equal key means an equal input set rather than a probable one; reusing a decision because
  `blocks.size()` matched is not possible. Policy rules and their scope are unchanged.

### Added

- **Locked-contraption HUD indicator (client-only).** A single line of text above the hotbar appears while the
  crosshair is on a contraption the policy denies, so a silently ignored click is now explained instead of
  looking like a missed input. It is drawn by a `LayeredDraw.Layer` registered through NeoForge's
  `RegisterGuiLayersEvent` above `VanillaGuiLayers.HOTBAR`, is suppressed by F1 and spectator mode, and never
  appears for an allowed contraption. The decision comes from `ContraptionInteractionPolicy.isInteractive(...)`
  — the same call the interaction gate makes — so the hint cannot contradict the authoritative server check,
  and it grants nothing: the server still rejects the interaction on its own. Target resolution uses Create's
  `ContraptionHandler.loadedContraptions` registry plus a bounding-box raytrace (`ContraptionTargetProbe`)
  instead of `minecraft.hitResult`, because a contraption is not part of the client level and its blocks are
  not what vanilla picks.

- `create_sable_n_rails:interact_me` and `create_sable_n_rails:interact_me_not` marker blocks:
  instant-break, non-colliding, waterloggable face-attached plates, with original 16x16 textures,
  blockstates, block and item models, English and Russian localisation, crafting recipes (andesite alloy
  + cogwheel), loot tables and recipe-unlock advancements.
- Six server configuration values, one per contraption category (train, translating, bearing, clockwork,
  mounted, other), each with `ALWAYS` / `WITHOUT_STICKER` / `WITH_STICKER` / `NEVER`.
  Defaults: `WITH_STICKER` for the five known categories, `NEVER` for unrecognised contraptions.
- Server-side enforcement of the interactivity decision for player interactions with the blocks of a moved
  contraption, including storage access, seat boarding and actor behaviours.
- Client-side mirror of the decision so a denied right click does not swallow the click.
- Caching of the decision per contraption entity, invalidated on contraption block changes, world
  start/stop and configuration load/reload.
- `create_sable_n_rails.mixins.json` and the `[[mixins]]` entry in `neoforge.mods.toml`; five mixin classes
  (`AbstractContraptionEntityMixin`, `CarriageContraptionEntityMixin`, `ContraptionHandlerClientMixin`,
  `AbstractContraptionEntityClientMixin`, `TrainRelocationPacketMixin`), with the two client-only ones
  (`ContraptionHandlerClientMixin`, `AbstractContraptionEntityClientMixin`) under the `client` key.
- JUnit tests for the pure policy logic and in-game GameTests for registration, rotation/mirroring, NBT
  round trip, contraption classification and marker visibility in the contraption block map.
- `gameTestServer` development run and an empty 3x3 GameTest structure template.
- Expanded upstream research with exact file/line evidence for the implemented feature.

### Notes

- The addon has **not** been compiled or run in this environment: there is no JDK and the Gradle/Maven
  hosts are unreachable. JUnit tests, GameTests and in-game checks were written but never executed.
  See `IMPLEMENTATION_STATUS.md`.
- No third-party code or assets were used. Behaviour was re-implemented independently from the sources
  reviewed in `UPSTREAM_RESEARCH.md`.

## 0.1.0 - Unreleased

- Establish a Java 21 / NeoForge 1.21.1 Gradle project with pinned Create and Sable dependencies.
- Add mod metadata and client/server development run configurations.
- Record initial upstream compatibility, API, and licensing research.
- No gameplay integration is implemented yet.
