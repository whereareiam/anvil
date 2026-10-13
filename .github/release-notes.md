---
version: 1.0.0
---

# Anvil 1.0.0 - Many versions, one workbench

Anvil 1.0.0 runs NeoForge and several Minecraft versions per platform, locally or in Docker, lets players read commands and answer dialogs, and brings an IntelliJ plugin that starts and inspects scenarios. Most modules, APIs and Gradle tasks changed since 0.0.1, so follow the current documentation rather than upgrading step by step.

## Highlights

* NeoForge servers, several Minecraft versions per platform, and processes run in Docker.
* An IntelliJ plugin on JetBrains Marketplace that lists a project's scenarios, starts them from the tool window or the editor, and shows their processes, consoles, players and accounts.
* Players read the command tree they received, check commands as the client does, and see and answer dialogs on Minecraft 1.21.11 and 26.1.2.
* JUnit environments declared with annotations, scenario factories that hand objects to their tests, tests that require stored accounts, and live-test suites with their own Gradle tasks.
* Players verify online logins against another session server, optionally an Yggdrasil mock, choose their virtual host and source address, and report why a connection ended.
* Processes can keep running across scenarios, restart, be killed without a clean shutdown, and run with limited processors and priority.

