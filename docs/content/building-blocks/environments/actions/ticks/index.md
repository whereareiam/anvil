---
title: Waiting for ticks
description: Wait for a server's ticks or until a condition holds instead of sleeping, and have the server sprint through ticks as /tick sprint does.
---

Use a server's `Ticks` capability when a test waits for something the server does over time: crops
growing, water spreading, a machine finishing its work, chunks unloading. Instead of sleeping for a
guessed number of seconds, the test waits in the server's own ticks, checking its condition after each
tick the server completes, so it waits exactly as long as the server needs, however busy the machine is.
A sprint has the server run ticks as fast as it can, as vanilla's `/tick sprint` does, so minutes of game
time pass in seconds. Retrieve it from the process:
`anvil.processes().server("server").capability(Ticks.class)`.

`Ticks` works through Minecraft's own tick rate manager on NeoForge, for every release Anvil supports. Its
provider installs the agent extension it needs into those servers itself; nothing has to be declared in
the scenario. Paper and Spigot servers have no `Ticks` yet, and a proxy runs no ticks. Install the
`me.whereareiam.anvil.capability.ticks` unit with the platform units, or explicitly select the default
capability unit, which includes it.

## Wait until a condition holds

A `Condition` names what the test waits for, looks at the state it depends on, and tells when that state
satisfies it. `until` checks it at once and again after each tick the server completes, and returns the
state it held for. This fragment belongs in a live test after Alice has joined. It uses
`ScenarioContext anvil`, `SimulatedPlayer alice` and these imports:

```java
import me.whereareiam.anvil.api.model.Condition;
import me.whereareiam.anvil.capability.dispatch.Dispatch;
import me.whereareiam.anvil.capability.messages.MessageWatch;
import me.whereareiam.anvil.capability.messages.Messages;
import me.whereareiam.anvil.capability.ticks.Ticks;
import java.time.Duration;
```

```java
Ticks ticks = anvil.processes().server("server").capability(Ticks.class);
Dispatch console = anvil.processes().server("server").capability(Dispatch.class);
Messages messages = alice.capability(Messages.class);

console.execute("setblock 4 64 4 minecraft:water");
Condition<String> spread = Condition.of("the water reaches 8 64 4", () -> {
	MessageWatch answer = messages.watch();
	messages.command("execute if block 8 64 4 minecraft:water");
	return answer.received(text -> text.contains("commands.execute.conditional"), Duration.ofSeconds(5)).plain();
}, answer -> answer.contains("pass"));

ticks.until(spread, Duration.ofSeconds(30));
```

When the condition has not held by the timeout, the wait throws `IllegalStateException` naming the
condition, the time and ticks it waited, and the state it saw last, here the server's last answer:

```text
Gave up on 'server' after 30.0 s and 597 ticks waiting until the water reaches 8 64 4; last seen: <answer>
```

`Condition.of(description, check)` takes a plain `BooleanSupplier` when there is no state worth reporting;
the last state seen is then `false`. The observation runs once on every check and may itself fail, as the
message wait above does when no answer arrives; that failure ends the wait.

## Let the server run ticks

`await(ticks, timeout)` waits until the server has run a number of ticks at its own pace, for a test that
watches the server do its work for a while, such as checking that no error is logged during 200 ticks of a
busy farm. `tick()` returns how many ticks the server's process has run.

```java
ticks.await(200, Duration.ofSeconds(30));
```

## Sprint through ticks

`sprint(ticks, timeout)` has the server run the ticks as fast as it can and returns once it has run them.
`sprintUntil(condition, ticks, timeout)` sprints at most that many ticks, checks the condition after each
answer the sprinting server gives, and stops the sprint once the condition holds:

```java
ticks.sprint(1_200, Duration.ofSeconds(30));

Condition<Boolean> grown = Condition.of("the wheat at 4 64 4 is fully grown",
		() -> blockMatches(messages, "4 64 4", "minecraft:wheat[age=7]"));
ticks.sprintUntil(grown, 24_000, Duration.ofSeconds(60));
```

`blockMatches` stands for the test's own check, such as the `execute if block` command above.

- A sprint already running is replaced by the new one.
- The server runs a few ticks more while the last check is made and the sprint stopped, so wait with
  `sprintUntil` only for a condition that stays true once it holds.
- A sprint that runs its ticks without the condition holding, or does not finish in time, is stopped and the
  wait fails as `until` does. A sprint the scenario left running is stopped when the scenario finishes, so a
  server kept for later scenarios runs at its own pace again.
- Players stay connected while the server sprints, but their clients see the world change in bursts, as with
  `/tick sprint`. Movement and other client actions are best done before or after a sprint.

A server restarted while a wait runs counts on from the first tick of its new process. A wait on a frozen
process, see [Freezing processes](../freezes/index.md), gives up at its timeout.
