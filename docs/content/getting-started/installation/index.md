---
title: Installation
description: Configure repositories, Gradle plugins, protocol support, and the live-test source set.
---

Apply Anvil to the Gradle project that will contain your scenarios and journeys. This can be an
existing plugin project or a dedicated Java test project. The setup below supplies JUnit, the built-in
capabilities, Paper support, and the MCProtocol backend used by the [first test](../first-test/index.mdx).

## Configure repositories

Add these blocks to `settings.gradle.kts`. Keep any repositories your application already needs.

```kotlin
pluginManagement {
	repositories {
		gradlePluginPortal()
		maven("https://registry.whereareiam.me/maven/packages")
		maven("https://repo.opencollab.dev/main/")
		maven("https://repo.opencollab.dev/maven-snapshots/")
	}
}

dependencyResolutionManagement {
	repositories {
		mavenCentral()
		maven("https://registry.whereareiam.me/maven/packages")
		maven("https://repo.opencollab.dev/main/")
		maven("https://repo.opencollab.dev/maven-snapshots/")
	}
}
```

These examples use Anvil version `0.0.1`. Use one available Anvil version consistently across the
framework, platform units, and capability units. For a locally published checkout, add `mavenLocal()`
to both repository lists and use that checkout's publication version. See
[building and publication](../../contributing/publishing/index.md).

## Configure the build

Use this in `build.gradle.kts`:

```kotlin
plugins {
	java
	id("me.whereareiam.anvil") version "0.0.1"
	id("me.whereareiam.anvil.platform.paper") version "0.0.1"
}

dependencies {
	add("anvilProtocols", anvil.protocols.mcprotocol)
}

tasks.withType<JavaCompile>().configureEach {
	options.release.set(21)
}

anvil {
	acceptEula()
	protocol("mcprotocol")
}
```

Calling `acceptEula()` records your acceptance of the [Minecraft EULA](https://www.minecraft.net/eula).
Review it before using this configuration.

The umbrella plugin adds JUnit and foreground scenario tooling, together with the built-in
capabilities. The Paper unit adds Paper provisioning and its platform agent. The protocol dependency
supplies the native players. Applying Anvil does not package your plugin into a server automatically;
[register its built JAR as an asset](../../building-blocks/environments/workspaces/assets/index.md) when you are ready
to test its behavior.

## Choose source locations

```text
src/
├── main/             Your application or plugin
├── test/             Ordinary unit tests
└── anvil/
    ├── java/         Scenarios and live JUnit tests
    └── resources/    Test resources
```

Run `./gradlew tasks --group verification` to confirm that `anvilTest` is available. This lists tasks
without starting a server. Then follow the [first test](../first-test/index.mdx).

## Smaller installations

Use `me.whereareiam.anvil.junit` for the automated JUnit workflow or
`me.whereareiam.anvil.scenarios` for foreground environments. With either smaller installation,
choose [capability units](../../building-blocks/players/capabilities/index.md) explicitly and keep the
required platform and protocol providers installed. The [Gradle integration](../../integrations/gradle/index.md)
lists the plugin and dependency choices.
