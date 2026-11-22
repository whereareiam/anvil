package me.whereareiam.anvil.edition.java.protocol.worker.position;

import me.whereareiam.anvil.edition.java.protocol.model.world.Vec3d;
import me.whereareiam.anvil.edition.java.protocol.packet.health.ClientboundSetHealth;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ServerboundClientTickEnd;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ServerboundPlayerLoaded;
import me.whereareiam.anvil.edition.java.protocol.worker.TestJavaPlayer;
import me.whereareiam.anvil.player.api.channel.Subscription;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static me.whereareiam.anvil.edition.java.protocol.worker.position.JoinedPlayers.LOADING_STARTED;
import static me.whereareiam.anvil.edition.java.protocol.worker.position.JoinedPlayers.connected;
import static me.whereareiam.anvil.edition.java.protocol.worker.position.JoinedPlayers.joined;
import static me.whereareiam.anvil.edition.java.protocol.worker.position.JoinedPlayers.load;
import static me.whereareiam.anvil.edition.java.protocol.worker.position.JoinedPlayers.teleport;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientPositionTickTest {
	private static final Vec3d SPAWN = new Vec3d(8.5, 80.0, 8.5);

	private final AtomicInteger ticked = new AtomicInteger();

	@Test
	void ticksNothingBeforeThePlayerJoinedALevel() {
		TestJavaPlayer player = connected("1.21.11");
		player.protocol().player().onTick(ticked::incrementAndGet);

		player.tick();

		assertEquals(List.of(), player.sent());
		assertEquals(0, ticked.get());
	}

	@Test
	void endsEachTickFrom1_21_2AndTicksThePlayerOnceItsPositionIsKnown() {
		for (String version : List.of("1.21.1", "1.21.11")) {
			TestJavaPlayer player = joined(version);
			AtomicInteger ticks = new AtomicInteger();
			player.protocol().player().onTick(ticks::incrementAndGet);
			player.tick();
			assertEquals(0, ticks.get(), version);

			player.deliver(teleport(SPAWN, 0.0F, 0.0F, Set.of()));
			player.tick();
			player.tick();

			assertEquals(version.equals("1.21.1") ? 2 : 0, ticks.get(), version + ": 1.21.4 waits for its level");
			assertEquals(version.equals("1.21.1") ? 0 : 3, player.sent(ServerboundClientTickEnd.class).size(), version);
		}
	}

	@Test
	void waitsFrom1_21_4UntilTheSectionOfThePlayerHasCompiledAndTellsTheServer() {
		TestJavaPlayer player = joined("1.21.11");
		player.protocol().player().onTick(ticked::incrementAndGet);
		load(player, 0, 0);
		player.deliver(LOADING_STARTED);
		player.deliver(teleport(SPAWN, 0.0F, 0.0F, Set.of()));

		for (int tick = 0; tick < 10; tick++) player.tick();
		assertEquals(0, ticked.get(), "a section compiles only with the eight columns around it loaded");
		for (int x = -1; x <= 1; x++)
			for (int z = -1; z <= 1; z++) load(player, x, z);
		player.tick();
		assertEquals(0, ticked.get(), "the frame after a tick compiles the section");
		player.tick();

		assertEquals(1, ticked.get());
		assertEquals(List.of(new ServerboundPlayerLoaded()), player.sent(ServerboundPlayerLoaded.class));
	}

	@Test
	void letsADeadPlayerIntoItsLevelAtOnce() {
		TestJavaPlayer player = joined("1.21.11");
		player.deliver(LOADING_STARTED);
		player.deliver(teleport(SPAWN, 0.0F, 0.0F, Set.of()));
		player.deliver(new ClientboundSetHealth(0.0F, 20, 5.0F));

		player.tick();

		assertEquals(List.of(new ServerboundPlayerLoaded()), player.sent(ServerboundPlayerLoaded.class));
		assertTrue(player.protocol().player().isDead());
	}

	@Test
	void ticksThePlayerOnlyInALoadedChunkBefore1_17AndAlwaysFrom1_17() {
		TestJavaPlayer old = joined("1.16.5");
		old.protocol().player().onTick(ticked::incrementAndGet);
		old.deliver(teleport(SPAWN, 0.0F, 0.0F, Set.of()));
		old.tick();
		assertEquals(0, ticked.get());
		load(old, 0, 0);
		old.tick();
		assertEquals(1, ticked.get());

		TestJavaPlayer current = joined("1.17.1");
		AtomicInteger ticks = new AtomicInteger();
		current.protocol().player().onTick(ticks::incrementAndGet);
		current.deliver(teleport(SPAWN, 0.0F, 0.0F, Set.of()));
		current.tick();
		assertEquals(1, ticks.get(), "the player ticks in a chunk its client lacks");
	}

	@Test
	void goesOnTickingPastAFailingTickOfThePlayerAndStopsATickThatClosed() {
		TestJavaPlayer player = joined("1.21.1");
		player.deliver(teleport(SPAWN, 0.0F, 0.0F, Set.of()));
		player.protocol().player().onTick(() -> {
			throw new IllegalStateException("broken tick");
		});
		Subscription counting = player.protocol().player().onTick(ticked::incrementAndGet);

		player.tick();
		counting.close();
		player.tick();

		assertEquals(1, ticked.get());
	}

	@Test
	void sendsNothingInATickAfterItsConnectionClosedWhileTheConnectionsBindingsEnd() {
		TestJavaPlayer player = joined("1.21.11");
		player.deliver(teleport(SPAWN, 0.0F, 0.0F, Set.of()));
		player.tick();
		player.bindConnection(() -> player::tick);

		player.disconnect();

		assertEquals(1, player.sent(ServerboundClientTickEnd.class).size());
		assertTrue(player.protocol().player().entityId().isEmpty());
	}

	@Test
	void ticksTwentyTimesASecondOnceStartedUntilClosed() throws InterruptedException {
		TestJavaPlayer player = joined("1.21.1");
		player.deliver(teleport(SPAWN, 0.0F, 0.0F, Set.of()));
		player.protocol().player().onTick(ticked::incrementAndGet);

		long started = System.nanoTime();
		player.protocol().start();
		while (ticked.get() < 10) {
			assertTrue(System.nanoTime() - started < Duration.ofSeconds(10).toNanos(), "the client did not tick");
			Thread.sleep(5);
		}
		assertTrue(System.nanoTime() - started >= Duration.ofMillis(400).toNanos(), "ten ticks take half a second");
		player.close();
		int stopped = ticked.get();
		Thread.sleep(150);

		assertEquals(stopped, ticked.get());
	}
}
