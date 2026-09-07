---
title: Installation
description: Add Anvil's Gradle plugins, protocol provider, and platform provider to a test project.
---

# Installation

Apply Anvil to the project that owns your scenarios. The plugin supplies the `src/anvil` source
set, the `anvil` extension, and the tasks used to run scenarios. Your existing `src/main` and
`src/test` source sets continue to build normally.

## Add the repositories

In `settings.gradle.kts`, add the Anvil registry to plugin and dependency resolution. Keep
`mavenLocal()` when testing a locally published Anvil build.

```kotlin
pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        maven("https://registry.whereareiam.me/maven/packages")
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        maven("https://registry.whereareiam.me/maven/packages")
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.opencollab.dev/main/")
        maven("https://repo.opencollab.dev/maven-snapshots/")
    }
}
```

Use a published Anvil version for a consumer project. The current development version in this
repository is `0.0.1`; keep all Anvil plugin units on the same version.

## Apply the plugins

The umbrella plugin is the shortest path to a complete setup. It supplies automated JUnit
execution, foreground scenario execution, and the aggregate built-in capability unit. Platform
units are always explicit because they add the providers and agents for the platform you run.

```kotlin
plugins {
    java
    id("me.whereareiam.anvil") version "0.0.1"
    id("me.whereareiam.anvil.platform.paper") version "0.0.1"
}
```

Choose a smaller installation when the project needs only one workflow or selected capabilities:

| Need | Plugin |
| --- | --- |
| Automated JUnit scenarios | `me.whereareiam.anvil.junit` |
| Foreground scenario listing and execution | `me.whereareiam.anvil.scenarios` |
| Both workflows and all built-in capabilities | `me.whereareiam.anvil` |

With the scenarios plugin, apply capability units such as
`me.whereareiam.anvil.capability.session` or `me.whereareiam.anvil.capability.messages` explicitly.
Apply one platform unit for each server or proxy platform in your scenarios:
`paper`, `spigot`, `velocity`, or `bungeecord`.

## Add a protocol provider

Anvil discovers protocol providers from the `anvilProtocols` dependency bucket. The bundled
MCProtocolLib provider is available through the typed `anvil.protocols.mcprotocol` coordinate:

```kotlin
dependencies {
    add("anvilProtocols", anvil.protocols.mcprotocol)
}

anvil {
    protocol("mcprotocol")
}
```

The `protocol` selection is optional when exactly one provider is installed. Set it explicitly
when the runtime classpath contains more than one provider. Protocol providers also own optional
online-player authentication; see [Authentication](../../running-environments/authentication/index.md).

## Register the artifact under test

Register the JAR that Anvil must install into the managed process. A single-project plugin uses a
task provider:

```kotlin
anvil {
    artifact("plugin-under-test", tasks.named("jar"))
}
```

In a multi-project build, register another project's artifact instead:

```kotlin
anvil {
    artifact("plugin-under-test", project(":plugin"))
}
```

The name may contain letters, digits, `.`, `_`, and `-`. A scenario refers to the same name with
`AssetSource.artifact("plugin-under-test")` or `Distribution.artifact("plugin-under-test")`.

## Accept the EULA

Server scenarios require an explicit acknowledgement:

```kotlin
anvil {
    acceptEula()
}
```

This records the build configuration. It does not grant permission to redistribute Minecraft or
accept the EULA for another person.

## Authentication tasks

Every Anvil Gradle installation provides `anvilLogin` and `anvilLogout` for an optional online
authentication profile exposed by the selected protocol provider:

```shell
./gradlew anvilLogin --auth-profile=main
./gradlew anvilLogout --auth-profile=main
```

The profile is stored in the Anvil cache owned by the local user. Do not put account tokens in
Gradle arguments, project files, environment variables, or logs. Offline scenarios do not need
these tasks.

## Verify the installation

Compile the scenario source set and list the available tasks:

```shell
./gradlew compileAnvilJava tasks --group anvil
```

If Gradle cannot resolve a plugin or provider, first check that every Anvil plugin uses the same
version and that the registry appears in both repository blocks. If a scenario starts but cannot
resolve a platform, apply the matching platform unit and check that its platform is declared in the
scenario. Continue with [Run your first scenario](../first-scenario/index.md).
