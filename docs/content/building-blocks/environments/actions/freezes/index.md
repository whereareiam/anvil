---
title: Freezing processes
description: Suspend a server or proxy while its players stay connected, as a hung server would, then let it carry on.
---

Freeze a process when a test needs to know what a plugin does when a server stops answering without
crashing: whether another node takes over its work once its leases or heartbeats expire, or whether its
players notice. `processes.freeze(name)` suspends that one process while it keeps its connections, so its
players stay on it, but it runs no tick, answers no packet and renews nothing. `processes.thaw(name)` lets it
carry on where it stopped. [Killing](../kills/index.md) is the counterpart for a process that dies.

```java
var processes = anvil.processes();
processes.freeze("lobby");
assertTrue(processes.server("lobby").frozen());

// ... another server or the proxy takes over within its lease time

processes.thaw("lobby");
```

- Under local execution the process and its children are stopped with `SIGSTOP`, which needs a POSIX
  system; on Windows `freeze` throws `UnsupportedOperationException`. Under Docker execution its container
  is paused.
- Only a running process freezes; freezing a frozen one has no effect, and thawing one that is not frozen has
  none either.
- Stopping, killing or restarting a frozen process thaws it first, so its stop command reaches it.
- The console records each freeze and thaw with an `[Anvil]` line.
- A connected player's client keeps waiting for the frozen process, and may time out on its own after about
  half a minute, as a vanilla client does.
