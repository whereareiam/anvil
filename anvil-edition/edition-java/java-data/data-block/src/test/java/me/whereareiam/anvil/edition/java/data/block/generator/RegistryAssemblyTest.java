package me.whereareiam.anvil.edition.java.data.block.generator;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.whereareiam.anvil.edition.java.data.block.generator.facts.BlockFacts;
import me.whereareiam.anvil.edition.java.data.block.generator.source.MinecraftDataBlock;
import me.whereareiam.anvil.edition.java.data.block.generator.source.MinecraftDataShapes;
import me.whereareiam.anvil.edition.java.data.block.model.BlockState;
import me.whereareiam.anvil.edition.java.data.block.model.BlockType;
import me.whereareiam.anvil.edition.java.data.block.model.collision.CollisionBox;
import me.whereareiam.anvil.edition.java.data.block.model.collision.CollisionShape;
import me.whereareiam.anvil.edition.java.data.block.type.BlockKind;
import me.whereareiam.anvil.edition.java.data.block.type.FluidType;
import me.whereareiam.anvil.edition.java.data.block.type.MovementBehaviour;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RegistryAssemblyTest {
	private static final String CONTEXT = "minecraft-data data/pc/1.16.2/blocks.json for protocol 754";
	private static final String SHAPES = """
			{"blocks": {"air": 0, "snow": [0, 1]}, "shapes": {"0": [], "1": [[0, 0, 0, 1, 0.125, 1]]}}
			""";

	private final ObjectMapper json = new ObjectMapper();
	private BlockFacts facts;

	@TempDir
	Path directory;

	@BeforeEach
	void readFacts() throws IOException {
		Path file = directory.resolve("block-facts.toml");
		Files.writeString(file, """
				[[block]]
				names = ["minecraft:air"]
				source = "AirBlock"
				kind = "AIR"

				[[block]]
				names = ["minecraft:snow"]
				source = "SnowLayerBlock"
				friction = "0.98F"
				bounce = "0.66F"
				behaviours = ["SLIME_STEP", "CLIMBABLE"]
				property-start = { layers = 1 }
				""");
		facts = BlockFacts.read(file);
	}

	@Test
	void assemblesBlocksWithTheirFactsPropertiesAndShapes() throws IOException {
		RegistryAssembly.Assembled assembled = assemble(blocks(null, 2));

		BlockType snow = assembled.blocks().get(1);
		assertEquals(BlockKind.AIR, assembled.blocks().getFirst().kind());
		assertEquals(List.of("1", "2"), snow.properties().getFirst().values());
		assertEquals(0.98F, snow.movement().friction());
		assertEquals((double) 0.66F, snow.movement().bounceFactor());
		assertEquals(Set.of(MovementBehaviour.SLIME_STEP, MovementBehaviour.CLIMBABLE), snow.movement().behaviours());
		BlockState thin = assembled.states()[2];
		assertEquals(Map.of("layers", "2"), thin.properties());
		assertEquals(new CollisionShape(List.of(new CollisionBox(0, 0, 0, 1, 0.125, 1))), thin.collision());
		assertSame(CollisionShape.EMPTY, assembled.states()[1].collision());
		assertEquals(FluidType.NONE, thin.fluid());
		assertEquals(Set.of("minecraft:air", "minecraft:snow"), assembled.names());
	}

	@Test
	void refusesListedIntegerValuesThatDisagreeWithTheFacts() {
		IllegalStateException failure = assertThrows(IllegalStateException.class, () -> assemble(blocks("[\"0\", \"1\"]", 2)));

		assertEquals(CONTEXT + ": minecraft:snow integer property layers lists [0, 1], but block-facts.toml gives it the "
				+ "start 1; add or fix its property-start", failure.getMessage());
	}

	@Test
	void refusesStatesTheirPropertiesDoNotCombineTo() {
		IllegalStateException failure = assertThrows(IllegalStateException.class, () -> assemble(blocks(null, 3)));

		assertEquals(CONTEXT + ": minecraft:snow has 3 states, but its properties combine to 2", failure.getMessage());
	}

	@Test
	void refusesABlockWithoutShapesForEachState() {
		String shapes = "{\"blocks\": {\"air\": 0, \"snow\": [0]}, \"shapes\": {\"0\": []}}";

		assertThrows(IllegalStateException.class, () -> assemble(blocks(null, 2), shapes));
	}

	private RegistryAssembly.Assembled assemble(String blocks) throws IOException {
		return assemble(blocks, SHAPES);
	}

	private RegistryAssembly.Assembled assemble(String blocks, String shapes) throws IOException {
		return RegistryAssembly.assemble(CONTEXT, json.readValue(blocks, new TypeReference<List<MinecraftDataBlock>>() {
		}), json.readValue(shapes, MinecraftDataShapes.class), facts);
	}

	private static String blocks(String snowValues, int snowStates) {
		String values = snowValues == null ? "" : ", \"values\": " + snowValues;
		return """
				[
				  {"id": 0, "name": "air", "minStateId": 0, "maxStateId": 0, "defaultState": 0, "states": [], "hardness": 0},
				  {"id": 1, "name": "snow", "minStateId": 1, "maxStateId": %d, "defaultState": 1,
				   "states": [{"name": "layers", "type": "int", "num_values": 2%s}]}
				]
				""".formatted(snowStates, values);
	}
}
