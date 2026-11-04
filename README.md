# Create: Sable 'n' Rails

**Unofficial, experimental compatibility project** for Create contraptions and Sable sub-levels. This project is not an official port and is not endorsed by the Create, Create: Interactive, or Sable teams. It does not use Valkyrien Skies ship objects.

## Target platform

- Minecraft **1.21.1**, Java **21**, **NeoForge only**.
- Build-pinned dependency versions: NeoForge `21.1.228`, Create `6.0.10-280`, Sable `2.0.6`, Ponder `1.0.82`, Flywheel `1.0.6`, Registrate `MC1.21-1.3.0+67`.
- Sable Companion `1.6.0` is a transitive API dependency of Sable; this addon does not bundle it.
- Exact Sable `2.0.6` release metadata confirms the Java/runtime pins. A completed Gradle build and game run are still unavailable because the Gradle distribution download is failing. See [UPSTREAM_RESEARCH.md](./UPSTREAM_RESEARCH.md).

## Status

### Implemented

- Java 21 / NeoForge Gradle project skeleton, mod metadata, and client/server development run definitions.
- Upstream Create/Sable compatibility already provided by Sable 2.0.6 (physics integration, collision adaptation, projected raytrace); this addon has not added gameplay behavior.

### Experimental

- None yet.

### Planned

- Trace the exact Create lifecycle and identify a concrete feature gap not already handled by Sable, plus a safe extension point.
- Implement and verify that smallest confirmed gap, including persistence and teardown; avoid duplicating Sable's upstream compatibility layer.
- Evaluate bearings/nested contraptions, trains, and item/fluid interfaces only after source/API review.

### Unsupported

- Fabric and Forge.
- Valkyrien Skies integration and Create: Interactive 1.20.1-specific behavior.
- Any feature not listed under Implemented.

No gameplay integration, marker block, recipe, item, bearing, train change, or interface transfer is implemented in this initial scaffold.

## Installation

There is no release JAR yet. Do not install this development scaffold in a game instance. When a verified release exists, install the same supported NeoForge 1.21.1 runtime versions listed above on both client and server and follow the release-specific instructions.

## Build

Install a Java 21 JDK, then run from the repository root:

```powershell
.\gradlew.bat build
```

To start development environments:

```powershell
.\gradlew.bat runClient
.\gradlew.bat runServer
```

## Bugs and support

Report reproducible issues in the project repository. Include Minecraft, NeoForge, Create, and Sable versions, a minimal reproduction, and relevant logs after removing credentials or other private data. Do not report an upstream defect against Create or Sable based solely on behavior in this unofficial addon.

## License

Original project source and documentation with clear project authorship are licensed under MIT; see [LICENSE](./LICENSE) and [LICENSE_DECISION.md](./LICENSE_DECISION.md). Third-party dependencies retain their own terms; see [THIRD_PARTY_NOTICES.md](./THIRD_PARTY_NOTICES.md). This license does not cover user-provided instructions or third-party assets and code.
