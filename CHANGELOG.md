# Changelog

## 1.5.0 — 2026-08-12

Minecraft 26.2 support — and one download for every server we support: this release ships a jar per Minecraft version for both Fabric and Paper.

### What's new

- **Minecraft 26.2 support** on Fabric and Paper
- **Pick the jar that matches your server**: each release now includes builds for Minecraft `1.21.11`, `26.1.x`, and `26.2` — e.g. a Paper server on 26.2 wants `SafeZone-Paper-1.5.0+26.2.jar`
- **Wind charges work again** (#5, #6): wind-charge and Breeze wind-burst knockback was wrongly blocked in the wilderness and inside claims you own or are trusted in. Wind charges now launch players everywhere, while TNT and creeper protection inside claims is unchanged.
- **New config option `windChargeKnockbackInClaims`** (default `true`): set it to `false` if you want wind-charge knockback suppressed for players standing inside claims — the wilderness is never affected. Apply with `/sz reload`.

### Compatibility notes

- The `1.21.11` jars run on Java 21+; the `26.1.x` and `26.2` jars require Java 25
- Existing configs, claims, and data work unchanged — the new config key is optional
- FastAsyncWorldEdit integration stays dormant until FAWE ships a 26.2 build; WorldEdit and Axiom integrations are unaffected

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
