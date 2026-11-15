package me.whereareiam.anvil.protocol.java.block.generator.facts;

import com.fasterxml.jackson.databind.JsonMappingException;
import me.whereareiam.anvil.protocol.java.block.model.movement.StuckSpeed;
import me.whereareiam.anvil.protocol.java.block.type.BlockKind;
import me.whereareiam.anvil.protocol.java.block.type.CollisionKind;
import me.whereareiam.anvil.protocol.java.block.type.MovementBehaviour;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockFactsTest {
	@TempDir
	Path directory;

	@Test
	void mergesTheEntriesOfOneBlockAndWidensFloatsAsVanillaDoes() throws IOException {
		BlockFacts facts = read("""
				[[block]]
				names = ["minecraft:powder_snow"]
				source = "PowderSnowBlock.entityInside"
				stuck = { horizontal = "0.9F", vertical = "1.5D" }

				[[block]]
				names = ["minecraft:powder_snow", "minecraft:scaffolding"]
				source = "dynamic shapes"
				collision = "DYNAMIC"

				[[block]]
				names = ["minecraft:scaffolding"]
				source = "climbable tag"
				behaviours = ["CLIMBABLE"]

				[[block]]
				names = ["minecraft:snow"]
				source = "SnowLayerBlock.LAYERS"
				friction = "0.98F"
				kind = "FENCE"
				property-start = { layers = 1 }
				""");

		BlockFact snow = facts.of("minecraft:powder_snow");
		assertEquals(new StuckSpeed((double) 0.9F, 1.5), snow.getMovement().getStuckSpeed());
		assertEquals(CollisionKind.DYNAMIC, snow.getCollision());
		assertEquals(Set.of(MovementBehaviour.CLIMBABLE), facts.of("minecraft:scaffolding").getMovement().getBehaviours());
		assertEquals(0.98F, facts.of("minecraft:snow").getMovement().getFriction());
		assertEquals(BlockKind.FENCE, facts.of("minecraft:snow").getKind());
		assertEquals(Map.of("layers", 1), facts.of("minecraft:snow").getPropertyStarts());
		assertEquals(BlockFact.DEFAULT, facts.of("minecraft:stone"));
		assertEquals(Set.of("minecraft:powder_snow", "minecraft:scaffolding", "minecraft:snow"), facts.names());
	}

	@Test
	void refusesAValueSetTwiceForOneBlock() {
		IllegalStateException failure = assertThrows(IllegalStateException.class, () -> read("""
				[[block]]
				names = ["minecraft:ice"]
				source = "a"
				friction = "0.98F"

				[[block]]
				names = ["minecraft:ice"]
				source = "b"
				friction = "0.98F"
				"""));

		assertEquals("block-facts.toml sets friction of minecraft:ice more than once", failure.getMessage());
	}

	@Test
	void refusesNamingADefault() {
		IllegalStateException failure = assertThrows(IllegalStateException.class,
				() -> read("[[block]]\nnames = [\"minecraft:ice\"]\nsource = \"a\"\nkind = \"OTHER\"\n"));

		assertTrue(failure.getMessage().startsWith("block-facts.toml names kind OTHER for minecraft:ice"), failure.getMessage());
	}

	@Test
	void refusesAnEntryWithoutItsSourceOrWithAForeignName() {
		assertThrows(IllegalStateException.class, () -> read("[[block]]\nnames = [\"minecraft:ice\"]\nsource = \" \"\n"));
		IllegalStateException foreign = assertThrows(IllegalStateException.class,
				() -> read("[[block]]\nnames = [\"example:ice\"]\nsource = \"a\"\n"));

		assertEquals("block-facts.toml block #1 names 'example:ice', which is not a minecraft: block name", foreign.getMessage());
	}

	@Test
	void refusesUnknownFieldsAndConstants() {
		assertThrows(JsonMappingException.class, () -> read("[[block]]\nnames = [\"minecraft:ice\"]\nsource = \"a\"\nslippery = true\n"));
		assertThrows(JsonMappingException.class,
				() -> read("[[block]]\nnames = [\"minecraft:ice\"]\nsource = \"a\"\nbehaviours = [\"SLIPPERY\"]\n"));
	}

	private BlockFacts read(String text) throws IOException {
		Path file = directory.resolve("block-facts.toml");
		Files.writeString(file, text);
		return BlockFacts.read(file);
	}
}
