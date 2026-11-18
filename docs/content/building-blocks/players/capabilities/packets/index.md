---
title: Packets
description: Match the packets a player's client receives in the order they arrive, and record whole packet streams to compare two builds of a server.
---

`Packets` shows a test the packets the player's client receives, in the order they arrive on the wire. Use it
when a behavioural assertion cannot see what reached the client: the order of a chunk and the block repairs
after it, a batching change, or whether two builds of a server send the same stream. Install the packets unit,
or the default capability unit. Every MCProtocolLib release Anvil ships has it; a protocol library that does
not hand over the packets its players receive, such as an in-process test library, does not.

There are two ways in:

- a **tap** takes the packets a selection names and keeps their values, for a test to match them;
- a **recording** keeps every packet with its bytes, for a test to compare the whole stream with another one.

Packets cross from the player's worker to the test only while a tap or a recording is open.

### Chunk columns are costly

Some packets are costly: decoding them takes far more than their bytes, and their value holds nothing their
bytes do not. Today the chunk column, `ClientboundLevelChunkWithLight` (`map_chunk`), is the one costly packet
(`ClientboundPackets.isCostly`). The player's release decodes a costly packet only when something asks for its
class, so:

- a tap whose selection names `ClientboundLevelChunkWithLight` gets each chunk's value, and the worker decodes
  chunks while that tap is open;
- a tap of every packet, or of the name `map_chunk`, gets chunk frames with their name and position in the
  stream, but without a value;
- a recording keeps chunk frames as their bytes alone, even while a chunk tap is open, and compares them by
  bytes.

A chunk's value would otherwise double what crosses from the worker and what a recording keeps. On Paper 1.21.11,
loading 312 chunk columns made 28.7 MB on the worker channel and a 4.4 MB recording with the values, and
15.0 MB and 1.8 MB without them.

## Name packets

A packet the Java protocol models has a value of its class, such as `ClientboundBlockUpdate` or
`ClientboundLevelChunkWithLight` from `me.whereareiam.anvil.edition.java.protocol.packet`. Every packet, modelled
or not, also has a name: minecraft-data's name of the packet in the player's protocol, such as `map_chunk`,
`block_change`, `multi_block_change` or `update_light`. These are [minecraft-data's
names](https://github.com/PrismarineJS/minecraft-data), not those of Mojang's code, and stay the same across
the releases that carry the packet. Anvil ships them for every protocol minecraft-data has; for a protocol it
lacks, currently Minecraft 26.2 and 26.3, packets have no name, and a tap refuses to select by name.

A value's class need not match the name: where releases say the same thing in different packets, the protocol
reports the current packet, so a 1.16.5 `spawn_entity_living` arrives as a `ClientboundAddEntity`.

## Match packets with a tap

This fragment belongs in a live test after Alice has connected. It uses `SimulatedPlayer alice`, a
`RunningServer lobby` and these imports:

```java
import me.whereareiam.anvil.capability.packets.PacketTap;
import me.whereareiam.anvil.capability.packets.Packets;
import me.whereareiam.anvil.capability.packets.model.PacketSelection;
import me.whereareiam.anvil.edition.java.protocol.model.world.chunk.ChunkPosition;
import me.whereareiam.anvil.edition.java.protocol.packet.world.chunk.ClientboundBlockUpdate;
import me.whereareiam.anvil.edition.java.protocol.packet.world.chunk.ClientboundLevelChunkWithLight;
```

Open the tap before the action, then wait for the packets in order:

```java
Packets packets = alice.capability(Packets.class);
PacketSelection repair = PacketSelection.of(ClientboundLevelChunkWithLight.class, ClientboundBlockUpdate.class);
try (PacketTap tap = packets.tap(repair)) {
	lobby.console().sendCommand("ptest reload-chunk 0 0");
	tap.received(ClientboundLevelChunkWithLight.class, chunk -> chunk.getPosition().equals(new ChunkPosition(0, 0)));
	tap.received(ClientboundBlockUpdate.class, update -> update.getChange().getBlockState() != 0);
}
```

A tap reads its packets through a cursor: each wait looks at the packets after the cursor, oldest first, and
moves the cursor past the one it returns. The two waits above therefore pass only when the block update
arrived after the chunk. `neverReceived(type, condition, duration)` checks that no such packet arrives,
`expectReceived(type, condition)` gives an `Expectation` to wait on with others, `history()` lists every packet
the tap received and `unread()` those after the cursor. A wait for a class the selection does not take is
refused.

Each `TappedPacket` has its `sequence` among the packets the tap received, the connection's `state`, the
packet's `id`, its `name()` and, when the protocol models it, its `packet()`. Select packets the protocol does
not model by name and wait for them by name:

```java
try (PacketTap tap = packets.tap(PacketSelection.named("update_light"))) {
	lobby.console().sendCommand("ptest relight 0 0");
	TappedPacket light = tap.received("update_light");
}
```

`PacketSelection.every()` takes every packet, and `and` joins selections. The player's worker refuses a name
its protocol has no packet of, so a misspelt name fails at once instead of never matching.

## Record a stream and compare it

A recording keeps every packet the player receives, with its bytes, until it stops. Record a deterministic
scenario on a known-good build, keep the file, and compare a run of the modified build with it. The fragment
uses these imports:

```java
import me.whereareiam.anvil.capability.packets.PacketRecording;
import me.whereareiam.anvil.capability.packets.Packets;
import me.whereareiam.anvil.capability.packets.model.PacketMask;
import me.whereareiam.anvil.capability.packets.model.RecordedPackets;
import java.nio.file.Path;
```

Record the known-good stream once and write it to the test's resources:

```java
Packets packets = alice.capability(Packets.class);
try (PacketRecording recording = packets.record()) {
	lobby.console().sendCommand("ptest chunk-repair");
	packets.write(recording.stop(), Path.of("src/anvil/resources/chunk-repair.packets.jsonl.gz"));
}
```

Compare every later run with it:

```java
RecordedPackets golden = packets.read(Path.of("src/anvil/resources/chunk-repair.packets.jsonl.gz"));
try (PacketRecording recording = packets.record()) {
	lobby.console().sendCommand("ptest chunk-repair");
	packets.assertSame(golden, recording.stop(), PacketMask.runVarying().ignore("bundle_delimiter"));
}
```

`assertSame` throws a `RecordingMismatchException`, an assertion failure, listing the first differences with
the positions of the frames in both streams; `compare` returns every `PacketDifference` instead. Both refuse
recordings of different protocols, whose packets mean other things.

### How streams compare

- Frames pair up in order. The comparison aligns the two streams so that a frame only one stream has, or one
  that moved, is reported once and the frames after it still pair up.
- A pair is equal when its state, its name and its bytes are equal. A difference names the byte offset where
  the bytes part.
- Entity IDs are compared by correspondence, not by number: the first entity ID each stream names in a
  packet the protocol models pairs them, and every later packet must name the paired ID. A build that creates
  one more entity, and so numbers every later entity differently, still compares equal, while a packet sent
  for the wrong entity does not. A pair whose bytes differ but whose value names entities is compared by
  value for this reason.

### Leave out what varies from run to run

A `PacketMask` names what the comparison leaves out:

- `ignore(name)` leaves every frame of that name out of both streams, so neither its presence nor its order
  matters, for example `ignore("bundle_delimiter")`.
- `ignore(type, fields...)` compares the frames whose value is of that class by value, without those fields,
  for example `ignore(ClientboundSystemChat.class, ClientboundSystemChat.Fields.text)`. Every packet class
  has these `Fields` constants, so the compiler checks the names, and a comparison refuses a field the class
  does not have. Without fields, `ignore(type)` compares the class by value alone. A mask refuses a costly
  class, such as `ClientboundLevelChunkWithLight`, because recordings keep its bytes alone; leave chunks out by
  name with `ignore("map_chunk")` when they vary.

`PacketMask.runVarying()` leaves out what differs between two runs of one build: keep-alives, pings and the
periodic time updates, whose presence follows the clock, the login's and the respawn's unmodelled hashed seed,
and the signatures of chat. `PacketMask.strict()` leaves out nothing.

A value holds only what the protocol models; comparing a class by value does not see the rest of its bytes.
A packet the protocol does not model and whose bytes vary, or that names entities, can only be left out by
name; to compare its contents, it needs a model in the protocol, which the
[protocol adapters guide](../../../../extending/capabilities/protocol-adapters/index.md) describes.

### The recording file

`write` writes gzip-compressed JSON Lines, which `zcat file | jq` reads. The first line is the header: the
format version, the `protocol` number, the `minecraft` version of the client and the `player`. Each further
line is one frame in arrival order: its `seq`, `state`, `id`, `name`, its bytes as base64 `payload` and, when
the protocol models it and it is not costly, its `packet` value, which names its `kind`. Entity IDs in values are objects of the one
member `entity`:

```json
{"format":1,"protocol":774,"minecraft":"1.21.11","player":"Alice"}
{"seq":0,"state":"PLAY","id":70,"name":"entity_destroy","payload":"ASo=","packet":{"kind":"ClientboundRemoveEntities","entityIds":[{"entity":42}]}}
{"seq":1,"state":"PLAY","id":42,"name":"update_light","payload":"AgAB..."}
```
