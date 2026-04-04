# CLAUDE.md

## Build

Use the Gradle wrapper from the repo root:

- `.\gradlew.bat build`
- `.\gradlew.bat check`
- `.\gradlew.bat test`
- `.\gradlew.bat runServer`

CI runs `build` on Java 25. The project targets Java 21.

## Repo focus

Safe Zone is a **server-only** Fabric land-claim mod for Minecraft 1.21.11. The current behavior reference is `README.md`; keep docs aligned with shipped code, not old plans.

## Important code areas

- `src\main\java\com\simpleforapanda\safezone\SafeZone.java` — bootstrap, startup/shutdown, starter kit
- `...\command\PlayerCommand.java` — `/claim` and `/claims`
- `...\command\AdminCommand.java` — `/sz`, `/safezone`, `/sz inspect`
- `...\item\ClaimWandHandler.java` and `...\item\ModItems.java` — configured claim-wand behavior
- `...\listener\ProtectionListener.java` — protection hooks
- `...\manager\ClaimManager.java` — claims, limits, JSON persistence
- `...\screen\TrustMenu.java` — vanilla 9x6 trust menu

## Guardrails

- Use **Mojang Official Mappings**
- Mod ID: `safe-zone`
- Package root: `com.simpleforapanda.safezone`
- Keep the core flow server-compatible; do not require custom client items or screens
- The default claim wand is a **vanilla Golden Hoe**, but the actual wand item comes from the `gameplay` section of `config.json`; claim actions must suppress vanilla item behavior when the configured wand is used
- Claims are **Overworld-only**, **full-height columns**, default **64x64 max**, default **3 claims per player**
- Gameplay tuning lives in `<world>\safe-zone\config.json` under the `gameplay` section
- Key gameplay config fields: `claimWandItemId`, `starterKitEnabled`, `dropStarterKitWhenInventoryFull`, `defaultMaxClaims`, `maxClaimWidth`, `maxClaimDepth`, `claimGapEnforced`, `claimGapMinDistance`, `claimExpiryDays`, `notificationsEnabled`, `wandRemoveConfirmSeconds`, `commandRemoveConfirmSeconds`
- Persist UUIDs as **strings**
- `/sz inspect` means **Shift + right-click with an empty hand**
- There is currently **no `src\client` source set**
- Only document protections and automation that are actually implemented
