---
title: Listeners
description: Give a server or proxy extra ports, such as a plugin's own server, and tell the other processes where to reach them.
---

A plugin may open a port of its own besides the game port, such as a proxy plugin its servers connect to.
Declare it as a listener: Anvil reserves a port for it, publishes it under Docker execution, and names it to
every process of the scenario, so parallel scenarios never share a port.

## Declare a listener

Add it to the server or proxy builder:

```java
.listener("link")
```

A listener's name is lowercase letters, digits and dashes, unique within its process.

## Bind it and reach it

Write placeholders instead of addresses, in text assets and in JVM and program arguments:

| Placeholder | Becomes |
| --- | --- |
| `${anvil.process.<name>.host}` | the host other processes reach the process at |
| `${anvil.process.<name>.port}` | the process's game port, as other processes reach it |
| `${anvil.process.<name>.listener.<listener>}` | the port of one of its listeners |

The process binds the listener on that port itself, at any address; other processes connect to the host and
the port:

```java
// The proxy listens:
.asset(WorkspaceAsset.builder()
		.source(AssetSource.text("listen: 0.0.0.0:${anvil.process.proxy.listener.link}"))
		.target(Path.of("plugins/MyProxyPlugin/config.yml"))
		.build())

// A server connects:
.asset(WorkspaceAsset.builder()
		.source(AssetSource.text("proxy: ${anvil.process.proxy.host}:${anvil.process.proxy.listener.link}"))
		.target(Path.of("plugins/MyServerPlugin/config.yml"))
		.build())
```

Under local execution the listener's port is the same on the host and for other processes. Under Docker
execution the process binds it inside its container and other processes reach it at the process's name in
the scenario's network, while the test reaches its published host port.

The test reaches a listener with `anvil.processes().get("proxy").listener("link")`.

Placeholders name the processes of the same lifetime: a process kept for the engine with
`ProcessLifetime.ENGINE` is prepared before the scenario's own, so their listeners cannot name each other.
