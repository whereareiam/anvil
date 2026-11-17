package me.whereareiam.anvil.protocol.java.worker.position;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalPlayerBehaviourTest {
	@Test
	void appliesFromTheVersionThatBroughtABehaviour() {
		assertFalse(LocalPlayerBehaviour.PLAYER_LOADED.appliesTo(version("1.21.1")));
		assertTrue(LocalPlayerBehaviour.PLAYER_LOADED.appliesTo(version("1.21.4")));
		assertTrue(LocalPlayerBehaviour.CLAMPED_TELEPORT_PITCH.appliesTo(version("26.1.2")));
	}

	@Test
	void appliesUntilTheVersionThatEndedABehaviour() {
		assertTrue(LocalPlayerBehaviour.TELEPORT_CONFIRMATION.appliesTo(version("26.2")));
		assertFalse(LocalPlayerBehaviour.TELEPORT_CONFIRMATION.appliesTo(version("26.3")));
		assertTrue(LocalPlayerBehaviour.TICKS_ONLY_IN_LOADED_CHUNK.appliesTo(version("1.16.5")));
		assertFalse(LocalPlayerBehaviour.TICKS_ONLY_IN_LOADED_CHUNK.appliesTo(version("1.17.1")));
	}

	@Test
	void collectsTheBehavioursOfAVersionAndReadsChunksOnlyWhereTheyNeedThem() {
		Set<LocalPlayerBehaviour> between = LocalPlayerBehaviour.of(version("1.21.1"));

		assertEquals(Set.of(LocalPlayerBehaviour.TELEPORT_CONFIRMATION), between);
		assertFalse(LocalPlayerBehaviour.readsChunks(between));
		assertTrue(LocalPlayerBehaviour.readsChunks(LocalPlayerBehaviour.of(version("1.16.5"))));
		assertTrue(LocalPlayerBehaviour.readsChunks(LocalPlayerBehaviour.of(version("26.3"))));
	}

	private static @NotNull MinecraftVersion version(@NotNull String text) {
		return MinecraftVersion.parse(text);
	}
}
