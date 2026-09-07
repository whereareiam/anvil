---
title: Proxies
description: Choose a proxy platform and route players through a backend server group.
---

# Proxies

Anvil treats proxies as first-class server-side platforms. A proxy can sit in front of one or more
servers, and player routing still uses the same `ScenarioContext` and `Server` observation APIs.

Use [Velocity](velocity/index.md) when you want modern PaperMC builds and flexible forwarding
support. Use [BungeeCord](bungeecord/index.md) when you need the legacy BungeeCord family.

Proxy platforms determine which forwarding modes the scenario can negotiate before launch. A player
that joins through a proxy still connects to a concrete backend server in the same scenario.
