---
name: release
description: Cut a Safe Zone release - finalize the CHANGELOG in end-user voice, verify preflight, tag vX.Y.Z, and let the release workflow build and publish the per-version jars to a GitHub release. Use when the user asks to release, publish, or ship a version.
---

# Safe Zone release process

Releases are driven by tags: pushing `vX.Y.Z` triggers `.github/workflows/release.yml`,
which builds every Minecraft version in clean CI, extracts that version's CHANGELOG
section as the release notes, and publishes a GitHub release with all six runtime jars
(`SafeZone-{Fabric,Paper}-X.Y.Z+{mc}.jar`). Your job is everything the workflow can't
judge: the changelog wording, preflight sanity, and the tag itself.

## 1. Preflight — all must hold before anything else

- On `main`, working tree clean, synced with `origin/main` (`git fetch` + compare).
- CI green on the `main` HEAD commit (`gh run list --branch main --limit 1`).
- Stonecutter state canonical: `./gradlew "Reset active project"` then `git diff --exit-code`.
- `mod.version` in `gradle.properties` equals the version being released
  (the release workflow hard-fails on mismatch). If it needs bumping, that bump is
  part of the release commit.
- `CHANGELOG.md` has a `## <version>` section (see step 2).

## 2. Finalize the CHANGELOG entry — end-user voice

The section becomes the public release notes verbatim. Audience: **server admins and
players**, not contributors. Rules:

- Lead with what changed in-game or for administration: gameplay fixes, new config
  options, supported Minecraft versions, which jar to download.
- Say "wind charges knock players back again", not "reworked ServerExplosionMixin".
- Name issue numbers for fixes (`#5`, `#6`) — they link automatically on GitHub.
- Include a **Compatibility notes** block: Minecraft versions per jar, Java
  requirement (21 for 1.21.11 jars, 25 for 26.x), config/data migration impact
  (usually "existing configs and claims work unchanged"), and any known upstream
  gaps (e.g. FAWE builds).
- Internal changes (build system, tests, refactors) are dropped entirely — the
  changelog is end-user facing only. No "Internals" sections.
- Replace any `(unreleased)` marker with the release date:
  `## X.Y.Z — YYYY-MM-DD`.

Show the rewritten entry to the user and get approval before committing.

Also sweep `README.md` for staleness against this release: supported-version
badge and compatibility table (including per-jar Java requirements), install
instructions (jar naming/selection), and anything describing behavior that
changed this cycle.

## 3. Commit and verify

- Commit the changelog (and any version bump) to `main` as `release: vX.Y.Z`.
- Push, then wait for the CI build on that commit to go green before tagging —
  never tag an unverified commit.

## 4. Tag — this is the release trigger

```
git tag vX.Y.Z
git push origin vX.Y.Z
```

## 5. Watch and verify

- Watch the Release workflow run (`gh run watch`).
- When it finishes, verify the release page: correct notes, exactly six jars
  (3 Fabric + 3 Paper, one per Minecraft version), tag marked Latest.
- Link the release to the user.

## 6. Modrinth publication

The user uploads to Modrinth manually (until mod-publish-plugin automation
lands). Prepare everything for them:

**Project description**: if the README or shipped behavior changed this cycle,
update `docs/MODRINTH.md` (the tracked copy of the Modrinth project body,
condensed from README.md — verify command tables against the actual command
registrars, not just the README) and hand it over for pasting.

**Version entries** — one per jar, six total. Provide a metadata table:

| Jar | Loader | Game versions | Version number |
| --- | --- | --- | --- |
| `SafeZone-Fabric-X.Y.Z+<mc>.jar` | Fabric | exactly `<mc>` (mixins pin the patch version; `+26.1.2` covers 26.1–26.1.2) | `X.Y.Z+<mc>` |
| `SafeZone-Paper-X.Y.Z+<mc>.jar` | Paper | the whole line the jar's `api-version` + compile surface supports (e.g. `+1.21.11` → all of 1.21.x — verified by compiling against the line's oldest paper-api) | `X.Y.Z+<mc>` |

All channel **Release**, environment **server-only**; Fabric entries declare
**Fabric API** as a required dependency. Jars come from the GitHub release
assets, never a local build.

**Per-jar changelogs** — derive from the CHANGELOG entry; do NOT paste it
verbatim. Rules:

- Keep only lines relevant to that jar's Minecraft version (a "Minecraft 26.2
  support" bullet belongs only on the 26.2 jars).
- Strip issue references like `(#5, #6)` — they don't link on Modrinth and
  mean nothing to its audience.
- State only that jar's Java requirement.
- Mind the framing: a jar for a newly supported Minecraft version is a
  "first Safe Zone release for <mc>"; a jar for a line that skipped releases
  summarizes everything "since <last version shipped for that line>" (check
  the release history — e.g. 1.21.11 skipped 1.4.0).

Show the per-jar changelogs to the user before they upload.

## 7. Post-release

- Closed issues referenced in the notes get a comment only if the fix needs
  user action (e.g. new config); otherwise the auto-close from the PR suffices.
- The next feature commit bumps `mod.version` (this repo does not use -SNAPSHOT
  dev versions; version bumps land with the first change of the next cycle).
