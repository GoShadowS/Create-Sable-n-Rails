# Create: Sable 'n' Rails

**Unofficial, experimental addon** that adds a server-authoritative interactivity layer for Create
contraptions that move as Sable sub-levels. This project is not an official port of Create: Interactive
and is not endorsed by the Create, Create: Interactive or Sable teams. It does not use Valkyrien Skies
ship objects and does not depend on Valkyrien Skies.

## Target platform

- Minecraft **1.21.1**, Java **21**, **NeoForge only** (no Fabric, no Forge).
- Direct dependency versions declared in [`gradle.properties`](./gradle.properties): NeoForge `21.1.228`,
  Create `6.0.10-280`, Sable `2.0.6`, Ponder `1.0.82`, Flywheel `1.0.6`, Registrate `MC1.21-1.3.0+67`.
- **Sable Companion is not a direct dependency.** It is not declared in [`build.gradle`](./build.gradle); its
  group appears there only in a repository filter, which says where it could be fetched from, not that it is on
  the classpath. Whether it arrives transitively through Sable, and whether any version of it ends up in the
  build or the JAR, is **unverified** — no Gradle run has been executed for this project.
- APIs were checked against the Create and Sable sources named in
  [UPSTREAM_RESEARCH.md](./UPSTREAM_RESEARCH.md), with one documented gap: the presence of
  `RegisterGuiLayersEvent` in the exact NeoForge build `21.1.228` is marked there as **not verified**.
  No other API claim in this README is verified by a compiled or running build.

## Status

### Implemented

- **Interact Me / Interact Me Not markers** (`create_sable_n_rails:interact_me`,
  `create_sable_n_rails:interact_me_not`): instant-break, waterloggable, face-attached plates
  (floor, ceiling, wall) with original textures, crafting recipes using Create's andesite alloy and
  cogwheel, loot tables, and recipe-unlock advancements.
- **Six server-side policies**, one per contraption category (trains, translating, bearings, clockwork,
  mounted, other), each `ALWAYS` / `WITHOUT_STICKER` / `WITH_STICKER` / `NEVER`. Defaults:
  `WITH_STICKER` for the five known categories, `NEVER` for unknown addon contraptions.
- **Server-authoritative enforcement** for every right-click on the blocks of a moved contraption:
  inventories/storage, seats, wrench and actor interactions all go through the same gated method, so a
  rejected interaction is ignored by the server even if a modified client sends the packet anyway. Seats
  and wrenches are blocked on purpose — see "Enforcement details" below for the one client-side side
  effect this has.
- **Client-side mirror** of the decision: a contraption with nothing interactive is skipped by the
  client's interaction raytrace, so looking at it behaves like looking past it (the held item keeps
  working) instead of the click being consumed.
- **Persistence through Create's own data**: markers are ordinary contraption blocks, so the state
  survives disassembly, reassembly, chunk reload and world save/load with no addon-specific NBT.
- **Fresh state after runtime changes**: the decision is recomputed when the contraption's block map
  changes — all three Create write paths are covered, including the carriage override that bypasses
  `super` and the client-side block-change packet — and when a world starts or stops or the configuration
  is loaded. A cached decision is reused only when the exact marker signature (position and marker kind, with
  structural comparison, not a hash), the contraption category and the configuration policy still match, so
  swapping one marker for the other at the same position cannot keep a stale decision.
- **Locked-contraption HUD indicator** (client-only): while the crosshair is on a contraption the policy
  denies, one line of text above the hotbar says the interaction is locked. It answers "why did my click do
  nothing", never appears for an allowed contraption, and is suppressed by F1 and spectator mode. It draws
  from the same policy method the interaction gate uses, so it cannot contradict what the server does; it
  has no authority of its own. Targeting reuses Create's own client raytrace — `getRayInputs` for the
  reach-limited ray, `rayTraceContraption` for real block shapes — over Create's loaded-contraption registry;
  a bounding-box intersection is only a pre-filter and never counts as a hit, so grazing a contraption that
  the ray misses inside does not show the badge.
- Java 21 / NeoForge Gradle project, mod metadata, mixin configuration, `runClient` / `runServer` /
  `gameTestServer` runs, JUnit tests for the pure decision logic, and in-game GameTests.

### Experimental

- The whole interactivity layer is experimental: it has been written and statically parsed, but **no
  Gradle build, test run or in-game run has been executed** in the environment where it was developed
  (see "Verification" below). Treat it as unverified until it compiles and passes the manual checklist.

### Planned

- Re-evaluate derailment / Train Buffer Stop, Propagator Bearings (including the disjointed variant) and
  cross-sub-level item/fluid transfer **only** if a safe, documented API for the pinned versions can be
  established. No such API was found in Create 6.0.10 or Sable 2.0.6.

### Unsupported

- Fabric and Forge.
- Valkyrien Skies integration, VS2 ship objects, and Create: Interactive 1.20.1-specific behaviour.
- Derailment, Train Buffer Stop, Propagator Bearings, nested-contraption changes, and portable/fluid
  interface transfer. These are **not** implemented; nothing in the code pretends otherwise.
- Any feature not listed under Implemented.

## How it works

| Piece | Behaviour |
|---|---|
| Marker blocks | The marker state lives in `Contraption#getBlocks()`, which Create serialises into the contraption entity's NBT and writes back to the world on disassembly. Nothing else persists the decision. |
| Policy resolution | `ContraptionCategory` classifies the contraption; the matching server config value is applied. `ALWAYS`/`NEVER` ignore markers (server setting wins); `WITHOUT_STICKER` is a deny-list; `WITH_STICKER` is an allow-list. |
| Enforcement | One cancellable `HEAD` inject on `AbstractContraptionEntity#handlePlayerInteraction`. Returning `false` is Create's own "not handled" result: storage access, seat boarding, wrench handling and actor behaviours are all skipped and `sender.swing(...)` is never reached. |
| Client mirror | An `@Redirect` on the single `rayTraceContraption` *call* inside `rightClickingOnContraptionsGetsHandledLocally`, so a contraption with no interactive block is skipped by the interaction loop. Cosmetic only; the server decides. |
| Assembly gate | The markers declare themselves movable via `BlockMovementChecks.registerMovementAllowedCheck` (`SUCCESS` for the two markers, `PASS` for everything else). Without it `PushReaction.BLOCK` makes them "unmovable" and any contraption carrying a marker fails to assemble. |
| Seats and wrenches | Deliberately gated like everything else: no boarding and no train relocation on a denied contraption. Relocation is additionally blocked where it actually completes — `TrainRelocationPacket#handle` on the server — so entering the client-side mode is not enough to move a denied train. |
| Sable | Sable 2.0.6 already provides physics, collision and sub-level raytrace integration for Create contraptions. This addon does not duplicate any of it and adds no ship objects. |

## Enforcement details

**What a denied interaction cancels.** The gate is a `HEAD` cancel on
`AbstractContraptionEntity#handlePlayerInteraction`, which is the one method Create uses for every
right-click that reaches a contraption's blocks. Cancelling it means the method body never runs: no
`contraption.interactors` dispatch, no `MountedStorageManager#handlePlayerStorageInteraction`, no seat
boarding, no passenger ejection. `ContraptionInteractionPacket#handle` then skips `sender.swing(...)`,
because that call is guarded by the same return value.

**Wrench relocation is gated at three points, not one.** `false` from `handlePlayerInteraction` is a
*branch selector* on the client, not a stop: it routes into `handleSpecialInteractions`, whose
wrench-on-carriage case calls `TrainRelocator#carriageWrenched` and puts the client into relocation mode.
The move itself is requested later by an unrelated `TrainRelocationPacket`, whose handler
(`TrainRelocationPacket#handle`) is the only caller of `TrainRelocator#relocate(...)` in Create. So a
denied contraption is stopped at: (1) the contraption interaction gate, (2) `handleSpecialInteractions` —
so the relocation mode never starts on the client, and (3) `TrainRelocationPacket#handle` — so even a
client that enters the mode (or a modified client that sends the packet directly) cannot complete the
move. Permitted trains take Create's original code path and relocate exactly as before.

**Relocation is judged over the whole train, on both sides.** `TrainRelocator#relocate` moves the *train*:
it loops over `train.carriages` and mutates train-wide state. A train is a list of independent carriages,
each with its own `CarriageContraption`, block map and markers, so naming an allowed carriage must not
launder a denied one. The rule lives in one method,
`ContraptionInteractionPolicy#isTrainInteractive(Train, Level)`, which both the client pre-check
(`ContraptionHandlerClientMixin`, before `TrainRelocator#carriageWrenched`) and the authoritative server
check (`TrainRelocationPacketMixin`) call — the two cannot drift apart.

| Situation | Client | Server |
|---|---|---|
| Every carriage interactive | relocation mode starts | packet accepted, train relocates |
| Any carriage non-interactive | click on **any** carriage of the train does not start the mode | packet rejected if sent anyway |
| Modified client names an allowed carriage of a train containing a denied one | (bypassed) | rejected: the whole train is evaluated, not the named carriage |

**Forged or malformed packets are rejected before Create's code runs.** Both ids in `TrainRelocationPacket`
are client-controlled. The mixin cancels at `HEAD` when `trainId` does not resolve, when `entityId` is not a
carriage, when that carriage belongs to a different train, or when any carriage of the train is
non-interactive. The first case is not only a policy matter: Create's own handler builds its log message
from `train.id.toString()` inside its `train == null` branch (`TrainRelocationPacket.java:51-53`), so an
unknown id reaching it is a `NullPointerException` on the server thread rather than a rejection. Cancelling
at `HEAD` means that line is never reached.

**Fail-closed edge case, and what it means in game.** Per carriage, the contraption is resolved through
the `CarriageContraptionEntity` registered for the relevant level. If a carriage has **no** such entity — for
example a carriage of a multi-dimension train that is not currently loaded in the dimension you are in, or a
carriage whose entity has not been created yet on the client — it is counted as **denied**. So a train that
is "allowed by configuration" is not automatically relocatable: every carriage must also be present and
loaded where the check runs. In practice a single-dimension train whose carriages are all loaded relocates
normally; a train that is partly in another dimension will refuse relocation until all of it is loaded in
the checker's dimension. That is deliberate (there is nothing to evaluate otherwise) and it is the reason
this page does not claim that a permitted train can always be relocated.

**Seats and wrenches are inside the gate on purpose.** Create treats a seat as a block interaction, so
exempting seats would let players board exactly the contraptions the server marked non-interactive — the
most important case for trains. Same reasoning for the wrench: relocating a carriage *is* an interaction
with that carriage.

**Default `otherHandling = "NEVER"` and what it means.** Any contraption type this addon does not
recognise (i.e. one contributed by another addon) is non-interactive by default, which preserves the
pre-0.2.0 behaviour for those contraptions but also means that, out of the box, you cannot open a chest on
a contraption from an unknown addon. The five known Create categories default to `WITH_STICKER`, i.e. they
are non-interactive until an `interact_me` marker is attached. Both defaults are deliberate and are not
being changed silently; set a category to `ALWAYS` to restore unconditional interaction.

## Configuration

`config/create_sable_n_rails-common.toml` (empty placeholder) and
`config/create_sable_n_rails-server.toml`, plus a per-world copy under `<world>/serverconfig/`.
The server file contains six values:

```toml
[interactivity]
    otherHandling = "NEVER"
    trainHandling = "WITH_STICKER"
    translatingHandling = "WITH_STICKER"
    bearingHandling = "WITH_STICKER"
    clockworkHandling = "WITH_STICKER"
    mountedHandling = "WITH_STICKER"
```

The values are synced to clients by NeoForge; a client cannot grant itself interaction rights.

## Installation

There is no verified release jar yet. When one exists: install the same Minecraft 1.21.1 NeoForge and the
pinned Create and Sable versions on **both** client and server, then drop the addon jar into `mods/`.
The addon is required on the server; on the client it is needed for the raytrace mirror and the marker
models, so install it on both.

## Build

With a Java 21 JDK and network access to Maven Central, the NeoForged Maven and the Create/Sable Maven:

```bash
./gradlew build          # compile + jar
./gradlew test           # JUnit tests for the pure policy logic
./gradlew runClient      # development client
./gradlew runServer      # development dedicated server
./gradlew gameTestServer # headless GameTests
```

## Verification

The addon was developed in an environment **without a JDK and without access to the Gradle/Maven hosts**.
What was actually done, and what was not:

- All sources were parsed with a Java parser (0 syntax errors) and all JSON assets were validated; the
  GameTest structure file was decoded with an independent NBT reader.
- `./gradlew build`, `./gradlew test` and `./gradlew gameTestServer` were **not** run.
- Nothing has been tested in game.

See [IMPLEMENTATION_STATUS.md](./IMPLEMENTATION_STATUS.md) for the exact list and the manual test checklist.

## Bugs and support

Report reproducible issues in the project repository. Include Minecraft, NeoForge, Create and Sable
versions, a minimal reproduction, and relevant logs with any private data removed. Do not report an
upstream defect against Create or Sable based only on behaviour of this unofficial addon.

## License

Original project source, documentation, models and textures authored for this repository are licensed
under **MIT**; see [LICENSE](./LICENSE) and [LICENSE_DECISION.md](./LICENSE_DECISION.md). Third-party
dependencies keep their own terms; see [THIRD_PARTY_NOTICES.md](./THIRD_PARTY_NOTICES.md). No Create,
Create: Interactive, Sable or Sable Companion code or assets are copied into this project, and no
third-party logos or branding are used.
