# Safe Zone

[![CI](https://github.com/joelra/safe-zone/actions/workflows/build.yml/badge.svg)](https://github.com/joelra/safe-zone/actions/workflows/build.yml)
[![Latest release](https://img.shields.io/github/v/release/joelra/safe-zone?style=flat-square)](https://github.com/joelra/safe-zone/releases)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-3C8527?style=flat-square)](https://www.minecraft.net/)
[![Platforms](https://img.shields.io/badge/Platforms-Fabric%20%7C%20Paper-5865F2?style=flat-square)](#compatibility)
[![License](https://img.shields.io/github/license/joelra/safe-zone?style=flat-square)](LICENSE)

Safe Zone is a **server-side land claim mod/plugin** for **Minecraft 1.21.11**. It supports **Fabric** and **Paper**, keeps the core experience usable for **unmodded clients**, and uses a configurable **vanilla item claim wand** instead of custom client content.

## Table of contents

- [At a glance](#at-a-glance)
- [Compatibility](#compatibility)
- [Install](#install)
- [Player quick start](#player-quick-start)
- [Features](#features)
- [Commands](#commands)
- [Configuration and data](#configuration-and-data)
- [Protection coverage](#protection-coverage)
- [Known limitations](#known-limitations)
- [Local development](#local-development)

## At a glance

| Topic | Summary |
| --- | --- |
| Client requirement | No client mod required for the current core workflow |
| Claiming flow | Use a configurable vanilla item wand to select two corners and create a claim |
| Visual feedback | Preview outlines help with creating, resizing, and inspecting claims |
| Player tools | `/claim` covers claim info, lists, trust access, and self-removal |
| Admin tools | `/sz` covers inspection, transfers, moderation, limits, reloads, and wand recovery |
| Platform support | Shared core logic with Fabric and Paper loader-specific adapters |

## Compatibility

| Item | Value |
| --- | --- |
| Minecraft | `1.21.11` |
| Java | `21+` |
| Fabric build | Fabric Loader + Fabric API |
| Paper build | Paper `1.21.11` |
| Optional Paper integrations | Axiom Paper Plugin, FastAsyncWorldEdit, or WorldEdit |
| Client requirement | None for the current core feature set |

## Install

Choose the build that matches your server software.

### Fabric

1. Install Fabric Loader on the server.
2. Put the Safe Zone **Fabric jar** in `mods\`.
3. Put **Fabric API** in `mods\`.
4. Start the server once to generate Safe Zone files under `<world>\safe-zone\`.

### Paper

1. Install a compatible Paper server.
2. Put the Safe Zone **Paper jar** in `plugins\`.
3. Optional: install **AxiomPaperPlugin** and/or **FastAsyncWorldEdit** or **WorldEdit** if you want claim-restricted editor support on Paper.
4. Start the server once to generate `plugins\SafeZone\`.

#### FastAsyncWorldEdit setup (Paper only)

Safe Zone registers a `FaweMaskManager` with FAWE so that every player edit session is automatically restricted to claims they own or are trusted in. This requires enabling FAWE's region-restriction framework in `FastAsyncWorldEdit/config.yml`:

```yaml
region-restrictions: true
```

With `region-restrictions: true`, FAWE consults Safe Zone (and any other registered protection plugins) before allowing block changes. Players without accessible claims cannot use WorldEdit at all. Admin players with `safezone.command.admin` bypass the restriction.

If `region-restrictions` is left as `false` (the FAWE default), FAWE skips all mask managers and Safe Zone's WorldEdit restrictions will have no effect.

## Player quick start

1. Join the server and receive the starter claim wand if starter-kit grants are enabled.
2. Right-click one block with the wand to save the first corner.
3. Right-click a second block to create the claim.
4. Stand inside your claim and use the wand to manage it:
   - **Shift + left-click** to open the trust/build-access menu
   - **Shift + right-click** to remove the claim after confirming
   - **Right-click a claim corner** to start resizing
5. Use `/claim` or `/claims` for claim info, lists, and trust helpers.

## Features

| Area | What it gives you |
| --- | --- |
| In-world claiming | Claim creation, resizing, and removal without custom client-side items |
| Access management | Owner/trusted/admin-bypass permission model with a vanilla-compatible trust menu |
| Staff moderation | Claim lookup, transfer, trust changes, removals, limits, reloads, and wand recovery |
| Paper integrations | Optional Axiom and FAWE/WorldEdit claim-restricted editing on Paper |
| Shared behavior | Consistent rules, persistence behavior, and claim logic across Fabric and Paper |

<details>
  <summary><strong>Claiming and visualization</strong></summary>

- First-join starter wand support
- Two-corner claim creation
- Claim resizing from owned corners
- Claim removal with confirmation
- Action-bar and preview visualization while using the wand

</details>

<details>
  <summary><strong>Trust and player self-service</strong></summary>

- Owner/trusted/admin-bypass permission model
- Vanilla-compatible trust/build-access menu
- Claim info and list commands for players
- Trusted-claim browsing for collaborators

</details>

<details>
  <summary><strong>Admin moderation and operations</strong></summary>

- Claim lookup by ID or current position
- Player-scoped claim browsing
- Transfer, trust, untrust, remove, and remove-all flows
- Claim limit overrides
- Config/data reload support
- Audit log and offline admin notification support

</details>

<details>
  <summary><strong>Optional Paper integrations</strong></summary>

- **Axiom Paper Plugin:** players with `safezone.axiom` can connect with Axiom, and block edits are restricted to claims they own or are trusted in
- **FastAsyncWorldEdit / WorldEdit:** player edit sessions are restricted to claims they own or are trusted in
- **Admin bypass:** players with `safezone.command.admin` bypass those edit restrictions
- These integrations are **Paper-only** and are inactive unless the corresponding plugin is installed

</details>

<details>
  <summary><strong>Persistence and platform model</strong></summary>

- Shared claim, notification, and audit logic across Fabric and Paper
- Atomic JSON saves
- Optional backup and recovery settings
- Platform-specific path layout with consistent behavior

</details>

## Commands

### Player commands

`/claim` and `/claims` are aliases.

| Command | Purpose |
| --- | --- |
| `/claim` | Show help |
| `/claim help` | Show command help |
| `/claim list [page]` | List claims you own |
| `/claim trusted [page]` | List claims where you are trusted |
| `/claim here` | Show the claim at your position |
| `/claim info [claimId]` | Show details for an accessible claim |
| `/claim trust [claimId]` | Open the trust menu for one of your claims |
| `/claim remove <claimId>` | Remove one of your claims after confirmation |

`/claim remove` must be run twice within the configured confirmation window.

### Admin commands

`/sz` and `/safezone` are aliases and require operator or equivalent admin access.

| Command | Purpose |
| --- | --- |
| `/sz` | Show help |
| `/sz help` | Show command help |
| `/sz list [page]` | List loaded claims |
| `/sz list owner <player> [page]` | List claims owned by one player |
| `/sz list trusted <player> [page]` | List claims that trust one player |
| `/sz info [claimId]` | Show claim details |
| `/sz here` | Show the claim at your position |
| `/sz remove <claimId>` | Remove one claim |
| `/sz removeall <player> confirm` | Remove every claim owned by a player |
| `/sz transfer <claimId> <player>` | Transfer ownership |
| `/sz trust <claimId> <player>` | Grant build access |
| `/sz untrust <claimId> <player>` | Remove build access |
| `/sz inspect [claimId]` | Inspect claims in-world or by ID |
| `/sz reload` | Reload config and data |
| `/sz givewand [player]` | Give a claim wand |
| `/sz limits <player> [maxClaims\|clear]` | Manage claim limit overrides |

## Configuration and data

### Server paths

| Platform | Install location | Config path | Runtime data | Audit log |
| --- | --- | --- | --- | --- |
| Fabric | `mods\` | `<world>\safe-zone\config.json` | `<world>\safe-zone\` | `<world>\safe-zone\safe-zone_audit.log` |
| Paper | `plugins\` | `plugins\SafeZone\config.json` | `plugins\SafeZone\data\` | `plugins\SafeZone\logs\audit.log` |

### Rules at a glance

- Claims are **Overworld-only**
- Claims are **full-height columns**
- UUID-backed data is stored as **strings**
- Runtime JSON is saved atomically
- `notifications.json` stores queued offline notices and prunes stale entries
- Claim inactivity expiry is **login-driven**:
  - if a player logs in **before** expiry, owned claims refresh their activity time
  - if a player logs in **at or after** expiry, expired claims are removed instead

<details>
  <summary><strong>Configuration reference</strong></summary>

### Gameplay settings

| Setting | Default | Acceptable values | What it controls |
| --- | --- | --- | --- |
| `claimWandItemId` | `minecraft:golden_hoe` | Any valid Minecraft item ID | Which vanilla item acts as the claim wand |
| `starterKitEnabled` | `true` | `true` or `false` | Whether players receive the wand automatically on first join |
| `dropStarterKitWhenInventoryFull` | `true` | `true` or `false` | Whether the wand drops at the player's feet if their inventory is full |
| `defaultMaxClaims` | `3` | Integer `>= 1` | Default claim limit per player |
| `maxClaimWidth` | `64` | Integer `>= 1` | Maximum X width for a claim |
| `maxClaimDepth` | `64` | Integer `>= 1` | Maximum Z depth for a claim |
| `claimGapEnforced` | `false` | `true` or `false` | Whether a minimum gap between claims is enforced |
| `claimGapMinDistance` | `10` | Integer `>= 0` | Minimum spacing between claims when gap enforcement is enabled |
| `claimExpiryDays` | `0` | Integer `>= 0` | Inactivity expiry window in days; `0` disables expiry |
| `notificationsEnabled` | `true` | `true` or `false` | Whether offline admin notifications are queued and delivered |
| `notificationRetentionDays` | `30` | Integer `>= 1` | How long queued offline notices are kept before pruning |
| `wandRemoveConfirmSeconds` | `5` | Integer `>= 1` | Confirmation window for wand-based claim removal |
| `commandRemoveConfirmSeconds` | `10` | Integer `>= 1` | Confirmation window for `/claim remove` |
| `wandSelectionRangeBlocks` | `30` | Integer `>= 0` | Claim wand selection and preview range; `0` uses the maximum supported range |

### Ops settings

| Setting | Default | Acceptable values | What it controls |
| --- | --- | --- | --- |
| `auditLogEnabled` | `false` | `true` or `false` | Whether audit events are written to the Safe Zone audit log |
| `mirrorAuditToServerLog` | `false` | `true` or `false` | Whether audit events are also mirrored to the main server log |
| `createDataBackups` | `false` | `true` or `false` | Whether Safe Zone creates backups before replacing data/config files |
| `recoverFromBackupOnLoadFailure` | `false` | `true` or `false` | Whether Safe Zone tries to recover from a backup if a load fails |

</details>

<details>
  <summary><strong>Stored files</strong></summary>

| Platform | Files |
| --- | --- |
| Fabric | `claims.json`, `config.json`, `player_limits.json`, `starter_kit_recipients.json`, `notifications.json`, `safe-zone_audit.log` |
| Paper | `config.json`, `data\claims.json`, `data\player_limits.json`, `data\starter_kit_recipients.json`, `data\notifications.json`, `logs\audit.log` |

</details>

## Protection coverage

Safe Zone currently protects:

- Block breaking
- Block placement attempts
- Bucket placement and use targets
- Fluid spread into protected claims from outside or a different claim
- General block use inside claims
- Minecart interaction in claims
- Fire spread into claims
- Explosion block damage in claims
- Explosion-created fire in claims
- Paintings, item frames, minecarts, boats, and armor stands from explosion damage or movement
- Trusted, owner, and admin-bypass players from explosion damage and knockback inside claims
- Claim-wand interactions without hoe tilling or durability use
- **Paper only:** Axiom editing inside owned/trusted claims when AxiomPaperPlugin is installed and the player has `safezone.axiom`
- **Paper only:** FAWE / WorldEdit edit sessions inside owned/trusted claims when FastAsyncWorldEdit or WorldEdit is installed

Not implemented yet:

- Piston movement protection
- Enderman grief prevention
- General non-explosion entity-damage protection

## Known limitations

- **Paper corner preview:** after the second click for claim creation or resize, the gold corner block may not appear immediately until you move, re-enter the claim, or switch off and back onto the wand.
- **FAWE "Operation completed" outside claims:** when a player's WorldEdit selection is entirely outside their claim areas, FAWE's region mask silently filters all block changes to zero — no blocks are modified — but FAWE still reports "Operation completed (N)". This is a limitation of FAWE's batch-level mask system, which cannot signal an early cancellation when a selection falls wholly outside allowed regions.

## Local development

Run these from the repository root on Windows:

```powershell
.\gradlew.bat build
.\gradlew.bat check
.\gradlew.bat test
```

### Run locally

```powershell
.\gradlew.bat runServer
.\gradlew.bat runClient
.\gradlew.bat runDatagen
.\gradlew.bat runPaperServer
```

- Fabric runtime folders use `run\fabric\`
- Paper runtime folders use `run\paper\`

Module tasks are also available:

```powershell
.\gradlew.bat :fabric:runServer
.\gradlew.bat :fabric:runClient
.\gradlew.bat :fabric:runDatagen
.\gradlew.bat :paper:runServer
```

### Clean local runtime folders

```powershell
.\gradlew.bat cleanRunFabric
.\gradlew.bat cleanRunPaper
.\gradlew.bat cleanRun
```

- `cleanRunFabric` deletes `run\fabric\`
- `cleanRunPaper` deletes `run\paper\`
- `cleanRun` deletes both

## Build outputs

| Output | Path |
| --- | --- |
| Fabric runtime jar | `fabric\build\libs\safe-zone-<version>.jar` |
| Paper runtime jar | `paper\build\libs\safe-zone-paper-<version>.jar` |

CI and release automation publish the runtime Fabric and Paper jars by default.
