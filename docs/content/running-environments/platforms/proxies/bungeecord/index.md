---
title: BungeeCord
description: Use BungeeCord when you need the legacy proxy family and legacy forwarding behavior.
---

# BungeeCord

BungeeCord scenarios use the `me.whereareiam.anvil.platform.bungeecord` unit plugin. The provider
resolves a BungeeCord build from `Distribution.remote(version, build)` and installs the matching
platform agent into the process workspace.

BungeeCord uses legacy forwarding. The scenario still declares backend servers explicitly, and the
proxy routes players to the named default backend or to the route your test selects.

The current compatibility matrix exercises build `2085` for the supported BungeeCord version.
