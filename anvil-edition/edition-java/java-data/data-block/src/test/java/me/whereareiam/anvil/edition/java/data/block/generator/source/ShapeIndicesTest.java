package me.whereareiam.anvil.edition.java.data.block.generator.source;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShapeIndicesTest {
	private final ObjectMapper json = new ObjectMapper();

	@Test
	void givesEveryStateTheOneShapeAFileListsForTheBlock() throws IOException {
		assertEquals(List.of(3, 3, 3), json.readValue("3", ShapeIndices.class).forStates(3));
	}

	@Test
	void givesEachStateItsOwnShape() throws IOException {
		assertEquals(List.of(0, 1), json.readValue("[0, 1]", ShapeIndices.class).forStates(2));
	}

	@Test
	void refusesShapesThatDoNotCoverTheStates() throws IOException {
		ShapeIndices indices = json.readValue("[0, 1]", ShapeIndices.class);

		assertThrows(IllegalStateException.class, () -> indices.forStates(3));
	}
}
