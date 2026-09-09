---
title: Organizing journeys
description: Keep automated behavior readable and reuse journey code without sharing live state between tests.
---

A journey is the sequence your test executes against a running environment. Give each test a clear
starting condition, an action, and an expected observation. Keep the environment declaration focused
on the processes and inputs that make those steps possible.

## Give setup and assertions separate responsibilities

Declare plugin JARs, configuration, and fixture files as
[workspace inputs](../../../building-blocks/environments/workspaces/assets/index.md). Use a
[setup hook](../../../building-blocks/environments/definitions/index.md#prepare-live-state) for live preparation required
by every journey using that environment. Keep the action under test and its expected result together
in the journey, so a reader can see what a passing test establishes.

For example, granting a fixture permission may be common setup. Sending the permission-protected
command and verifying its reply belong in the test that promises to exercise that permission.

## Reuse a sequence through ordinary Java helpers

The following example runs the same small message-delivery journey for two player names. It uses
`PaperScenario` from the [first test](../../../getting-started/first-test/index.mdx), including its
direct process named `server` and installed Session, Messages, and Server capabilities.

Place `AnnouncementJourneyTest.java` in package `com.example.test` among the sources executed by your
JUnit runner. With the Anvil Gradle plugin, its location is
`src/anvil/java/com/example/test/AnnouncementJourneyTest.java`.

```java
package com.example.test;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.capability.messages.Messages;
import me.whereareiam.anvil.capability.server.Server;
import me.whereareiam.anvil.capability.session.Session;
import me.whereareiam.anvil.junit.AnvilTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@AnvilTest(PaperScenario.class)
class AnnouncementJourneyTest {
	@Test
	void aliceReceivesTheAnnouncement(ScenarioContext anvil) {
		verifyMessageDelivery(anvil, "Alice");
	}

	@Test
	void bobReceivesTheAnnouncement(ScenarioContext anvil) {
		verifyMessageDelivery(anvil, "Bob");
	}

	private void verifyMessageDelivery(ScenarioContext anvil, String playerName) {
		var player = anvil.players().create(playerName);
		var session = player.capability(Session.class);
		session.connect();
		session.connected();
		var identity = player.capability(Server.class).joined("server");
		assertEquals(playerName, identity.getObservedUsername());

		String marker = "announcement-for-" + playerName;
		anvil.processes().server("server").console().sendCommand("say " + marker);
		String message = player.capability(Messages.class).received(marker);
		assertTrue(message.contains(marker));
	}
}
```

Each test receives its own fresh execution and creates its own player. The helper establishes login,
agent-observed presence in this direct-server environment, and receipt of the announcement. Replace
the announcement step with your application's command or channelEvent and assert the result it promises.
The runner closes remaining players and processes after each method.

The marker is sufficient here because each player is new and the helper runs once per test. For
repeated actions on the same player, use distinct response markers or another observation;
`Messages.received` can match earlier captured messages.

## Reuse declarations without sharing live objects

Several tests can select one definition while exercising different behavior. To exercise one journey
against another supported environment, add a method selecting that definition and call the same
helper. Use common process names when the helper expects the same roles. A catalog or group does not
automatically create a JUnit test matrix; select each environment through the
[JUnit integration](../../../integrations/junit/selection/index.md).

Do not store contexts, players, or process handles in static fields for later methods. File reuse is
an explicit [workspace policy](../../../building-blocks/environments/workspaces/persistence/index.md),
and each journey must establish the initial application state it requires.

## Run and diagnose the smallest journey

Use your runner's test selection to execute the method you are changing. The
[Gradle testing guide](../../../integrations/gradle/testing/index.md) covers its source set, filters,
and reports. Keep isolated application logic in ordinary unit tests and reserve live journeys for
behavior that depends on the actual runtime.

A JUnit assertion-only failure does not currently mark the scenario lifecycle unsuccessful for
workspace retention. Choose a persistent workspace when you need files after every outcome and
consult [cleanup diagnostics](../../../help/troubleshooting/cleanup/index.md). Use
[assertions and waits](../assertions/index.md) to make failures identify the missing observation.
