---
title: Authentication
description: Provide account discovery and authentication without exposing credentials to Gradle or scenario code.
---

Authentication is an optional service of a protocol library. Offline-only libraries inherit
`Optional.empty()` from `ProtocolLibraryProvider.authentication(accountsDirectory)` and need no
account subsystem.

For an online-capable library, return a `ProtocolAuthentication` from that method. It must work
without creating the library or a player. The service receives a local account ID and a callback for
prompts and status:

| Method | Responsibility |
|---|---|
| `accounts()` | List the accounts your library stores, with their resolved public identity metadata |
| `login(accountId, output)` | Complete your library's account workflow and persist its private account file |
| `logout(accountId, output)` | Remove the named account from the private store |

Each `AuthenticationAccount` that `accounts()` returns names your library ID as its
`getLibraryId()`. Account IDs are local to a library, so two libraries may store the same ID.

The `accountsDirectory` argument is the configured local account root. Put credential documents in a
library-owned private location. Access and refresh tokens stay inside your library; never return them
to tooling, include them in status output, or place them in Gradle inputs, CLI arguments, environment
variables, system properties, logs, or project workspaces. If you use an isolated worker, transfer the
needed credential only through its private stdin channel.

## Integrate with the account manager

The engine's account manager lists the accounts of every installed library. An online player must use
an account stored by the library selected for it, and the selected release must declare the
`ONLINE_AUTHENTICATION` feature; otherwise player creation is refused before connecting. A player
created from an account pool lease uses the library that stores the leased account. A pool refuses an
account ID that several libraries store.

The IntelliJ account manager and the Gradle `anvilAccount` task share one entry point,
`AnvilAuthentication`. It delegates `login` and `logout` to the library named by its `--library`
option, else by `anvil.protocolLibrary`, else to the sole installed library that offers
authentication. Scenario requests carry only the local account ID; your library resolves credentials
when it creates an online client.

## Test without a real account

Use a fake authentication service to verify library selection, account IDs, account listing,
status callbacks, and account-store behavior with synthetic credentials, including missing accounts
and interrupted writes. Do not add real online-account authentication to CI. The consumer-facing
workflow is documented under
[Building blocks → Players → Authentication](../../../building-blocks/players/authentication/index.md).
