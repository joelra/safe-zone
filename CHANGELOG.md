# Changelog

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
