---
title: Velocity
description: Use Velocity when you want the modern proxy family and flexible forwarding support.
---

# Velocity

Velocity scenarios use the `me.whereareiam.anvil.platform.velocity` unit plugin. The provider
resolves a Velocity build from `Distribution.remote(version, build)` and installs the matching
platform agent into the process workspace.

Velocity supports the forwarding modes that Anvil can negotiate for the connected server group.
Declare the backend servers in the scenario, set the proxy as the entrypoint when players should
join through it, and use the `Server` capability to assert the backend that actually observed the
player.

```java
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftProxy;
import me.whereareiam.anvil.api.type.Platforms;

MinecraftProxy proxy = MinecraftProxy.builder()
        .name("proxy")
        .platform(Platforms.VELOCITY)
        .distribution(Distribution.remote("3.5.1", "615"))
        .server("lobby")
        .defaultServer("lobby")
        .build();
```

Paper accepts modern and legacy forwarding. Velocity can participate in either mode, so the
scenario negotiates a compatible forwarding configuration before launch. The current compatibility
matrix exercises Velocity build `615`.
