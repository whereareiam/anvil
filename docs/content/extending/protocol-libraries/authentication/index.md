---
title: Authentication
description: Derive your library's session from a stored Microsoft account without exposing credentials to Gradle or scenario code.
---

An Anvil account is one Microsoft account. The user signs it in once, without choosing a library, and every
installed library that plays online derives its own session from it. Whether an account can be used is a
property of the Minecraft edition, not of the library: the worker records one result per edition, which counts
for every installed library of that edition. Offline-only libraries need no account code at all.

## Derive a session in the native worker

A library that plugs into Anvil's native worker returns a `WorkerAuthentication` from
`WorkerLibraryProvider.authentication()`. It has one method:

| Method | Responsibility |
|---|---|
| `derive(account, version)` | Turn the account's Microsoft token into the `AuthenticationSession` a player of that Minecraft version signs in with, or refuse the account |

`AccountCredentials` carries the account ID, its `MicrosoftToken` (the access and refresh token, their expiry,
and the client ID and scope of the Microsoft application that issued them) and the session your library
cached in the account file last time. Return a `DerivedSession`:

- `DerivedSession.available(session, cachedSession, token)` when your services accept the account. The worker
  stores `cachedSession`, a text of your choice such as a serialized token chain, for your next derivation, and
  records the session's username and UUID as the account's profile for the edition the derivation was for.
- `DerivedSession.unavailable(reason, token)` when the services of the edition refuse the account, for example
  because it does not own that edition. Word the reason for people: name the account and what it lacks, never
  credentials. The worker records it for the edition and refuses the player with it.
- Throw `IllegalStateException` when you cannot tell, for example because a service is unreachable. A sign-in
  or a pool's check then records the account's availability for the edition as unknown.

Either result returns the Microsoft token as your derivation left it. Refresh it with the application it names;
the worker stores the refreshed token in place of the old one, so the next library derives from the newest
token. Restore your cached session with the token you are given rather than the copy inside the cache, which
another library's refresh may have made stale. MCProtocolLib and CloudburstMC Protocol both build a
MinecraftAuth manager with `login(MsaToken)` from the account's token and reuse their cached manager.

The worker owns everything around it: the device-code sign-in, the account files, and the
`ProtocolAuthentication` that tooling and the account manager use. Right after a sign-in it checks each edition
that the installed libraries' releases with the `ONLINE_AUTHENTICATION` feature speak once, with the first
installed library of the edition, calling `derive` with the newest Minecraft version of that library's built-in
releases of the edition. A pool that has no available account for an edition checks an account of unknown
availability the same way. For a player, the worker calls the player's library with the player's version, for
services such as Xbox Live that start a session for a game version, and records the result for the edition of the
player's release. It delivers the session to the player's worker JVM only over its private standard input.

## Where accounts are listed and pooled

`ProtocolLibraryHost.authentication(loader, accountsDirectory)` offers the account workflow of the libraries a
host runs; the native worker's host offers it as soon as one of its libraries has a `WorkerAuthentication`.
`ProtocolLibraryRegistry.authentication(accountsDirectory)` returns it for the installed libraries, creating
neither a library nor a player.

Each `AuthenticationAccount` lists its `AccountAccess` per `Edition`: `AVAILABLE` with the profile,
`UNAVAILABLE` with the checking library's reason, or `UNKNOWN`. `ProtocolAuthentication.check(accountId,
edition)` checks one edition again and records the result. The engine refuses an online player before it
connects when the account is recorded as unavailable for the edition of the servers the player reaches, and a
pool lends a player only an account available for that edition, checking accounts of unknown availability when
it has none. The selected release must also declare the `ONLINE_AUTHENTICATION` feature.

The IntelliJ account manager and the Gradle `anvilLogin` and `anvilLogout` tasks share one entry point,
`AnvilAuthentication`, which signs in through that workflow. Scenario requests carry only the local account ID.

Access and refresh tokens never go to tooling, status output, Gradle inputs, CLI arguments, environment
variables, system properties, logs, or project workspaces.

## Test without a real account

Call your `WorkerAuthentication` with a synthetic `MicrosoftToken` and a cached session whose game tokens never
expire, so no service is contacted, and answer the one request you want to test, such as a refusal, through
MinecraftAuth's `HttpClient` with a request executor of your own. Do not add real online-account
authentication to CI. The consumer-facing workflow is documented under
[Building blocks → Players → Authentication](../../../building-blocks/players/authentication/index.md).
