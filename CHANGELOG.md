# Changelog

## 1.5.0 (unreleased)

Multi-version release for **Minecraft 1.21.11, 26.1, and 26.2** on Fabric and Paper.

### Highlights

- Support for **Minecraft 26.2** on both loaders
- One release now ships a jar per supported Minecraft version (`1.21.11`, `26.1.2`, `26.2`) — pick the jar matching your server, e.g. `SafeZone-Fabric-1.5.0+26.2.jar`
- Wind charges and Breeze wind bursts knock players back again: knockback was wrongly suppressed in unclaimed wilderness and in claims the player owns or is trusted in (#5, #6). TNT/creeper knockback protection inside claims is unchanged.
- New config option `windChargeKnockbackInClaims` (default `true`): admins can set it to `false` to suppress wind-charge knockback for players standing inside claims; the wilderness is never affected.

### Internals

- Build restructured around [Stonecutter](https://stonecutter.kikugie.dev/) so all supported Minecraft versions build from a single source tree
- Runtime jars moved to `fabric\versions\<mc>\build\libs\` and `paper\versions\<mc>\build\libs\`
- Automated in-game test suite (GameTest framework) covering the claim protection matrix, explosion protection, and wind-charge knockback regressions — run per version with `:fabric:<mc>:runGameTest`

## 1.4.0

Update to **Minecraft 26.1** (26.1, 26.1.1, 26.1.2) on Fabric and Paper.

### Highlights

- Java 25 / Minecraft 26.1 toolchain migration for both loaders
- Axiom integration updated for 26.1; FAWE integration awaiting an upstream 26.1 build

## 1.3.1

- Fix claim visualization blocks not reappearing after chunks reload

## 1.3.0

Claim visualization improvements.

### Highlights

- Improved claim preview and outline rendering
- Off-hand wand support and `/claim show` toggle
- Trust menu improvements

## 1.1.0

First release with **Paper** support alongside Fabric.

### Highlights

- Split into shared-core, Fabric, and Paper modules; Paper plugin release jar
- Axiom and FAWE/WorldEdit integration for claim-restricted editing on Paper
- Long-range wand selection and configurable visualization settings
- Claim resize fixes

## 1.0.0

Initial public release for **Minecraft 1.21.11** on Fabric.

### Highlights

- Server-side-compatible land claims for unmodded clients
- Golden Hoe claim wand workflow, including first-join starter kit support
- Two-corner claim creation, resizing, removal confirmation, and vanilla menu trust management
- Player `/claim` tools and admin `/sz` management commands
- Persistent JSON-backed storage under `<world>\safe-zone\`
- Core protection coverage for block interaction, explosions, fluids, fire spread, and common entity interactions

### Known limitations

- Piston movement protection is still future work
- Enderman grief prevention is still future work
- General non-explosion entity-damage protection is still future work
- Claim inactivity expiry is configurable; `claimExpiryDays = 0` disables it, and owner login refreshes active claims while removing already-expired ones
