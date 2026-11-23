package me.whereareiam.anvil.api.model;

import me.whereareiam.anvil.api.model.player.PlayerOptions;
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.process.MinecraftProxy;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PresentationMetadataTest {
	@Test
	void metadataAndEveryPresentationFieldRemainOptional() {
		PresentationMetadata metadata = PresentationMetadata.builder().build();
		MinecraftServer server = server();
		MinecraftProxy proxy = MinecraftProxy.builder()
				.name("proxy")
				.platform("velocity")
				.distribution(Distribution.remote("3.4.0", "1"))
				.defaultServer(server.name())
				.server(server.name())
				.build();

		assertNull(metadata.displayName());
		assertNull(metadata.description());
		assertNull(metadata.category());
		assertTrue(metadata.tags().isEmpty());
		assertNull(AnvilScenario.builder().name("scenario").entrypoint("paper").build().metadata());
		assertNull(PlayerOptions.builder().name("alice").build().metadata());
		assertNull(server.metadata());
		assertNull(proxy.metadata());
	}

	@Test
	void metadataDoesNotChangeScenarioLookupOrComponentIdentities() {
		PresentationMetadata metadata = PresentationMetadata.builder()
				.displayName("Player Registration")
				.description("Registers a new player")
				.category("Authentication")
				.tag("proxy")
				.build();
		MinecraftProcess server = server().toBuilder().metadata(metadata).build();
		PlayerOptions player = PlayerOptions.builder().name("alice").metadata(metadata).build();
		AnvilScenario scenario = AnvilScenario.builder()
				.name("registration")
				.entrypoint(server.name())
				.metadata(metadata)
				.build();
		assertEquals("registration", scenario.name());
		assertEquals("paper", server.name());
		assertEquals("alice", player.name());
		assertSame(metadata, server.metadata());
		assertSame(metadata, player.metadata());
		assertSame(metadata, scenario.toBuilder().build().metadata());
	}

	@Test
	void tagsAreImmutableSnapshotsAcrossBuilderChanges() {
		Set<String> source = new LinkedHashSet<>(Set.of("auth"));
		PresentationMetadata.PresentationMetadataBuilder builder = PresentationMetadata.builder().tags(source);
		PresentationMetadata original = builder.build();
		source.add("source-change");
		builder.tag("builder-change");
		PresentationMetadata edited = original.toBuilder().tag("proxy").build();

		assertEquals(Set.of("auth"), original.tags());
		assertEquals(Set.of("auth", "proxy"), edited.tags());
		assertThrows(UnsupportedOperationException.class, () -> original.tags().add("mutation"));
	}

	private MinecraftServer server() {
		return MinecraftServer.builder()
				.name("paper")
				.platform("paper")
				.distribution(Distribution.remote("1.21.11", "1"))
				.build();
	}
}
