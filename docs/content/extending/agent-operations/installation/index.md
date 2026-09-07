---
title: Installing agent operations
description: Package the handler and add it to each target process as a workspace asset.
---

The managed JVM loads operation JARs from `plugins/anvil-agent-extensions`. Adding a host dependency
to `anvilCapabilities` makes it available to Anvil; install the platform handler separately as a
workspace asset.

## Build the JAR

Package the handler, shared operation contract, and implementation dependencies together, or install
the dependency JARs in the same extension directory. Preserve the operation-provider service file
when shading. Do not bundle Anvil agent APIs or the platform SDK; those classes come from the parent
loader supplied by the platform agent.

## Register the artifact

In a multi-project consumer build where `:echo-agent` produces the correctly packaged JAR, add this
to the existing Anvil block:

```kotlin
anvil {
	artifact("echo-agent", project(":echo-agent"))
}
```

Anvil resolves the project artifact and its build dependency. See
[Workspaces](../../../building-blocks/environments/workspaces/index.md) for other artifact sources.

## Declare the workspace asset

The following fragment defines a workspace plan for the target process. It assumes the artifact
registration above and belongs in your scenario definition:

```java
import me.whereareiam.anvil.api.model.workspace.AssetSource;
import me.whereareiam.anvil.api.model.workspace.WorkspaceAsset;
import me.whereareiam.anvil.api.model.workspace.WorkspacePlan;

import java.nio.file.Path;

WorkspaceAsset extension = WorkspaceAsset.builder()
		.group("echo-agent")
		.source(AssetSource.artifact("echo-agent"))
		.target(Path.of("plugins", "anvil-agent-extensions", "echo.jar"))
		.build();
WorkspacePlan workspace = WorkspacePlan.builder().asset(extension).build();
```

Pass `workspace` to `.workspace(workspace)` on the target `MinecraftServer` or `MinecraftProxy`
builder. Include the asset in each process that must handle the operation. If the process already
has a workspace plan, add the asset to that plan so its other declarations remain present. Assets
are installed before provider configuration and process startup.

The platform agent loads JARs in filename order with its own class loader as the parent. The endpoint
owns the extension loader for the running process and closes it during shutdown or failed startup.
Replacing a JAR in a running workspace does not reload the installed operations; restart the process
to load a new handler.

## Verify installation

Run the [Echo capability assertion](../../capabilities/contracts/index.md) against a named process
with a platform agent. If the operation is unavailable, inspect that process's `anvil-console.log`
and the packaged service file. A class-loading failure usually requires checking the contract JAR,
implementation dependencies, or duplicate copies of parent-provided APIs.

Keep the host's operation-contract dependency and the installed handler's contract version aligned.
For a real native operation, verify the application result in addition to successful dispatch.
