package me.whereareiam.anvil.testkit.tests.runtime.provider;

import me.whereareiam.anvil.api.exception.CapabilityUnavailableException;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.api.model.player.PlayerConnection;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.capability.cookies.Cookies;
import me.whereareiam.anvil.capability.messages.Messages;
import me.whereareiam.anvil.capability.session.Session;
import me.whereareiam.anvil.capability.sounds.Sounds;
import me.whereareiam.anvil.launcher.assembly.PlayerComposition;
import me.whereareiam.anvil.protocol.api.library.ProtocolLibrary;
import me.whereareiam.anvil.protocol.api.library.ProtocolLibraryRegistry;
import me.whereareiam.anvil.protocol.api.model.GameConnection;
import me.whereareiam.anvil.protocol.api.model.PlayerRequest;
import me.whereareiam.anvil.protocol.api.model.ProtocolLibraryContext;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayer;
import me.whereareiam.anvil.testkit.support.FixtureArtifacts;
import me.whereareiam.anvil.testkit.support.TestExtensionLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that a protocol library built outside Anvil, on the public worker kits alone, runs the worker extensions
 * of the built-in capabilities: the fixture library speaks the Java Edition protocol in-process to a server that only
 * echoes chat.
 */
class ExternalJavaLibraryIntegrationTest {
	private static final String LIBRARY = "java-fixture";

	@TempDir
	Path temporary;

	@Test
	void composesTheBuiltInCapabilitiesOnAnotherLibrarySpeakingTheJavaEditionProtocol() throws Exception {
		try (var ignored = new TestExtensionLoader(FixtureArtifacts.javaLibrary(), false);
		     ProtocolLibrary library = ProtocolLibraryRegistry.discover().require(LIBRARY).create(context())) {
			ProtocolPlayer created = library.create(PlayerRequest.builder()
					.name("Alice")
					.clientVersion(MinecraftVersion.parse("1.21.11"))
					.connection(GameConnection.builder().address(new InetSocketAddress("localhost", 25565)).build())
					.build());
			SimulatedPlayer alice = PlayerComposition.create(List.of(LIBRARY))
					.compose(created, PlayerConnection.to("server"), null, removed -> { });

			Session session = alice.capability(Session.class);
			session.connect();
			session.connected();
			Messages messages = alice.capability(Messages.class);
			messages.chat("hello");
			messages.command("anvil ping");

			assertEquals("<Alice> hello", messages.received("<Alice> hello"));
			assertEquals("ran /anvil ping", messages.received("ran /anvil ping"));
			session.disconnect();
			session.disconnected();
			assertFalse(session.state().connected());

			var cookies = assertThrows(CapabilityUnavailableException.class, () -> alice.capability(Cookies.class));
			assertTrue(cookies.getMessage().contains("the player's release has no "), cookies.getMessage());
			var sounds = assertThrows(CapabilityUnavailableException.class, () -> alice.capability(Sounds.class));
			assertTrue(sounds.getMessage().contains("the loaded " + LIBRARY + " release does not carry"),
					sounds.getMessage());
			alice.destroy();
		}
	}

	private ProtocolLibraryContext context() {
		return ProtocolLibraryContext.builder()
				.workers(launch -> {
					throw new AssertionError("The in-process library starts no worker");
				})
				.cacheDirectory(temporary.resolve("cache"))
				.accountsDirectory(temporary.resolve("accounts"))
				.artifacts((uri, destination, sha256) -> {
					throw new AssertionError("The in-process library resolves no " + uri);
				})
				.build();
	}
}
