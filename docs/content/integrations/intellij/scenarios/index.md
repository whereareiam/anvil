---
title: Running scenarios
description: Load a project's scenarios in the Anvil tool window, start an environment or one process, and save run configurations.
---

[Install the plugin](../installation/index.md) and open a Gradle project that applies the standard
Anvil plugin. Its compiled `AnvilScenarioDefinition` classes provide the environments you can run; see
[scenario definitions](../../../workflows/scenarios/definitions/index.md) for a complete example.

## Load the project's scenarios

Open **View → Tool Windows → Anvil**. Anvil reads the Gradle model that IntelliJ imported for the
project and loads the scenarios automatically. If the project has not been imported yet, Anvil starts
the initial sync; if an import is already running, it waits for it.

Loading compiles the project's scenario sources, resolves its runtime and artifacts, and reads its
scenario definitions in a separate JVM. It does not start Minecraft. The standard Anvil plugin indexes
definitions compiled from the `anvil` and `test` source sets. The
[project tooling guide](../../gradle/tooling/index.md) covers preparation.

When the project contains several scenario sources, choose one in **Scenario source** above the list.
Nested builds must be opened or linked in IntelliJ before they appear.

Use the toolbar to keep the list current:

| Action | Use it when |
|---|---|
| **Load scenarios** / **Refresh scenarios** | You changed scenario definitions or runtime inputs |
| **Sync project** | You changed the build and want IntelliJ to import it again, then reload the scenarios |

After a successful sync started anywhere in IntelliJ, Anvil reloads the selected source's scenarios
automatically, waiting until any active run has finished cleanup. Disable **Refresh scenarios after
project sync** in [preferences](../settings/index.md) to reload manually.

## Find and inspect a scenario

Search by scenario name, category, description, tag, or process platform. Expand a scenario to see
its servers and proxies. A scenario with a single process and no setup step is shown as that server
or proxy directly.

Selecting a scenario or process shows its declaration in the details panel: entrypoint, execution
and Java settings, timeouts, platform and distribution, memory, online mode, and proxy routes.
Addresses, readiness, and workspace directories appear once a run allocates them. Select the
**Definition** link, or use **Open source**, to open the definition class; right-click a link to copy
its full class name or path.

Scenario titles and descriptions come from the definition's optional `PresentationMetadata`; see
[describing a scenario's purpose](../../../building-blocks/environments/definitions/index.md#describe-the-scenarios-purpose).
Without a display name, Anvil derives a readable title from the stable scenario name.

## Start a run

| Selection | Toolbar action | Effect |
|---|---|---|
| A scenario | **Run scenario** | Starts every process and runs the scenario's setup once |
| A server or proxy | **Start server** / **Start proxy** | Prepares the whole environment but starts only that process |

Pressing Enter on a selected scenario also runs it. The run opens in a new session tab; continue with
[inspecting a run](../sessions/index.md).

During a run, a status dot on each icon in the list shows its state: green for running, amber while
starting or changing state, red for failure, and gray for not started or stopped. Hover an item for a
description.

## Save a run configuration

**Run scenario** does not create a Run Configuration. Choose **Save run configuration** to add the
selected scenario, or the selected server or proxy, to IntelliJ's Run menu. The configuration stores
the scenario source, definition class, scenario name, and process. Leave **Process ID** blank in the
configuration editor to run the whole scenario. If a saved scenario source is removed or renamed,
choose its replacement in the configuration editor.
