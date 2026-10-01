---
title: IDE project tooling
description: Expose compiled scenario definitions and their runtime inputs to IDE clients through the project model.
---

Use project tooling to make a module's scenario definitions available in
[IntelliJ IDEA](../../intellij/index.md). The build supplies the source roots, generated definition names,
runtime dependencies, artifacts, and execution properties. IDE sync imports the declaration;
loading scenarios prepares the runtime and evaluates each definition in the isolated tooling JVM.

## Use the standard Anvil source set

The standard Anvil plugin configures project tooling for `src/anvil`. Every compiled
`AnvilScenarioDefinition` in that source set is indexed automatically; there is no definition list
to maintain in `build.gradle.kts`. Sync the project after changing scenario sources, then refresh
the scenarios in the IDE. Applying the standard plugin declares the IDE target; a class that merely
implements `AnvilScenarioDefinition` without the plugin remains an ordinary class.

The project model is read without compiling sources, loading scenario classes, or resolving the
scenario runtime. Preparation builds the selected classes and artifact producers, resolves the
runtime classpath, and selects the project's Java launcher. Listing prepared definitions does not
start Minecraft.

The standard plugin also accepts artifacts and engine settings directly. For Paper definitions,
configure repositories from [Installation](../../../getting-started/installation/index.md), keep all
Anvil versions aligned, and register the produced plugin through the same `anvil` block:

```kotlin
plugins {
	java
	id("me.whereareiam.anvil") version "0.0.1"
}

java {
	toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

dependencies {
	testImplementation("me.whereareiam.anvil:default:0.0.1")

	testRuntimeOnly("me.whereareiam.anvil:platform-bukkit-agent:0.0.1")
	testRuntimeOnly("me.whereareiam.anvil:platform-paper-provider:0.0.1")
	testRuntimeOnly("me.whereareiam.anvil:protocol-mcprotocol:0.0.1")
}

anvil {
	acceptEula()
	engine.protocol("mcprotocol")
	artifact("plugin-under-test", tasks.named("jar"))
}
```

Place one or more `AnvilScenarioDefinition` classes at `src/anvil/java` (or another source root
owned by the standard plugin). The EULA call records acceptance for managed servers. Registering
the plugin JAR makes it available as
`AssetSource.artifact("plugin-under-test")`; the scenario must reference it in its workspace to
install it. See [Workspace assets](../../../building-blocks/environments/workspaces/assets/index.md).

The plugin creates the `anvil` source set, compiles it before preparation, and resolves the tooling
runtime automatically. `anvilTooling` is an internal preparation task exposed for IDE sync and
manual inspection; it does not start Minecraft.

## Configure execution inputs

| Declaration member | Purpose |
|---|---|
| `engine { ... }` | Configure execution provider, protocol, cache/work directories, timeouts, parallelism, and other engine options |
| `acceptEula()` | Record EULA acceptance for managed servers |
| `artifact(name, notation)` | Register one file or task output for scenario workspaces |

Module labels come from the imported build name and module path. Configure optional display names
on the [scenarios and components](../../../building-blocks/environments/definitions/index.md#describe-the-scenarios-purpose)
when their names should describe the behavior being tested.

Artifact registration also supplies `anvil.artifact.<name>` to the scenario JVM. Use [engine
property names](../../../building-blocks/environments/configuration/engine/index.md) for engine
options. Keep authentication credentials in the provider's private account store, not in exported
files.

## Prepare a launch without starting it

```shell
./gradlew anvilTooling
```

The default output is `build/anvil/tooling.json`. It contains the tooling Java executable,
resolved classpath, generated definition names, named artifacts, and scenario JVM properties. It is a
machine-local generated file, not a checked-in project configuration.

Tooling clients may choose an absolute output path:

```shell
./gradlew anvilTooling --output-file=/absolute/path/to/tooling.json
```

In a multi-project build, use the declaring module's task path. IntelliJ obtains that path from
the imported model and owns its temporary output file; users do not enter task or manifest paths
in the tool window. The declaration and prepared-launch boundaries are described in
[Project tooling architecture](../../../contributing/architecture/tooling/index.md).
