# Safe Zone

<p align="center">
  <a href="https://fabricmc.net/" target="_blank"><img alt="fabric" height="56" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/fabric_vector.svg"></a>
  <a href="https://papermc.io/" target="_blank"><img alt="paper" height="56" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/paper_vector.svg"></a>
</p>

----

![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11%20%7C%2026.1%20%7C%2026.2-3C8527)
![Mod Loader](https://img.shields.io/badge/Mod%20Loader-Fabric%20|%20Paper-blue.svg)
![Environment](https://img.shields.io/badge/Environment-Server%20Only-4051b5)
![Java](https://img.shields.io/badge/Java-21%2B%20(1.21.11)%20%2F%2025%20(26.x)-e76f00)
![License MIT](https://img.shields.io/badge/License-MIT-lightgrey)

Safe Zone is a **server-only** Fabric and Paper land-claim mod for **Minecraft 1.21.11, 26.1, and 26.2**. Players protect builds with a configurable vanilla claim wand (**Golden Hoe** by default), admins manage claims with `/sz`, and **unmodded clients can still join and use the full workflow**.

## Highlights

- Server-side land claims for Fabric and Paper servers
- No client mod required
- First-join starter wand using a vanilla item
- In-world claim creation, resizing, trust management, and removal — with outline/preview visualization, no client mod needed
- Player self-service tools through `/claim`
- Admin moderation and management tools through `/sz`
- **Paper only:** optional claim-restricted editing with Axiom and FastAsyncWorldEdit / WorldEdit

## Requirements

Each release ships **one jar per Minecraft version** — pick the file matching your server (the `+26.1.2` jars cover all of 26.1.x):

| Your server | Jar | Java |
| --- | --- | --- |
| Minecraft 26.2 | `...+26.2.jar` | 25 |
| Minecraft 26.1 / 26.1.1 / 26.1.2 | `...+26.1.2.jar` | 25 |
| Minecraft 1.21.11 | `...+1.21.11.jar` | 21+ |

Fabric servers additionally need **Fabric Loader** and **Fabric API**.

## Install

1. **Fabric:** put the Safe Zone Fabric jar matching your Minecraft version in `mods/`, along with **Fabric API**.
   **Paper:** put the Safe Zone Paper jar matching your Minecraft version in `plugins/`.
2. Start the server once to generate the Safe Zone data/config files (Fabric: `<world>/safe-zone/`, Paper: `plugins/SafeZone/`).
3. Join normally. Players do **not** need anything installed on the client.

## Player quick start

1. Join the server and receive the configured claim wand once (default: **Golden Hoe**).
2. Right-click one block with the wand to save corner 1.
3. Right-click a second block with the wand to create the claim.
4. Stand inside your claim and:
   - **Shift + left-click** with the wand to open the build-access (trust) menu
   - **Shift + right-click** with the wand to remove the claim after confirming
   - **Right-click one of your claim corners** to start resizing
5. Use `/claim` for claim lists, trust helpers, and claim info — and `/claim show` to keep your claim boundaries visible without holding the wand.

## Commands

### Player commands

`/claim` and `/claims` are aliases.

| Command | Purpose |
| --- | --- |
| `/claim` | Show help |
| `/claim help` | Show command help |
| `/claim status` | Show your claim count and limit |
| `/claim list [page]` | List claims you own |
| `/claim trusted [page]` | List claims where you are trusted |
| `/claim here` | Show the claim at your position |
| `/claim info [claimId]` | Show details for an accessible claim |
| `/claim trust [claimId]` | Open the trust menu for one of your claims |
| `/claim remove <claimId>` | Remove one of your claims after confirmation |
| `/claim show` | Toggle always-on display of your claim boundaries |

`/claim remove` must be run twice within the configured confirmation window (10 seconds by default).

### Admin commands

`/sz` and `/safezone` are aliases and require operator or equivalent admin access.

| Command | Purpose |
| --- | --- |
| `/sz` | Show help |
| `/sz help` | Show command help |
| `/sz status` | Show Safe Zone status summary |
| `/sz list [page]` | List loaded claims |
| `/sz list owner <player> [page]` | List claims owned by one player |
| `/sz list trusted <player> [page]` | List claims that trust one player |
| `/sz info [claimId]` | Show claim details |
| `/sz here` | Show the claim at your position (**Paper only**; on Fabric use `/sz info`) |
| `/sz remove <claimId>` | Remove one claim |
| `/sz removeall <player> confirm` | Remove every claim owned by a player |
| `/sz transfer <claimId> <player>` | Transfer ownership |
| `/sz trust <claimId> <player>` | Grant build access |
| `/sz untrust <claimId> <player>` | Remove build access |
| `/sz tp <claimId>` | Teleport to a claim |
| `/sz notifications [purge [confirm]]` | Review or purge queued offline admin notices |
| `/sz inspect [claimId]` | Inspect claims in-world or by ID |
| `/sz reload` | Reload config and data |
| `/sz givewand [player]` | Give a claim wand |
| `/sz limits <player> [maxClaims\|clear]` | Manage claim limit overrides |

## Configuration and data

Safe Zone stores gameplay and ops settings together in one pretty-printed `config.json`, grouped into `gameplay` and `ops` sections on first load.

| Platform | Config | Runtime data | Audit log |
| --- | --- | --- | --- |
| Fabric | `<world>/safe-zone/config.json` | `<world>/safe-zone/` | `<world>/safe-zone/safe-zone_audit.log` |
| Paper | `plugins/SafeZone/config.json` | `plugins/SafeZone/data/` | `plugins/SafeZone/logs/audit.log` |

### Gameplay defaults

- `claimWandItemId` — `minecraft:golden_hoe`
- `starterKitEnabled` — `true`
- `dropStarterKitWhenInventoryFull` — `true`
- `defaultMaxClaims` — `3`
- `maxClaimWidth` / `maxClaimDepth` — `64` / `64`
- `claimGapEnforced` — `false` (`claimGapMinDistance` — `10` blocks when enabled)
- `claimExpiryDays` — `0` (disabled); when set, expiry is **login-driven**: logging in before expiry refreshes your claims, logging in after it removes expired ones
- `notificationsEnabled` — `true`; offline admin notices are queued and delivered on join (`notificationRetentionDays` — `30`)
- `windChargeKnockbackInClaims` — `true`; set `false` to suppress wind-charge knockback for players standing inside claims (the wilderness is never affected)
- `wandRemoveConfirmSeconds` — `5`
- `commandRemoveConfirmSeconds` — `10`
- `wandSelectionRangeBlocks` — `30`; `0` uses the maximum supported range

### Ops defaults

- `auditLogEnabled` — `false`
- `mirrorAuditToServerLog` — `false`
- `createDataBackups` — `false`
- `recoverFromBackupOnLoadFailure` — `false`

### Rules and files

- Claims are **Overworld-only** and **full-height columns**
- All JSON state saves atomically; config JSON stays pretty-printed, runtime state is compact
- `/sz reload` reloads `config.json` and data

## Protection coverage

Safe Zone currently protects:

- Block breaking and block placement attempts
- Bucket placement and use targets
- Fluid spread into protected claims from outside or a different claim
- General block use inside claims
- Minecart interaction in claims
- Fire spread into claims
- Explosion block damage and explosion-created fire in claims
- Paintings, item frames, minecarts, boats, and armor stands from explosion damage or movement
- Trusted, owner, and admin-bypass players from explosion damage and knockback inside claims — **wind charges are exempt** so movement works everywhere (configurable via `windChargeKnockbackInClaims`)
- Claim-wand interactions without hoe tilling or durability use
- **Paper only:** Axiom editing restricted to owned/trusted claims (requires AxiomPaperPlugin and `safezone.axiom`)
- **Paper only:** FAWE / WorldEdit edit sessions restricted to owned/trusted claims

Not implemented yet:

- Piston movement protection
- Enderman grief prevention
- General non-explosion entity-damage protection
