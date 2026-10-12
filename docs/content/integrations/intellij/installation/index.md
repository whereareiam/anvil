---
title: Installation
description: Install the Anvil plugin from JetBrains Marketplace and match it to your project's Anvil version.
---

The Anvil plugin supports IntelliJ IDEA 2025.2 through 2026.2 (builds `252` to `262.*`) and requires
the bundled Java plugin. Scenario discovery also needs the bundled Gradle plugin enabled. Anvil's
project runtime requires Java 21 or newer; individual Minecraft distributions may provision another JDK.

## Install from Marketplace

1. Open **Settings → Plugins** and select the **Marketplace** tab.
2. Search for **Anvil**, select the plugin by whereareiam, and choose **Install**.
3. Restart the IDE if prompted.
4. Open **View → Tool Windows → Anvil**.

JetBrains documents the general [plugin installation workflow](https://www.jetbrains.com/help/idea/managing-plugins.html).
Pre-release plugin versions are published to the Marketplace EAP channel; add that channel as a
custom plugin repository only when you want to test them.

## Match the project's Anvil version

The plugin runs your project's own Anvil tooling, so the project must apply the standard Anvil
Gradle plugin at a version compatible with the IDE plugin. Installing or updating the IDE plugin
does not change project dependencies.

After changing the project's Anvil version, sync the Gradle project so IntelliJ imports the current
tooling model. If Anvil reports an unsupported tooling protocol, update both the IDE plugin and the
project's Anvil artifacts to matching versions, then sync again. This plugin uses tooling protocol 7.

The [scenario running guide](../scenarios/index.md) continues from here.

## Install a development build

Use a development build to try unreleased changes. Consumer projects then need matching Anvil
artifacts, usually a [local publication](../../../contributing/publishing/index.md#local-publication);
Anvil's own checkout uses project dependencies and needs no publication.

Build the plugin ZIP from an Anvil checkout:

```shell
./gradlew :anvil-integration:integration-intellij:intellij:buildPlugin
```

The ZIP appears under `anvil-integration/integration-intellij/intellij/build/distributions`.
Completed [verification runs](https://github.com/whereareiam/anvil/actions) also retain it as an
`anvil-intellij-plugin-<version>` artifact; extract that download and use the plugin ZIP inside it.

1. Open **Settings → Plugins**.
2. Open the gear menu and choose **Install Plugin from Disk**.
3. Select the plugin ZIP and restart the IDE if prompted.

If IntelliJ rejects the archive, check that you selected the plugin ZIP rather than the outer
workflow artifact, and that your IDE version is within the supported build range.
