---
version: 2.0.0
---

# Anvil 2.0.0 - Players with cookies and screens

Your simulated players now keep cookies, see what the client sees, and wait on watches instead of counting checkpoints; your scenarios can even run on another machine. It is a big release, so read the upgrade notes before you strike.

## Upgrade notes

Upgrade notes are grouped by who has to act. Tests and build scripts are covered first; extension, agent and protocol library authors follow.

### Tests

#### `Server` becomes `Players`, on each server and proxy

The player-owned `Server` capability is replaced by the process-owned `Players` capability (`me.whereareiam.anvil.capability.players`). Ask the process instead of the player: `process.capability(Players.class)` offers `online()`, `profile(player)`, `backend(player)` and `watch()`. The watch is a `PlayerWatch` with `joined`, `left`, `neverJoined` and `expectJoined`.

On a proxy's watch, each method also takes a backend's scenario name, such as `joined(alice, "lobby")` or `left(alice, "lobby")`, to wait for the proxy to connect the player to that backend or move it away. Without a name, they mean the proxy itself.

| Before | After |
| --- | --- |
| `alice.capability(Server.class).joined("lobby")` | Create `PlayerWatch arrivals = anvil.processes().server("lobby").capability(Players.class).watch()` before the action, then call `arrivals.joined(alice)` |
| `Server.stayed("auth", duration)` | `neverJoined(alice, duration)` on a watch of each server the player must not reach, or `neverJoined(alice, "game", duration)` on the proxy's watch |
| `Server.identity()`, the observed profile | `players.profile(alice)` on the process whose view you need |
| `Server.identity()`, the route's server | Wait for `joined(alice, "lobby")` on a proxy watch created before the action, or read the proxy's `backend(alice)` once the player has settled |

`backend(alice)` is a snapshot that can lag a moment behind a move, so do not read it straight after an action.

`PlayerRoute`, `PlayerIdentity` and `PlayerObservation` are removed. The profiles the client requested and was granted are on the player: `SimulatedPlayer.requestedProfile()`, `grantedProfile()` and the new `grantedProfiles()`, which lists every profile granted at its logins.

#### `Console` becomes `Dispatch`

The process-owned `Console` capability is renamed to `Dispatch`. Import `me.whereareiam.anvil.capability.dispatch.Dispatch` and retrieve it with `process.capability(Dispatch.class)`. `execute(command)` is unchanged, and so is the process's own `RunningProcess.console()`.

#### Watches replace checkpoints

Histories are read through watches. `checkpoint()` and every wait that took one are removed.

| Capability | Create the watch | Watch methods | Removed |
| --- | --- | --- | --- |
| `Messages` | `messages.watch()` → `MessageWatch` | `received`, `neverReceived` (was `notReceived`), `expectReceived`, `unread` | `ReceivedMessage` |
| `Dialogs` | `dialogs.watch()` → `DialogWatch` | `shown`, `neverShown`, `expectShown` | `ShownDialog` |
| `Cookies` | `cookies.watch()` → `CookieWatch` | `requested`, `neverRequested`, `expectRequested` | |
| `ProcessConsole` | `console.watch()` or `console.watchFromStart()` → `ConsoleWatch` | `printed` (was `await(text, checkpoint, timeout)`), `neverPrinted`, `read(maximumLines)` (was `read(checkpoint, maximumLines)`) | `ConsoleOutput.nextCheckpoint` |

Pressing, closing and confirming dialogs stay on `Dialogs`.

#### Messages are the chat the player sees

- `Messages` follows what the player's chat shows, by the vanilla client's rules. `history()`, `received(...)` and `watch()` no longer hold chat that arrives while the client has no player, such as before the server's login or while a proxy returns the player to configuration. They also leave out player chat that the server's filter hides completely. A wait that fails names the matching messages the chat left out, and why.
- Action-bar text sent as chat moves from `Messages` to the action bars of `Screen`; wait for it with `screen.watchActionBars()`. This covers game-info chat before Minecraft 1.19 and overlay system chat from 1.19.
- `Messages.received(...)`, `MessageWatch.received(...)`, `history()` and `unread()` return each message as `Text` (`me.whereareiam.anvil.protocol.api.model.text`) instead of a `String`. `Text` is the component the server sent: `spans()` gives what the player sees, with each part's colour, decorations, click and hover, and `find(...)` finds components such as a translation and its arguments. Call `plain()` where a test used the text. Matchers still receive the plain text.

#### Dialog payloads stay on the player's worker

- `DialogAction.getPayload()` is removed. A `custom` or `dynamic/custom` action carries its click ID in `getValue()` and has a `getHandle()`.
- `DialogAction.of(type, value)` drops its payload argument; use `DialogAction.custom(type, id, handle)` for custom actions.
- `DialogClick` names the pressed action instead of carrying its ID and payload: `DialogClick.custom(handle, values)` replaces `custom(id, payload, values)`, and `getCustomId()` and `getPayload()` are removed.
- Pressing a custom button fails with an `IllegalStateException` when the server replaced or cleared its dialog before the click was sent.
- `DialogAfterAction` gains `OTHER` for an after-action Anvil does not know. Pressing a button of such a dialog fails.

#### Command trees

`CommandTreeNode` reports the root's name and a non-argument's parser as `null` instead of an empty string. A string argument's mode is the `StringArgumentMode` enum in `me.whereareiam.anvil.capability.commands.type` instead of its name.

### Build scripts

Both renamed capabilities come with new Gradle units. The default unit includes both.

| Before | After |
| --- | --- |
| `me.whereareiam.anvil.capability.server` | `me.whereareiam.anvil.capability.players` |
| `me.whereareiam.anvil.capability.console` | `me.whereareiam.anvil.capability.dispatch` (artifact `builtin-dispatch`) |

The built-in player capabilities ship one worker side per edition instead of one per library. `builtin-<feature>-java` replaces `builtin-<feature>-mcprotocol` and its release segments.

### Capability providers

#### Player and process capabilities

- Player capability providers read `requestedProfile()`, `grantedProfile()` and `connection()` from their context instead of `observation()`. `connection()` is the player's `PlayerConnection`, and its target process is always named.
- `PlayerCapabilityContext` has no player event feed any more: what a server or proxy knows is read from that process.
- Agent-backed player capabilities are removed, together with `AgentPlayerCapabilityProvider` and `AgentPlayerCapabilityContext`. What a server or proxy knows about a player is now a process capability: implement `AgentProcessCapabilityProvider`, register it under `META-INF/services/me.whereareiam.anvil.capability.agent.api.process.AgentProcessCapabilityProvider`, and retrieve it with `process.capability(Type.class)`.
- `RequestChannel`, which agent-backed process providers receive, moves to `me.whereareiam.anvil.capability.api.process`.
- Process capability providers reach the platform's general facts through `ProcessCapabilityContext.platform()`: `execute(command)`, and `events()` for the joins and leaves of that process only.

#### Protocol player capabilities

- Providers declare what they need instead of naming libraries: `requiredProtocols()`, such as `Set.of(JavaProtocol.class)`, or `workerBacked()` for a worker extension without an edition protocol.
- The built-in capabilities no longer restrict `supportedLibraries()` to `mcprotocol`. Keep it only for capabilities that use one library's own services.
- Providers receive the player's typed `ProtocolChannel` (`me.whereareiam.anvil.protocol.api.channel`) from `ProtocolPlayerCapabilityContext.channel()` instead of `CapabilityChannel`. It has the same methods, over `OperationDescriptor` and `EventDescriptor`.
- `CapabilityPlayer`, `ProtocolCapabilityPlayer`, `CapabilityChannel` and `MessageChannel` are removed. Capability composition works on the library's `ProtocolPlayer` and its `ProtocolChannel`.
- A custom `ProtocolPlayerComposerProvider` may override the new `create(Collection<String> libraries)`, which receives the libraries a scenario's players use by default. Otherwise `create()` is called.
- In the built-in capabilities, each feature's `<Feature>Packets` port is removed. The contract between a capability's host and worker moves to `<feature>-api` as `<Feature>Channel` in its `channel` package, which also holds the capability ID instead of the provider's `ID` constant.

#### Worker extensions

Worker extensions implement the player API's contracts from artifact `protocol-api`, which `capability-protocol-api` brings along.

| Before | After |
| --- | --- |
| `WorkerExtension`, `PlayerBindingContext`, `OperationRegistry`, `WorkerBinding` | Moved to `me.whereareiam.anvil.protocol.api.worker` |
| Service file for `WorkerExtension` | `META-INF/services/me.whereareiam.anvil.protocol.api.worker.WorkerExtension` |
| `ChannelOperation` | `OperationDescriptor` |
| `EventDescriptor` | Moved to `me.whereareiam.anvil.protocol.api.model.channel` |
| `ViewRotation`, `PlayerConnectionEvent` | Moved to `me.whereareiam.anvil.protocol.api.model.player` |
| `Subscription` | Moved to `me.whereareiam.anvil.protocol.api.channel` |
| `AdapterUnavailableException` (a `CapabilityException`) | `ProtocolUnavailableException` in `me.whereareiam.anvil.protocol.api.exception` (an `IllegalStateException`) |

Extensions speak the Java Edition protocol through artifact `protocol-java-api`:

- `PlayerBindingContext.protocol(JavaProtocol.class)` returns the player's `JavaProtocol`. It listens to and sends packets as release-neutral values, grouped by area under `me.whereareiam.anvil.protocol.java.api.packet`. It follows the player's current connection, observes the packets bindings send with `listenSent`, and tells with `supports` which packets the release carries.
- The library translates packets for every release, so an extension needs no segments of its own. `PlayerBindingContext.adapter(Class)` remains for code the protocol does not carry yet.
- An extension that speaks the protocol follows connections through `bindConnection(...)`, requires the packets it cannot work without through `JavaProtocol.require(...)`, and closes its listeners together through `Subscription.all(...)`. It may drop its `libraryId()`, which now defaults to every library.
- A binding from `bindNativeSession` now also closes when its connection ends, not only when a new connection replaces it.

### Platform agents

- Platform agents built outside Anvil no longer implement `identity(UUID)`. They report every player joining and leaving to `AgentServer.playerEvents()` (`PlayerEvents`); on a proxy, they report for the proxy itself and for every backend. The agent answers through the new `player-events` operation, next to `ping` and `command`.
- `AgentClient.identity(UUID)` and `executeCommand(command)` are removed, together with the `identity` operation and its models.
- `AgentOperationProvider.install` receives the platform agent: `install(PlatformAgent platform, AgentOperationRegistry registry)`.

### Protocol libraries

#### Players and profiles

- Implement `ProtocolPlayer.requestedProfile()` and `grantedProfile()` instead of `identity()`. Implement `grantedProfiles()` too if you keep every profile granted at earlier logins; without it, a player's events are found only under the current profile.
- A custom `ProtocolPlayerComposer` receives the player's `PlayerConnection` instead of an observation.
- Declare the protocols your players speak in `ProtocolLibraryProvider.protocols()`.

#### Channels and workers

- Implement the typed `ProtocolChannel` yourself: `request(OperationDescriptor, request)` and `subscribe(EventDescriptor, listener)` replace the byte-array methods, and the library owns their encoding.
- Workers load `WorkerExtension`s directly, and the worker's player implements `PlayerBindingContext`. Run worker extensions through the `player-worker` and `protocol-java-worker` kits. A worker-backed capability of a library that runs no worker extensions is now skipped with that reason instead of being composed.
- `NativeWorkerProvider`, `NativeWorkerExtension`, `NativePlayer`, `NativeOperations`, `NativeBinding`, `NativeWorkerContext`, `ProtocolSubscription` and `NativeAdapterUnavailableException` are removed.

#### Chat

- Carry system, player and disguised chat as `Text` in `ClientboundSystemChat`, `ClientboundPlayerChat` and `ClientboundDisguisedChat`. A library without components passes `Text.literal(text)`.
- When building `ClientboundPlayerChat`, pass the message's filter mask, signature and last seen signatures. For a message that passes through unsigned, as every message before Minecraft 1.19 does, call `ClientboundPlayerChat.unsigned(text)`.
- From Minecraft 1.19.1, report the server's chat deletions as `ClientboundDeleteChat`, which `Messages` needs to model the chat.
- Send `PlayerRequest.getLanguage()`, the player's game language, in the client information instead of a fixed `en_us`.
