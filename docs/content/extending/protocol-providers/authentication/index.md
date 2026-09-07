---
title: Authentication
description: Provide login and logout without exposing credentials to Gradle or scenario code.
---

Authentication is an optional service on `ProtocolProvider`. Offline-only implementations inherit
`Optional.empty()` from `authentication(cacheDirectory)` and need no login subsystem.

For an online-capable provider, return a `ProtocolAuthentication` from that method. It must work
without creating a backend or player. The service receives a profile name and a callback for prompts
and status:

| Method | Responsibility |
|---|---|
| `login(profile, output)` | Complete the provider's account workflow and persist its private profile |
| `logout(profile, output)` | Remove the named profile from the private store |

The `cacheDirectory` argument is the Anvil cache root. Put credential documents in a provider-owned
private location. Access and refresh tokens stay inside your provider; never return them to tooling,
include them in status output, or place them in Gradle inputs, CLI arguments, environment variables,
system properties, logs, or project workspaces. If you use an isolated worker, transfer the needed
credential only through its private stdin channel.

## Integrate with the existing tasks

After the provider is installed in `anvilProtocols` and selected, users invoke:

```shell
./gradlew anvilLogin --auth-profile=developer
./gradlew anvilLogout --auth-profile=developer
```

Tooling resolves the provider from `anvilProtocolRuntime` and delegates to its authentication service.
Use `--auth-profile`; Gradle owns `--profile` for build profiling. Scenario requests carry the profile
name, and the provider resolves credentials when it creates an online client.

## Test without a real account

Use a fake authentication service to verify selected-provider dispatch, profile names, status
callbacks, and configuration-cache reuse. Test credential-store behavior with synthetic credentials,
including missing profiles and interrupted writes. Do not add real online-account authentication to
CI. The consumer-facing workflow is documented under
[Building blocks → Players → Authentication](../../../building-blocks/players/authentication/index.md).
