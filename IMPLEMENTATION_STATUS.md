# Implementation status

Updated: 2026-11-04

## Initial audit

- Repository started at `main`, matching `origin/main`, clean except for user-provided untracked `AI_TASK.md` (preserved and excluded from this project's license). The only tracked project file was a 27-byte README containing the project title. Git history contains two user-authored commits; no source, Gradle build, tests, assets, or LICENSE were present.
- Java 21 is installed; current `java -version` output is OpenJDK `21.0.11` (2026-04-21 build). No system Gradle is available; a Gradle 8.10.2 wrapper was added.
- No commits or pushes have been made.

## Completed in this pass

- Rechecked runtime version pins: Java 21.0.11; Minecraft 1.21.1; NeoForge 21.1.228; Create 6.0.10-280; Sable 2.0.6; Ponder 1.0.82; Flywheel 1.0.6; Registrate `MC1.21-1.3.0+67`; Sable Companion 1.6.0 transitively. Fetched Sable's actual release tag `mc1.21.1-2.0.6-neoforge`, commit `ad8b4d377741fbd923f4d863b2cf9e462daff9a6`; its `gradle.properties` explicitly confirms these coordinates and Java version. Inspected the exact-release Create integration mixin sources, not only `main`.
- Chose a Create/Sable API dependency plus Sable's NeoForge runtime module for development runs; pinned dependent library versions. Sable Companion 1.6.0 is provided transitively by Sable and is not manually shaded.
- Added a minimal NeoForge mod entry point and metadata. No gameplay feature is implemented.
- Added English/Russian README, upstream research, initial changelog, license decision, MIT license, dependency notices, and this continuation record.

## Continuation: exact release research (2026-11-04)

- Sable tag `mc1.21.1-2.0.6-neoforge` resolves to commit `ad8b4d377741fbd923f4d863b2cf9e462daff9a6`; exact-tag `gradle.properties` confirms Java 21 and the pinned runtime versions. Exact-tag `AbstractContraptionEntityMixin` and `ContraptionColliderMixin` inspected; Sable owns confirmed physics/plot integration, collision adaptation, and its projected raytrace integration. This means a second conversion of Create contraptions into Sable-owned sub-level data would be unsafe and duplicative.
- Added a capability boundary table in `UPSTREAM_RESEARCH.md`: separates standard Create lifecycle, Sable's upstream additions, and Create: Interactive candidate behaviors not demonstrated for this pairing. No specific absent Interactive feature plus safe API/hook is established yet; its reviewed reference targets 1.20.1. Accordingly, no gameplay feature is claimed as implemented or added in this continuation. This is an evidence blocker, not a claim the mod is complete.
- Repeated `gradlew.bat build --no-daemon` once. Cached distribution was corrupt; subsequent download stalled near 35% for three minutes. Stopped that attempt. Previous run's exact `Connection reset`/`UnknownHostException` remained the reason the build was blocked. No build/game test success claimed.
- Current `java -version` verifies OpenJDK 21.0.11. User-provided `.vscode/` and `AI_TASK.md` remain unchanged; no commits/pushes.

## Validation

- `java -version`: Java 21.0.11 (success).
- `gradlew.bat --version`: not run successfully because no wrapper existed at initial audit.
- `gradlew.bat build`: two bounded attempts. Prior attempt reported `Connection reset`, `UnknownHostException: services.gradle.org`, and `zip END header not found` for the partial Gradle 8.10.2 ZIP. This session retried once; it again hit the corrupted cached ZIP and began a new download, which stalled at about 35% for three minutes without finishing. Stopped that hanging attempt; build remains unverified. Do not retry until network access to `services.gradle.org` is restored.
- `git diff --check`: passed.
- `neoforge.mods.toml`: standalone parser validation was not available (Python is not installed); Gradle/ModDev metadata validation has not run because the distribution could not be downloaded.
- No client/server launch or gameplay tests have run.

## Blockers and next concrete work

1. Complete/retry `gradlew.bat build`, fix dependency resolution or compile errors; confirm `runClient`, `runServer`, and registration before gameplay integration.
2. Trace exact Create 6.0.10-280 contraption assembly, movement, dismantling, block entity/NBT, kinetics, train and interface lifecycle. Current bounded source evidence confirms Sable 2.0.6 already handles Create physics, collision and projected raytrace via its own mixins; no distinct missing behavior and safe addon hook has yet been confirmed.
3. Do not invent a gameplay feature or duplicate Sable integration. After a specific gap and safe API/hook are proven from exact source, implement the smallest server-authoritative feature and test persistence, loss/duplication, and client/server behavior. Until then, the gameplay part is blocked.
4. Only then consider per-contraption interaction state/markers, nested contraptions/bearings, train derailing/buffer stops, and item/fluid interfaces. These remain planned and unsupported.
5. Audit the final JAR for accidental third-party embedding and refine notices before any release.
