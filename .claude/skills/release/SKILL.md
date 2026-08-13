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

## 6. Post-release

- Closed issues referenced in the notes get a comment only if the fix needs
  user action (e.g. new config); otherwise the auto-close from the PR suffices.
- The next feature commit bumps `mod.version` (this repo does not use -SNAPSHOT
  dev versions; version bumps land with the first change of the next cycle).
