---
title: Overview
description: Supply platform executables and Java runtimes and reuse acquired software between runs.
---

Provisioning supplies the software an environment needs before its processes start. Choose the
platform executable, provide a compatible Java runtime, and decide whether Anvil may obtain missing
inputs or must use software already available on the machine.

For a Paper environment, that means resolving the selected Paper build and its required Java
installation. The executable can remain in the shared cache while the process reads and writes its
own workspace. Supplying software and managing those working files are separate decisions.

## Prepare the software

1. [Platform](./platform/index.md): select a fixed distribution build or checksum, or supply an existing
   executable through a local path or registered artifact.
2. [Java](./java/index.md): select the required Java version and use an installed runtime or a verified
   archive where supported by the execution provider.
3. [Cache](./cache/index.md): reuse acquired artifacts and resolution metadata, prepare offline runs,
   and refresh selections deliberately.

The platform pages explain their own distribution selectors. Anvil uses those provider choices to
resolve the executable; it does not infer a server's native Minecraft version from an arbitrary JAR
filename. Keep the declaration's versions aligned with the environment's players.

## Place files and control state separately

A [workspace plan](../workspaces/index.md) determines what goes into a process's working directory and
what survives a run. Its assets install plugin JARs, configuration, and fixtures. Its persistence,
cache, and cleanup rules manage generated state.

A named artifact can supply either an executable distribution or a workspace asset. Gradle or the
embedding application supplies its exact file; the use of that file determines where it belongs in
the declaration. Copying a plugin JAR into `plugins` is workspace installation, while selecting a
Paper executable is platform provisioning.

Use [execution configuration](../configuration/execution/index.md) to choose how processes run.
Docker image mappings belong to that provider; a host Java installation does not automatically become
a container runtime.
