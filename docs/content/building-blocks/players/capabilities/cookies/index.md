---
title: Cookies
description: Hold the cookies a server stores on the player, answer its requests as the client does and wait for them.
---

`Cookies` holds the cookies a server stores on the player and answers the server's cookie requests as
the Minecraft client does. Cookies arrived in Minecraft 1.20.5, so players on 1.20.6, 1.21.1, 1.21.11 and
26.1.2 get this capability; players on 1.16.5 to 1.19.4 do not, and retrieving it fails with
`CapabilityUnavailableException`. Install the cookies unit and its Session dependency, or explicitly
select the default capability unit.

A player with the capability answers every request on its own, in the login, configuration and game
phases, whether or not the test looks at its cookies. A server or proxy that relies on cookies, for
example one that asks for a cookie to keep a connection alive or that passes a ticket from one server to
the next, therefore meets the same answers it would get from a vanilla client.

## Check what the server asked for

This fragment belongs in a live test after Alice has connected to a server whose plugin stores a cookie
and asks for it again. It uses `SimulatedPlayer alice` and these imports:

```java
import me.whereareiam.anvil.capability.cookies.CookieWatch;
import me.whereareiam.anvil.capability.cookies.Cookies;
import me.whereareiam.anvil.capability.cookies.model.CookieRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
```

Wait until Alice holds the cookie, then for the request, and read what she answered:

```java
Cookies cookies = alice.capability(Cookies.class);
CookieWatch asked = cookies.watch();
byte[] ticket = cookies.stored("example:ticket", Duration.ofSeconds(10));
CookieRequest request = asked.requested("example:ticket", Duration.ofSeconds(10));
String answered = new String(request.getAnswer(), StandardCharsets.UTF_8);
```

`stored(key)` waits until Alice holds a cookie under the key and returns a copy of its payload. A watch
sees only the requests made after it was created: `requested(key)` waits for the next request for the key,
and the following wait continues after it. The request carries the answer Alice gave, or `null` when she
held no cookie under the key.

`neverRequested(key, duration)` fails as soon as the server has asked for the key since the previous wait
and returns normally once the duration has passed without a request; it does not move the watch.
`expectRequested(key)` describes the next request without waiting, for
[`player.awaitFirst(...)`](../../../../workflows/testing/assertions/index.md#wait-for-whichever-outcome-comes-first).

## Keys

A key is a resource location, such as `example:ticket`. A key without a namespace means the
`minecraft` namespace, as in the game: `ticket` and `minecraft:ticket` name the same cookie. Keys in
`stored()` and in requests always carry their namespace.

## Give and take away cookies

`store(key, payload)` gives Alice a cookie, replacing one with the same key, and `remove(key)` takes it
away, so the next request for it is answered with none:

```java
cookies.store("example:ticket", "day-pass".getBytes(StandardCharsets.UTF_8));
cookies.remove("example:ticket");
```

A payload holds at most 5120 bytes, as in the game; a larger one, or a key that is not a resource
location, fails with `IllegalArgumentException`.

## Cookies and connections

A vanilla client keeps the cookies of a connection only across a transfer to another server, which an
Anvil player does not follow. Each connection therefore starts with no cookies, and its cookies end with
it:

- While Alice is connected, `store(...)` gives the cookie to the current connection.
- While she is disconnected, `store(...)` gives it to her next connection, as if she arrived there
  through a transfer.
- `stored()` returns what she holds now: the current connection's cookies, or the ones her next
  connection starts with.

The history of requests is kept across reconnects, so start a watch before the join when a key is
requested on every join.

## Inspect failures

`requests()` returns every request the server made, in order, without waiting, and `stored()` returns a
snapshot of the cookies Alice holds. When a wait for a request fails, check that the server or plugin
asks for the cookie on this connection, and that its key has the namespace you expect.
