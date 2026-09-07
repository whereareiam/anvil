---
title: Fixtures
description: Use shared setup and real JAR artifacts when the tested boundary requires them.
---

Default to test-specific setup beside the test. Share a fixture when several tests use it or when
compiling and loading a real JAR is part of the behavior being verified.

| Fixture under `anvil-testing/testing-fixtures` | Purpose |
|---|---|
| `fixtures-server-plugin` | Commands and native observations installed in real servers |
| `fixtures-external-extension` | External backend, capabilities, operations, and JAR/class-loader setup |

These modules use ordinary `src/main/java` and `src/main/resources` and produce real JARs. They are
internal test artifacts and are not published as consumer libraries.

## Keep the tested boundary intact

The external-extension fixture compiles against public Anvil contracts. Its runtime tests verify
provider selection, missing-capability diagnostics, and offline authentication defaults. Its live
tests verify agent operations, observations without a Session adapter, and cleanup.

Its in-process backend is a contract fixture, not another production Minecraft client. Use a real
backend for assertions about native protocol compatibility.

Resolve fixture JARs through Gradle project artifacts. Do not guess a sibling `build/libs` filename;
that bypasses build dependencies and can pick a stale or plain artifact when a shaded one is needed.
Keep Anvil and platform APIs provided by the parent loader in extension-JAR tests.

## Choose where new assertions belong

Put a one-off mock or helper with its owning test. Add a command to the server fixture when multiple
journeys need the same native observation. Extend the external fixture when provider discovery or
class-loader behavior requires an independently compiled artifact.

Keep `examples/proof-of-patience` consumer-shaped. It demonstrates adding Anvil to a plugin build and
running journeys; it is not the owner of framework-specific system assertions. See
[Unit and runtime tests](../unit-runtime/index.md) and [Live tests](../live/index.md) for commands.
