package me.whereareiam.anvil.protocol.mcprotocol.provider;

import me.whereareiam.anvil.capability.protocol.api.player.channel.CapabilityChannel;
import me.whereareiam.anvil.capability.protocol.api.player.channel.MessageChannel;
import me.whereareiam.anvil.capability.protocol.api.player.channel.Subscription;
import me.whereareiam.anvil.capability.protocol.api.model.EventDescriptor;
import me.whereareiam.anvil.capability.protocol.api.model.player.PlayerConnectionEvent;
import me.whereareiam.anvil.capability.inventory.model.SlotSelection;
import me.whereareiam.anvil.capability.messages.model.MessageText;
import me.whereareiam.anvil.capability.binding.TypedCapabilityChannel;
import me.whereareiam.anvil.environment.cache.filesystem.FileCache;
import me.whereareiam.anvil.environment.provisioning.artifact.HttpArtifactAcquirer;
import me.whereareiam.anvil.launcher.assembly.provisioning.CacheArtifactStorage;
import me.whereareiam.anvil.protocol.api.model.PlayerRequest;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayer;
import me.whereareiam.anvil.protocol.api.provider.ProtocolBackend;
import me.whereareiam.anvil.protocol.mcprotocol.worker.fixture.EventWorkerExtension;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class PlayerChannelEventIsolationTest {
	@TempDir
	Path temporary;

	@ParameterizedTest
	@CsvSource({"1.21.11,false", "1.21.11,true", "26.1.2,false", "26.1.2,true"})
	void listenerFailuresStayWithOnePlayerWhileAnotherIssuesRpcInsideCallback(String version, boolean badSchema) throws Exception {
		try (var artifacts = new HttpArtifactAcquirer(temporary, new CacheArtifactStorage(new FileCache(temporary)), false, false, 4);
			 ProtocolBackend backend = new McProtocolProvider().create(temporary, artifacts::obtain)
		) {
			ProtocolPlayer alice = backend.create(request("Alice", version));
			ProtocolPlayer bob = backend.create(request("Bob", version));
			CapabilityChannel aliceChannel = channel(alice);
			CapabilityChannel bobChannel = channel(bob);
			var entered = new CountDownLatch(1);
			var proceed = new CountDownLatch(1);
			var nested = new CompletableFuture<MessageText>();
			try {
				bobChannel.subscribe(EventWorkerExtension.CHANGED, message -> {
					entered.countDown();
					try {
						if (!proceed.await(3, TimeUnit.SECONDS)) throw new IllegalStateException("callback gate timed out");
						nested.complete(bobChannel.request(EventWorkerExtension.ECHO, message));
					} catch (InterruptedException interrupted) {
						Thread.currentThread().interrupt();
						throw new IllegalStateException(interrupted);
					}
				});
				if (badSchema)
					aliceChannel.subscribe(new EventDescriptor<>(EventWorkerExtension.CHANGED.getId(), SlotSelection.class), ignored -> fail("schema must reject this event"));
				else
					aliceChannel.subscribe(EventWorkerExtension.CHANGED, ignored -> { throw new IllegalArgumentException("custom listener failed"); });

				bobChannel.request(EventWorkerExtension.EMIT, new MessageText("nested reply"));
				assertTrue(entered.await(3, TimeUnit.SECONDS));
				aliceChannel.request(EventWorkerExtension.EMIT, new MessageText("wrong shape"));
				IllegalStateException failure = assertThrows(IllegalStateException.class,
						() -> aliceChannel.await(() -> false, "observe a value", Duration.ofSeconds(3)));
				assertTrue(failure.getMessage().contains("Alice"));
				assertTrue(failure.getMessage().contains(EventWorkerExtension.CHANGED.getId()));
				assertInstanceOf(IllegalArgumentException.class, failure.getCause());

				proceed.countDown();
				assertEquals(new MessageText("nested reply"), nested.get(3, TimeUnit.SECONDS));
				assertEquals(new MessageText("still open"), bobChannel.request(EventWorkerExtension.ECHO, new MessageText("still open")));
				assertSame(failure, assertThrows(IllegalStateException.class, alice::destroy));
			} finally {
				proceed.countDown();
				try { alice.destroy(); } finally { bob.destroy(); }
			}
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"1.21.11", "26.1.2"})
	void destructionDrainsAcceptedCallbacksBeforeRemovingTheirChildPlayer(String version) throws Exception {
		try (var artifacts = new HttpArtifactAcquirer(temporary, new CacheArtifactStorage(new FileCache(temporary)), false, false, 4);
			 ProtocolBackend backend = new McProtocolProvider().create(temporary, artifacts::obtain);
			 var closers = Executors.newVirtualThreadPerTaskExecutor()
		) {
			ProtocolPlayer player = backend.create(request("Alice", version));
			CapabilityChannel channel = channel(player);
			var entered = new CountDownLatch(1);
			var proceed = new CountDownLatch(1);
			var closing = new CountDownLatch(1);
			var callback = new CompletableFuture<MessageText>();
			try {
				channel.subscribe(EventWorkerExtension.CHANGED, message -> {
					entered.countDown();
					await(proceed);
					callback.complete(channel.request(EventWorkerExtension.ECHO, message));
				});
				channel.request(EventWorkerExtension.EMIT, new MessageText("drained"));
				assertTrue(entered.await(3, TimeUnit.SECONDS));
				var destruction = closers.submit(() -> { closing.countDown(); player.destroy(); });
				assertTrue(closing.await(3, TimeUnit.SECONDS));
				proceed.countDown();
				assertEquals(new MessageText("drained"), callback.get(3, TimeUnit.SECONDS));
				destruction.get(3, TimeUnit.SECONDS);
				assertTrue(player.destroyed());
			} finally { proceed.countDown(); player.destroy(); }
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"1.21.11", "26.1.2"})
	void destructionInsideCallbackDoesNotJoinItselfOrReplayQueuedNormalEvents(String version) throws Exception {
		try (var artifacts = new HttpArtifactAcquirer(temporary, new CacheArtifactStorage(new FileCache(temporary)), false, false, 4);
			 ProtocolBackend backend = new McProtocolProvider().create(temporary, artifacts::obtain)
		) {
			ProtocolPlayer player = backend.create(request("Alice", version));
			ProtocolPlayer other = backend.create(request("Bob", version));
			CapabilityChannel channel = channel(player);
			var entered = new CountDownLatch(1);
			var proceed = new CountDownLatch(1);
			var notified = new CountDownLatch(1);
			var callbacks = new AtomicInteger();
			try {
				channel.subscribe(EventWorkerExtension.CHANGED, message -> {
					callbacks.incrementAndGet();
					entered.countDown();
					await(proceed);
					player.destroy();
				});
				channel.subscribe(EventWorkerExtension.CHANGED, ignored -> {
					callbacks.incrementAndGet();
					channel.request(EventWorkerExtension.ECHO, new MessageText("must be canceled"));
				});
				channel.subscribe(PlayerConnectionEvent.DESTROYED, ignored -> notified.countDown());
				channel.request(EventWorkerExtension.EMIT, new MessageText("first"));
				assertTrue(entered.await(3, TimeUnit.SECONDS));
				channel.request(EventWorkerExtension.EMIT, new MessageText("queued"));
				proceed.countDown();
				assertTrue(notified.await(3, TimeUnit.SECONDS));
				assertEquals(1, callbacks.get());
				assertTrue(player.destroyed());
				assertEquals(new MessageText("other player"), channel(other)
						.request(EventWorkerExtension.ECHO, new MessageText("other player")));
			} finally {
				proceed.countDown();
				try { player.destroy(); } finally { other.destroy(); }
			}
		}
	}

	private void await(CountDownLatch latch) {
		try {
			if (!latch.await(3, TimeUnit.SECONDS)) throw new IllegalStateException("callback gate timed out");
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(interrupted);
		}
	}

	private CapabilityChannel channel(ProtocolPlayer player) {
		var raw = player.channel().orElseThrow();
		return new TypedCapabilityChannel(new MessageChannel() {
			public byte @NotNull [] request(@NotNull String operation, byte @NotNull [] request) { return raw.request(operation, request); }
			public @NotNull Subscription subscribe(@NotNull String event, @NotNull Consumer<byte[]> listener) {
				var subscription = raw.subscribe(event, listener);
				return subscription::close;
			}
			public void await(@NotNull BooleanSupplier condition, @NotNull String description, @NotNull Duration timeout) { raw.await(condition, description, timeout); }
			public @NotNull Set<String> installedCapabilities() { return raw.installedCapabilities(); }
		});
	}

	private PlayerRequest request(String name, String version) {
		return PlayerRequest.builder().name(name).clientVersion(version)
				.address(new InetSocketAddress("localhost", 9)).build();
	}
}
