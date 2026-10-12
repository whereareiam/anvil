---
version: 1.0.0
---

# Anvil 1.0.0 - Many versions, one workbench

Anvil 1.0.0 runs NeoForge and several Minecraft versions per platform, locally or in Docker, lets players read commands and answer dialogs, and brings an IntelliJ plugin that starts and inspects scenarios. Module boundaries and Gradle tasks changed, so read the upgrade notes before moving from 0.0.1.

## Highlights

* NeoForge servers, several Minecraft versions per platform, and processes run in Docker.
* An IntelliJ plugin on JetBrains Marketplace that lists a project's scenarios, starts them from the tool window or the editor, and shows their processes, consoles, players and accounts.
* Players read the command tree they received, check commands as the client does, and see and answer dialogs on Minecraft 1.21.11 and 26.1.2.
* JUnit environments declared with annotations, scenario factories that hand objects to their tests, tests that require stored accounts, and live-test suites with their own Gradle tasks.
* Players verify online logins against another session server, optionally an Yggdrasil mock, choose their virtual host and source address, and report why a connection ended.
* Processes can keep running across scenarios, restart, be killed without a clean shutdown, and run with limited processors and priority.

## Upgrade notes

* Gradle tasks moved to the `anvil` group and were renamed: `anvilScenario --list` is now `anvilScenarios`, `anvilScenario --scenario=<name>` is `anvilRun --scenario=<name>`, and `anvilAccount --login=<id>` and `--logout=<id>` are `anvilLogin --account=<id>` and `anvilLogout --account=<id>`. The `-Panvil.testMode=full` property is gone; run the `anvil` task to run every live test.
* Scenario contracts moved out of `api.runtime`: import them from `api.scenario`, `api.process` and `api.process.type`. Reach and restart processes through `context.processes()`, and create embedded engines through `AnvilLauncher`.
* Extensions build on new module boundaries: capability providers implement the protocol-backed or agent-backed provider APIs, and agent client and server contracts are separate artifacts. Update imports, dependencies and service descriptors as described in the extending guides.
* The IntelliJ plugin runs the project's own Anvil tooling, so keep the project's Anvil version equal to the plugin's.
