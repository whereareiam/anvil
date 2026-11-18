package me.whereareiam.anvil.capability.movement.java.physics.server;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.edition.java.physics.living.attribute.AttributeMap;
import me.whereareiam.anvil.edition.java.physics.living.attribute.LivingAttributes;
import me.whereareiam.anvil.edition.java.protocol.model.entity.state.AttributeSnapshot;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerAttributesTest {
	@Test
	void startsAtThePlayersVanillaDefaults() {
		AttributeMap attributes = player("1.21.11");

		assertEquals(0.1F, attributes.value(LivingAttributes.MOVEMENT_SPEED));
		assertEquals(0.42F, attributes.value(LivingAttributes.JUMP_STRENGTH));
		assertEquals(0.08, attributes.value(LivingAttributes.GRAVITY));
		assertEquals(0.6, attributes.value(LivingAttributes.STEP_HEIGHT));
		assertEquals(0.3, attributes.value(PlayerAttributes.SNEAKING_SPEED));
	}

	@Test
	void takesThePlayersOwnAttributesWhereTheClientsVersionHasThem() {
		AttributeMap older = player("1.19.4");
		AttributeMap current = player("1.21.11");
		List<AttributeSnapshot> sneaking = List.of(new AttributeSnapshot("minecraft:sneaking_speed", 1.0, List.of()));

		older.update(sneaking);
		current.update(sneaking);

		assertEquals(0.3, older.value(PlayerAttributes.SNEAKING_SPEED));
		assertEquals(1.0, current.value(PlayerAttributes.SNEAKING_SPEED));
	}

	private static @NotNull AttributeMap player(@NotNull String version) {
		return new AttributeMap(MinecraftVersion.parse(version), PlayerAttributes.supplier());
	}
}
