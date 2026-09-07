---
title: Paper
description: Use Paper builds as the server platform when you want the current PaperMC distribution family.
---

# Paper

Paper scenarios use the `me.whereareiam.anvil.platform.paper` unit plugin. The provider resolves a
Paper build from `Distribution.remote(version, build)` and installs the matching platform agent
into the process workspace.

Use a remote build when the provider publishes an immutable build number for the version you need.
The compatibility matrix currently exercises Paper build `132` for `1.21.11` and build `74` for
`26.1.2`.

Paper accepts modern and legacy forwarding. The provider writes the server configuration it owns
and preserves unrelated values in the same file.
