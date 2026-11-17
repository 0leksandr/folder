# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

IntelliJ Platform plugin (Kotlin) adding one editor action, **Fold to Defaults**, which resets a file's folding to the state a file gets on first open per *Settings | Editor | General | Code Folding*. It depends only on `com.intellij.modules.platform` and targets IDE build 243+ with no upper bound. The plugin is registered in `src/main/resources/META-INF/plugin.xml` (action `FoldToDefaults`, added to `FoldingGroup` after `ExpandAllToLevel`).

## Commands

Keep Gradle state inside the repo (git-ignored, ~6 GB after first build) and run Gradle with JDK 21+:

```sh
export GRADLE_USER_HOME="$PWD/.gradle-home"
./gradlew test                                   # run all tests
./gradlew test --tests '*FoldToDefaultsActionTest.testName'   # single test
./gradlew buildPlugin                            # build/distributions/fold-to-defaults-<version>.zip
./gradlew runIde                                 # sandbox IDE for manual testing
./gradlew generateUpdatePluginsXml -PpluginVersion=X.Y.Z -PdownloadBaseUrl=<dir the zip is served from>
```

Releases are done by pushing a `vX.Y.Z` tag: `.github/workflows/release.yml` builds with that version
(`-PpluginVersion`; `build.gradle.kts` only holds the local default), generates `updatePlugins.xml` with the
release's download URL (`-PdownloadBaseUrl`, required) and attaches both files to a GitHub release.

No lint task is configured.

## Architecture

The whole implementation is `FoldToDefaultsAction.kt`. Key design points that aren't obvious from a skim:

- It does **not** rebuild folding descriptors or match them to regions by range. The platform already computes each region's default on every folding update (across all PSI roots, so multi-language files work) and stores it; the action reads it via `CodeFoldingManager.isCollapsedByDefault(FoldRegion)` and applies it in one `runBatchFoldingOperation`. Hence no PSI access, no read action, and it is `DumbAware`.
- Regions where `isCollapsedByDefault` returns `null` (e.g. created by *Fold Selection*) and regions with `shouldNeverExpand()` are left untouched.
- Regions in a group are set together: the platform expands the whole group if any member is expanded by default.
- `update()` runs on BGT and only checks data-context entries (editor and PSI file present).

`SettingsHint.kt` shows a one-time, application-level notification (a project-level one appears only in that project's window and was missed with several projects open; the show-once flag is in application-level `PropertiesComponent`) pointing to the Code Folding settings page, since some languages (e.g. PHP) ignore the generic options and have their own section there. It fires from a `ProjectActivity` (install + restart) and a `DynamicPluginListener` (install without restart, where startup activities don't run). The page is selected by configurable id `editor.preferences.folding`, because the `String` overload of `showSettingsDialog` matches the localized display name.

## Build/test notes

- Bytecode is pinned to Java 21 (the oldest supported IDE runtime is JBR 21) regardless of the JDK running Gradle.
- Tests (`BasePlatformTestCase`) use the bundled Java plugin (`testBundledPlugin("com.intellij.java")`) to get a language with real folding builders, and toggle `JavaCodeFoldingSettings` (restored in `tearDown`). `build.gradle.kts` sets `idea.suppressed.plugins.id=com.intellij.modules.ultimate` for tests because the unified IDEA distribution's ultimate-only startup activities can't be instantiated in light tests.
- Gradle configuration cache and build cache are enabled in `gradle.properties`; the Kotlin stdlib is not added as a dependency (the platform provides it).
