# Third-party notices

This addon does not redistribute the following runtime mods or libraries in its JAR. They must be installed separately where noted. Their licenses remain independent of this project license.

| Project | Version / coordinate | License and source | Included in addon JAR? |
|---|---|---|---|
| Minecraft | 1.21.1 | Microsoft/Mojang Minecraft EULA and applicable terms | No |
| NeoForge | 21.1.228 | LGPL-2.1, [official repository](https://github.com/neoforged/NeoForge) and published POM | No |
| Create | 6.0.10-280 | MIT for code; assets All Rights Reserved; [license overview](https://github.com/Creators-of-Create/Create/blob/mc1.21.1/dev/LICENSE.md) | No |
| Sable | 2.0.6 | PolyForm Shield 1.0.0 unless otherwise stated; [license](https://github.com/ryanhcode/sable/blob/227077f7f528c9beab1336a9fad2e27b754e4e70/LICENSE.md) | No |
| Sable Companion | Version unverified in this project; referenced upstream as `1.6.0` (see `UPSTREAM_RESEARCH.md`) | MIT; [license](https://github.com/ryanhcode/sable-companion/blob/652ecf0e051846f3a433dd20126dd4d64ec3793a/LICENSE) | **Not a declared dependency.** Not declared in `build.gradle`; whether it resolves transitively through Sable, and whether any jar for it is present, is unverified because no Gradle resolution has been run |
| Ponder | 1.0.82 | MIT; [`LICENSE`](https://github.com/Creators-of-Create/Ponder/blob/main/LICENSE) | No |
| Flywheel | 1.0.6 | MPL-2.0; [`docs/LICENSE`](https://github.com/Engine-Room/Flywheel/blob/26.1.2/dev/docs/LICENSE) | No |
| Registrate | MC1.21-1.3.0+67 | MPL-2.0; [`LICENSE`](https://github.com/tterrag1098/Registrate/blob/1.21/LICENSE) | No |
| JUnit Jupiter | 5.10.2 (JUnit BOM) | EPL-2.0, [license](https://github.com/junit-team/junit5/blob/main/LICENSE.md) | No; test-only dependency, never packaged |

No third-party source, texture, model, audio, or logo is copied into this repository.

The assets under `src/main/resources/assets/create_sable_n_rails/` (two 16x16 PNG textures, two block
models, two item models, two blockstates, English and Russian language files) were created for this
project and are not derived from Create, Create: Interactive, Sable or any other mod. The block-state
rotation values follow the same convention Create uses for its own face-attached decoration blocks,
which is a factual convention, not copied content.

Behaviour was re-implemented independently after reading upstream sources as engineering reference
(see `UPSTREAM_RESEARCH.md`). No code, comments, text or assets were copied from those repositories:
Create: Interactive, the primary functional reference, is licensed All Rights Reserved.

Dependency resolution and the eventual distributable JAR still need inspection before release; update
this notice if any dependency is shaded, Jar-in-Jar'd or otherwise embedded.
