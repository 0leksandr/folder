# Fold to Defaults

An IntelliJ Platform plugin that adds one editor action, **Fold to Defaults**. It resets the folding of the
current file to the state a file gets on first open according to
*Settings | Editor | General | Code Folding*, discarding manual expand/collapse changes.

JetBrains IDEs remember manual folding state per file and have no built-in way to reapply the defaults
([IJPL-791](https://youtrack.jetbrains.com/issue/IJPL-791)). "Collapse All" + "Expand All to Level N" is not
equivalent: it also collapses loops, lambdas and other inner blocks.

- Every region with a language-defined default is set to it: collapsed if the language says "collapsed by
  default" (this reflects your Code Folding settings, e.g. *Method bodies*), otherwise expanded.
- Regions without a default, such as those created with *Fold Selection*, are left alone.
- Regions of a group (shared placeholder) are expanded or collapsed together.
- Works in every JetBrains IDE (depends only on `com.intellij.modules.platform`), IDE build 243 (2024.3) and newer.

The action is in **Code | Folding** and in the editor context menu's **Folding** submenu.

## Keyboard shortcut

No shortcut is assigned by default. Bind one in *Settings | Keymap*: search for **Fold to Defaults**
(it is under *Main menu | Code | Folding*), then right-click it and choose *Add Keyboard Shortcut*.

## Build

Requires a JDK 21 or newer to run Gradle (the plugin is compiled for Java 21).

Keep all Gradle state (wrapper distribution, dependency caches, the downloaded IDE) inside the repository
instead of `~/.gradle`:

```sh
export GRADLE_USER_HOME="$PWD/.gradle-home"   # git-ignored; about 6 GB after the first build
```

```sh
./gradlew test           # run the tests
./gradlew buildPlugin    # build/distributions/fold-to-defaults-<version>.zip
./gradlew runIde         # start a sandbox IDE with the plugin for manual testing
```

## Install

### From GitHub (recommended, with automatic updates)

Releases are published on [GitHub Releases](../../releases), and the IDE can install and update the plugin
from there through a custom plugin repository:

1. Copy the address of the link [updatePlugins.xml](../../releases/latest/download/updatePlugins.xml)
   (right-click, *Copy Link Address*).
2. Add it in *Settings | Plugins | ⚙ | Manage Plugin Repositories…*.
3. Search for **Fold to Defaults** on the *Marketplace* tab, and install it.

New releases then show up as regular plugin updates.

### From disk (local build)

Build the zip (see [Build](#build)), then *Settings | Plugins | ⚙ | Install Plugin from Disk…*, and pick the zip
from `build/distributions/`.

A copy installed from disk gets no updates. If the GitHub repository is also configured, the IDE shows
"Unable to identify where Fold to Defaults was installed from": use the notification's
*Choose where to get updates from* link and select that repository.

## Releasing

Push a tag of the form `vX.Y.Z`:

```sh
git tag v1.0.1 && git push origin v1.0.1
```

`.github/workflows/release.yml` then runs the tests, builds the zip with the tag's version (the version in
`build.gradle.kts` is only the default for local builds), generates `updatePlugins.xml` with
`./gradlew generateUpdatePluginsXml`, and attaches both files to a new GitHub release. The IDE only offers an
update when the version in `updatePlugins.xml` is higher than the installed one. The repository must be public,
because the IDE downloads without authentication.

## How it works

The platform computes the default state of each fold region on every folding update, across all PSI roots of
the file (so multi-language files work), and stores it on the region. The action reads that value through
`CodeFoldingManager.isCollapsedByDefault(FoldRegion)` and applies it in a single batch folding operation.
It does not rebuild descriptors itself, so it needs no PSI access or read action, and works in dumb mode.
