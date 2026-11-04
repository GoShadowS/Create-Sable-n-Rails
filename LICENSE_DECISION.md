# License decision

## Decision

License original Create: Sable 'n' Rails source and documentation under **MIT** (`MIT`). The standard MIT grant is in [`LICENSE`](./LICENSE). Copyright attribution is `Shad11t`, based on the author metadata of both existing commits (`ea28a21` and `198c523`); the year is 2026. This scope does not include content without clear project authorship, third-party software or assets, nor the user-provided untracked `AI_TASK.md`.

MIT is a permissive license that clearly permits modification, redistribution, modpack inclusion, and forks while requiring preservation of the notice and disclaimer. This fits the small, currently single-author addon and lowers friction for downstream modpacks and collaboration. Unlike copyleft, MIT does not require distribution of derivative source under the same terms. Apache-2.0 was considered for its explicit patent grant, but adds notice and patent-termination requirements without an identified project need. A copyleft option such as GPL-3.0 would increase reciprocal source obligations and could discourage modpack and addon integration. A dual license was not selected because no second licensing requirement is established; avoid confusing `OR` (choice of license) with `AND` (both licenses required).

This is a project decision, not legal advice. Confirm the attribution and rights before distributing.

## Comparison

| Project and reviewed source | License / scope | Relevance and implications |
|---|---|---|
| [Create](https://github.com/Creators-of-Create/Create), `mc1.21.1/dev`, [`LICENSE.md`](https://github.com/Creators-of-Create/Create/blob/mc1.21.1/dev/LICENSE.md) | MIT for code outside `src/main/resources/assets/`; assets All Rights Reserved | Similar Create ecosystem project. Demonstrates the need to separate code rights from assets. No Create code or assets are copied here. |
| [Create: Interactive](https://github.com/ValkyrienSkies/Create-Interactive), `1.20.x/main`, [`LICENSE`](https://github.com/ValkyrienSkies/Create-Interactive/blob/1.20.x/main/LICENSE) | Copyright All Rights Reserved | Direct functional reference, but not an open-source license grant. No code, text, or assets are reused. Its reviewed branch targets Minecraft 1.20.1, so it is not evidence of 1.21.1 compatibility. |
| [Sable](https://github.com/ryanhcode/sable), main at `227077f7f528c9beab1336a9fad2e27b754e4e70`, [`LICENSE.md`](https://github.com/ryanhcode/sable/blob/227077f7f528c9beab1336a9fad2e27b754e4e70/LICENSE.md) | PolyForm Shield 1.0.0, unless otherwise stated; applies to Sable repository content | Direct target dependency; includes a noncompete condition. It does not license this addon's own files. No Sable implementation is copied. |
| [Sable Companion](https://github.com/ryanhcode/sable-companion), main at `652ecf0e051846f3a433dd20126dd4d64ec3793a`, [`LICENSE`](https://github.com/ryanhcode/sable-companion/blob/652ecf0e051846f3a433dd20126dd4d64ec3793a/LICENSE) | MIT | Relevant lightweight compatibility library. It is not included as source in this repository. |

## Material scope and exclusions

- MIT applies only to original project source and text files authored for this repository where authorship/rights are clear.
- `AI_TASK.md` is untracked user-provided material and is excluded.
- Minecraft and all libraries/mods retain their own licenses. Dependency declarations do not transfer their licenses to this project.
- No third-party textures, models, sounds, logos, or source files are included. New game assets must be original or separately licensed and identified.
- The first implementation only references APIs of Create and Sable; it does not redistribute their JARs.
