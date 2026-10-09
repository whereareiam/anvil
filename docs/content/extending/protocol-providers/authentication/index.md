---
title: Authentication
description: Provide account discovery and authentication without exposing credentials to Gradle or scenario code.
---

Authentication is an optional service on `ProtocolProvider`. Offline-only implementations inherit
`Optional.empty()` from `authentication(accountsDirectory)` and need no account subsystem.

For an online-capable provider, return a `ProtocolAuthentication` from that method. It must work
without creating a backend or player. The service receives a local account ID and a callback for prompts
and status:

| Method | Responsibility |
|---|---|
| `accounts()` | List account IDs and resolved public identity metadata |
| `login(accountId, output)` | Complete the provider's account workflow and persist its private account file |
| `logout(accountId, output)` | Remove the named account from the private store |

The `accountsDirectory` argument is the configured local account root. Put credential documents in a provider-owned
private location. Access and refresh tokens stay inside your provider; never return them to tooling,
include them in status output, or place them in Gradle inputs, CLI arguments, environment variables,
system properties, logs, or project workspaces. If you use an isolated worker, transfer the needed
credential only through its private stdin channel.

## Integrate with the account manager

The IntelliJ account manager and the Gradle `anvilAccount` task share one entry point that resolves
the selected provider and delegates `login` and `logout` to its authentication service. Scenario requests carry only the local account ID; the provider resolves credentials when it
creates an online client.

## Test without a real account

Use a fake authentication service to verify selected-provider dispatch, account IDs, account listing,
status callbacks, and account-store behavior with synthetic credentials, including missing accounts
and interrupted writes. Do not add real online-account authentication to
CI. The consumer-facing workflow is documented under
[Building blocks → Players → Authentication](../../../building-blocks/players/authentication/index.md).
