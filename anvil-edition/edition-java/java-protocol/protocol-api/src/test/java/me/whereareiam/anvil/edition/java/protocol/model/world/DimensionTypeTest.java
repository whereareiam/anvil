package me.whereareiam.anvil.edition.java.protocol.model.world;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DimensionTypeTest {
	@Test
	void knowsFastLavaOnlyInTheVanillaNether() {
		assertTrue(Objects.requireNonNull(DimensionType.known("minecraft:the_nether")).fastLava());
	}

	@ParameterizedTest
	@ValueSource(strings = {"minecraft:overworld", "minecraft:overworld_caves", "minecraft:the_end"})
	void knowsSlowLavaInEveryOtherVanillaType(String key) {
		assertFalse(Objects.requireNonNull(DimensionType.known(key)).fastLava());
	}

	@Test
	void describesATypeByItsEffectsWithSlowLava() {
		assertFalse(DimensionType.withEffects("example:hot", "minecraft:the_nether", 0, 256).fastLava());
	}
}
