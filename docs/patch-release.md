# Patching and Shipping an Older Release

How to back-port a fix to an older release line and ship a new patch version.

## How releasing works in this repo

Releases are **100% git-tag-driven**. The workflow
[`.github/workflows/flow-deploy-release-artifact.yaml`](../.github/workflows/flow-deploy-release-artifact.yaml)
fires on any pushed tag matching `v[0-9]+.[0-9]+.[0-9]+-?*`. On a tagged push it:

1. Parses the version straight from the tag name (using `semver`).
2. Runs `./gradlew versionAsSpecified -PnewVersion=<tag-version>` — the version comes
   from the **tag**, not from `pbj-core/version.txt` (which just holds a
   `…-SNAPSHOT` placeholder).
3. Runs `assemble`, then `publishAggregationToCentralPortal` → **Maven Central**, and
   `publishPlugins` → **Gradle Plugin Portal**. Both run **only for non-prerelease tags**.

The trigger does **not** care which branch the tag is on — only that the tag matches the
pattern. That is what makes back-porting possible.

## Caveat: this repo has no release branches

Every release tag (`v0.13.x`, `v0.14.x`, `v0.15.x`, …) sits **directly on linear `main`**.
There are **no release/maintenance branches**. So "patching an old release" means creating
a branch yourself off the old tag, since you cannot commit to an existing tag.

## Steps to patch & ship an older release

Worked example: the latest release is `v0.15.10`, but you need to patch the `v0.14` line
**starting from `v0.14.5`** — for instance because `v0.14.6` introduced a regression you
do not want to carry forward — and ship the result.

> **Pick the new version carefully.** You are branching off `v0.14.5`, but `v0.14.6` is
> already a published tag and **cannot be reused**. Tags are immutable and `v0.14.6`'s
> artifacts are already on Maven Central. The next free patch number on this line is
> **`v0.14.7`**, so that is the version you will ship — even though the code is based on
> `v0.14.5`, not `v0.14.6`. If you instead want to supersede `v0.14.6`, the new release
> still has to be `v0.14.7`; you cannot overwrite `v0.14.6`.

### 1. Branch off the old release tag

```bash
git fetch --tags
git checkout -b release/0.14 v0.14.5
```

### 2. Apply the fix

If the fix already exists on `main`, cherry-pick it. Find the commit SHA first:

```bash
# Find the fix commit on main (e.g. by PR number or message)
git log --oneline main | grep -i "<search term or PR #>"

# Cherry-pick it onto the release branch (-x records the original SHA in the message,
# -s adds a DCO Signed-off-by line)
git cherry-pick -x -s <sha-of-fix-on-main>
```

For a range or multiple commits:

```bash
git cherry-pick -x -s <oldest-sha>^..<newest-sha>
```

If there are conflicts:

```bash
# resolve files, then:
git add <resolved-files>
git cherry-pick --continue
```

If the fix does **not** exist on `main`, commit it directly on the branch instead:

```bash
# ...make your changes...
git commit -s -m "fix: <description>"
```

### 3. Verify the build

```bash
( cd pbj-core && ./gradlew build qualityGate )
( cd pbj-integration-tests && ./gradlew build )
```

### 4. Push the branch

Keeps the commit reviewable and preserved.

```bash
git push -u origin release/0.14
```

### 5. Tag the new patch version and push the tag — this is what ships it

`v0.14.6` already exists, so the new tag is the next free patch number, `v0.14.7`:

```bash
# Sanity-check that the tag name isn't already taken before you push
git tag -l 'v0.14.*'

git tag v0.14.7
git push origin v0.14.7
```

Pushing `v0.14.7` triggers the deploy workflow, which builds at version `0.14.7` and
publishes to Maven Central and the Gradle Plugin Portal.

## Notes

- **No need to touch `version.txt`** — the workflow overrides it from the tag.
- **Prerelease / RC tags do not publish.** A tag like `v0.14.7-rc1` is treated as a
  prerelease (`prerelease=true`) and is **skipped** by the Maven Central and Plugin Portal
  jobs. Only a clean `vX.Y.Z` tag actually publishes artifacts.
- **GitHub Release object.** The workflow only publishes artifacts; it does not create a
  GitHub Release. If you want release notes, create the release manually:

  ```bash
  gh release create v0.14.7 --notes "Back-port: <description>"
  ```

- **DCO sign-off.** This repo enforces DCO — commits need a `Signed-off-by` line
  (`git commit -s`, `git cherry-pick -s`).

## Before you run this

1. Decide whether maintainers want a permanent `release/0.14` branch or are fine with a
   throwaway branch that only exists to host the tag.
2. Confirm you have push access for tags — the publish secrets live in CI, so **anyone who
   can push a matching tag triggers a real publish**. If you don't, route the tag through a
   maintainer.
