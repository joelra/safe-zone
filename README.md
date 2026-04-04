# Safe Zone

![Minecraft 1.21.11](https://img.shields.io/badge/Minecraft-1.21.11-3C8527?style=flat-square)
![Fabric Loader](https://img.shields.io/badge/Loader-Fabric-db202c?style=flat-square)
![Server Only](https://img.shields.io/badge/Side-Server%20Only-4051b5?style=flat-square)
![Java 21+](https://img.shields.io/badge/Java-21%2B-e76f00?style=flat-square)
![License MIT](https://img.shields.io/badge/License-MIT-lightgrey?style=flat-square)

Safe Zone is a **server-only** Fabric land-claim mod for **Minecraft 1.21.11**. Players protect builds with a configurable vanilla claim wand (**Golden Hoe** by default), admins manage claims with `/sz`, and **unmodded clients can still join and use the core workflow**.

## Highlights

- Server-side land claims for Fabric servers
- No client mod required for normal play
- First-join starter wand using a vanilla item
- In-world claim creation, resizing, trust management, and removal
- Player self-service tools through `/claim`
- Admin moderation and management tools through `/sz`
- JSON-based world data stored under `<world>\safe-zone\`

## Requirements

- Minecraft **1.21.11**
- Fabric Loader
- Fabric API
- Java **21+**

## Install

1. Install Fabric Loader on the server.
2. Put **Safe Zone** and **Fabric API** in the server `mods` folder.
3. Start the server once to generate the Safe Zone data/config files.
4. Join normally. Players do **not** need this mod installed on the client for the current feature set.

## Automation

- **CI** runs on pull requests and pushes to `main`, building the mod and uploading the generated jars as workflow artifacts.
- **Releases** are created automatically when a tag like `v1.0.0` is pushed. The workflow verifies the tag matches `mod_version`, builds the mod, and attaches the jars to a GitHub Release.

## Player quick start

1. Join the server and receive the configured claim wand once (default: **Golden Hoe**).
2. Right-click one block with the wand to save corner 1.
3. Right-click a second block with the wand to create the claim.
4. Stand inside your claim and:
   - **Shift + left-click** with the wand to open the build-access menu
   - **Shift + right-click** with the wand to remove the claim after confirming
   - **Right-click one of your claim corners** to start resizing
5. Use `/claim` for claim lists, trust helpers, and claim info.

## Current feature set

- First-join starter kit that gives each player one claim wand
- Two-corner claim creation in the Overworld
- Claim resizing by right-clicking an owned corner with the wand
- Claim removal by **Shift + right-click** inside an owned claim, with a default 5-second confirmation window
- Trust management by **Shift + left-click** inside an owned claim with the wand, opening a vanilla chest-style menu
- Player self-service claim browsing and trust/remove helpers through `/claim`
- Admin claim inspection, transfer, trust overrides, teleport, reload, give-wand, and limit controls through `/sz`
- Server-side outline/preview visualization without a client mod

## Commands

### Player commands

`/claim` and `/claims` are aliases.

- `/claim`
- `/claim help`
- `/claim list [page]`
- `/claim trusted [page]`
- `/claim here`
- `/claim info [claimId]`
- `/claim trust <claimId>`
- `/claim remove <claimId>`

`/claim remove` requires the same command twice within the configured confirmation window (10 seconds by default).

### Admin commands

`/sz` and `/safezone` are aliases and require operator/game-master level access.

- `/sz`
- `/sz help`
- `/sz list [page]`
- `/sz list owner <player> [page]`
- `/sz list trusted <player> [page]`
- `/sz info [claimId]`
- `/sz remove <claimId>`
- `/sz removeall <player>`
- `/sz notifications`
- `/sz notifications purge`
- `/sz notifications purge confirm`
- `/sz transfer <claimId> <player>`
- `/sz trust <claimId> <player>`
- `/sz untrust <claimId> <player>`
- `/sz tp <claimId>`
- `/sz inspect`
- `/sz reload`
- `/sz givewand [player]`
- `/sz limits <player> <maxClaims>`

`/sz inspect` toggles inspect mode. While it is enabled, admins can **Shift + right-click with an empty hand** to read claim info in-world.

## Configuration and data

Safe Zone stores gameplay and ops settings together in `<world>\safe-zone\config.json`. The file is pretty-printed and grouped into `gameplay` and `ops` sections on first load.

### Defaults

- `claimWandItemId` — `minecraft:golden_hoe`
- `starterKitEnabled` — `true`
- `dropStarterKitWhenInventoryFull` — `true`
- `defaultMaxClaims` — `3`
- `maxClaimWidth` / `maxClaimDepth` — `64` / `64`
- `claimGapEnforced` — `false`
- `claimGapMinDistance` — `10` blocks when enabled
- `claimExpiryDays` — `30` days stored in config; automatic inactivity expiry is still future work
- `notificationsEnabled` — `true`; when `false`, offline admin notices are disabled
- `notificationRetentionDays` — `30` days; pending offline admin notices older than this are pruned from `notifications.json`
- `wandRemoveConfirmSeconds` — `5`
- `commandRemoveConfirmSeconds` — `10`

### Rules and files

- Claims are **Overworld-only**
- Claims are **full-height columns**
- Per-player claim limits are stored in `player_limits.json`
- Ops settings live in the `ops` section of `<world>\safe-zone\config.json`
  - `auditLogEnabled` defaults to `false`
  - `mirrorAuditToServerLog` defaults to `false`
  - `createDataBackups` defaults to `false`
  - `recoverFromBackupOnLoadFailure` defaults to `false`
- Stored files include:
  - `claims.json`
  - `config.json`
  - `player_limits.json`
  - `starter_kit_recipients.json`
  - `notifications.json`
  - `safe-zone_audit.log`

`notifications.json` stores pending offline admin notices, such as claim removals done while a player is away. Entries are written when a notice is queued, removed after delivery on join, and stale undelivered notices are pruned after the configured retention window. When `notificationsEnabled` is `false`, Safe Zone clears pending offline admin notices and stops queuing new ones. Config JSON files stay pretty-printed for editing, while runtime state JSON is saved compactly. All JSON state files save atomically, and backup creation is controlled by `ops.createDataBackups` in `config.json`. `/sz reload` also reloads `config.json`.

## Protection coverage

Safe Zone currently protects:

- Block breaking
- Block placement attempts
- Bucket placement/use targets
- Fluid spread into protected claims from outside or a different claim
- General block use inside claims
- Minecart interaction in claims
- Fire spread into claims
- Explosion block damage in claims
- Explosion-created fire in claims
- Paintings, item frames, minecarts, boats, and armor stands from explosion damage or movement
- Trusted/owner/admin-bypass players from explosion damage and knockback inside claims
- Claim-wand interactions without hoe tilling or durability use

Not implemented yet:

- Piston movement protection
- Enderman grief prevention
- General non-explosion entity-damage protection
- Automatic inactivity expiry processing

## Building

From the repository root on Windows:

```powershell
.\gradlew.bat build
```

Useful commands:

```powershell
.\gradlew.bat check
.\gradlew.bat test
.\gradlew.bat runServer
```
