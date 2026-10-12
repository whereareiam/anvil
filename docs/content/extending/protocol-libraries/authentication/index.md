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
`getLibraryId()`. A library with its own store keeps its account IDs local to it; the account manager still
refuses to pool an ID that several libraries store.

The `accountsDirectory` argument is the configured local account root. Put credential documents in a
library-owned private location. Access and refresh tokens stay inside your library; never return them
to tooling, include them in status output, or place them in Gradle inputs, CLI arguments, environment
variables, system properties, logs, or project workspaces. If you use an isolated worker, transfer the
needed credential only through its private stdin channel.

## Let the native worker store your accounts

A library that plugs into Anvil's native worker does not implement `ProtocolAuthentication` itself. Its
`WorkerLibraryProvider.authentication()` returns a `WorkerAuthentication`, which only talks to the online
services:

| Method | Responsibility |
|---|---|
| `login(accountId, version, output)` | Sign the account in interactively and return its `AccountRecord`: username, profile UUID and your credentials as the text of a JSON object |
| `resolve(account, version)` | Refresh a stored record into the `AuthenticationSession` a player of that Minecraft version signs in with, and return the record to keep |

The worker turns that into the library's `ProtocolAuthentication`. It keeps one store for every hosted
library: one private `<accountId>.json` file per account in the account directory, whose `provider` key
names the library that stored it. Listing returns only your library's files. Login, logout and a player's
sign-in refuse an ID another library stores, naming that library, before your sign-in runs, and an
unreadable file is refused rather than replaced. A login receives the newest Minecraft version of your
built-in releases and a refresh the version of the player, for services such as Xbox Live that start a
session for a game version. A file without a `provider` key belongs to the one library that sets
`readsUnlabelledAccounts` on its `WorkerLibrary`; MCProtocolLib does, because it wrote such files.

## Integrate with the account manager

The engine's account manager lists the accounts of every installed library. An online player must use
an account stored by the library selected for it, and the selected release must declare the
`ONLINE_AUTHENTICATION` feature; otherwise player creation is refused before connecting. A player
created from an account pool lease uses the library that stores the leased account. A pool refuses an
account ID that several libraries store; libraries in the native worker share one store, so their IDs never
collide.

The IntelliJ account manager and the Gradle `anvilLogin` and `anvilLogout` tasks share one entry point,
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
