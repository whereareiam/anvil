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
				.defaultServer(server.getName())
				.server(server.getName())
				.build();

		assertNull(metadata.getDisplayName());
		assertNull(metadata.getDescription());
		assertNull(metadata.getCategory());
		assertTrue(metadata.getTags().isEmpty());
		assertNull(AnvilScenario.builder().name("scenario").entrypoint("paper").build().getMetadata());
		assertNull(PlayerOptions.builder().name("alice").build().getMetadata());
		assertNull(server.getMetadata());
		assertNull(proxy.getMetadata());
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
				.entrypoint(server.getName())
				.metadata(metadata)
				.build();
		assertEquals("registration", scenario.getName());
		assertEquals("paper", server.getName());
		assertEquals("alice", player.getName());
		assertSame(metadata, server.getMetadata());
		assertSame(metadata, player.getMetadata());
		assertSame(metadata, scenario.toBuilder().build().getMetadata());
	}

	@Test
	void tagsAreImmutableSnapshotsAcrossBuilderChanges() {
		Set<String> source = new LinkedHashSet<>(Set.of("auth"));
		PresentationMetadata.PresentationMetadataBuilder builder = PresentationMetadata.builder().tags(source);
		PresentationMetadata original = builder.build();
		source.add("source-change");
		builder.tag("builder-change");
		PresentationMetadata edited = original.toBuilder().tag("proxy").build();

		assertEquals(Set.of("auth"), original.getTags());
		assertEquals(Set.of("auth", "proxy"), edited.getTags());
		assertThrows(UnsupportedOperationException.class, () -> original.getTags().add("mutation"));
	}

	private MinecraftServer server() {
		return MinecraftServer.builder()
				.name("paper")
				.platform("paper")
				.distribution(Distribution.remote("1.21.11", "1"))
				.build();
	}
}
