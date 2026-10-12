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
| **Load Scenarios** / **Reload Scenarios** | You changed scenario definitions or runtime inputs |
| **Sync Project** | You changed the build and want IntelliJ to import it again, then reload the scenarios |

After a successful sync started anywhere in IntelliJ, Anvil reloads the selected source's scenarios
automatically, waiting until any active run has finished cleanup. Disable **Reload scenarios after
project sync** in [preferences](../settings/index.md) to reload manually.

## Find and inspect a scenario

Search by scenario name, category, description, tag, or process platform. Expand a scenario to see
its servers and proxies. A scenario with a single process and no setup step is shown as that server
or proxy directly.

Selecting a scenario or process shows its declaration in the details panel: entrypoint, execution
and Java settings, timeouts, platform and distribution, memory, online mode, and proxy routes.
Addresses, readiness, and workspace directories appear once a run allocates them. Double-click an
entry, press F4, or choose **Jump to Source** from its context menu to open the definition class.
Select the **Definition** link for the same result; right-click a link to copy its full class name or path.

Scenario titles and descriptions come from the definition's optional `PresentationMetadata`; see
[describing a scenario's purpose](../../../building-blocks/environments/definitions/index.md#describe-the-scenarios-purpose).
Without a display name, Anvil derives a readable title from the stable scenario name.

## Start a run

Select a scenario, or one of its servers or proxies, and choose an action from the toolbar or the
context menu:

| Selection | Action | Effect |
|---|---|---|
| A scenario | **Run Scenario** | Starts every process and runs the scenario's setup once |
| A server or proxy | **Start Server** / **Start Proxy** | Prepares the whole environment but starts only that process |
| Anything, while an environment runs | **Stop** | Stops the selected process of the running scenario, or else the whole environment |

Pressing Enter on a selection runs it as well. The run opens in its own session tab; continue with
[inspecting a run](../sessions/index.md).

One environment runs per project at a time. While another scenario is running, **Run Scenario** is
unavailable and its tooltip names the environment to stop first; **Stop** in the same toolbar stops it.
If a run cannot start, or an environment fails while its tab is not in view, a notification reports
the reason and links to its console.

During a run, a status dot on each icon in the list shows its state: green for running, amber while
starting or changing state, red for failure, and gray for not started or stopped. Hover an item for a
description.

## Run from the editor

Every class that implements `AnvilScenarioDefinition` has a Run icon in the gutter beside its name.
Click it, or right-click the class and choose **Run**, to run that scenario. Anvil creates a temporary
run configuration for the class and its scenario source, so the run also appears in IntelliJ's run
toolbar and Run window. The scenarios do not need to be loaded in the tool window first; the
environment tab shows the scenario's servers and proxies once its runtime has been prepared.

Abstract classes and classes outside an imported Anvil source get no icon.

## Catch mistakes while editing

The editor checks scenario definitions written in Java before you run them:

| Check | Reported when | What to do |
|---|---|---|
| **Latest build in an automated scenario** | `Distribution.remote(version, "latest")` in a method that does not call `manual(true)` on its scenario | Pin a build, or mark the scenario manual |
| **Version unknown to the platform provider** | A `remote` or `pinned` version that the server's or proxy's platform provider does not list | Fix the version, or use one the project's Anvil release knows |
| **Minecraft EULA not accepted** | A `build.gradle.kts` or `build.gradle` applies `me.whereareiam.anvil` or `me.whereareiam.anvil.junit` without `acceptEula()` | Press Alt+Enter and choose **Accept the Minecraft EULA** |

Process names complete as you type: inside the quotes of `entrypoint("…")`, or of a proxy's `server("…")`
and `defaultServer("…")`, press Ctrl+Space to list the servers and proxies the file names. Ctrl+click
a name to jump to the process that declares it.

These checks read only values written as literals or constants. Values computed at run time, such as
a helper method's parameters, are left to the validation that runs before the processes start. The
known versions come from the platform provider on the project's classpath, so they match the Anvil
release the project uses. Turn the checks on or off under **Settings | Editor | Inspections | Anvil**.

## Save a run configuration

Running from the tool window does not create a run configuration. Choose **Save as Run Configuration**
to add the selected scenario, or the selected server or proxy, to IntelliJ's run toolbar; a notification
confirms the name and links to the configuration editor. A configuration stores the scenario source,
the definition class, and an optional process. Leave **Process** blank in the configuration editor to
run the whole scenario. If a saved scenario source is removed or renamed, choose its replacement in
the configuration editor.

Run configurations offer only Run: processes cannot be debugged from the IDE.
