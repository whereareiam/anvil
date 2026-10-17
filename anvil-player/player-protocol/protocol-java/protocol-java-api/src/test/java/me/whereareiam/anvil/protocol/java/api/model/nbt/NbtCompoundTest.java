package me.whereareiam.anvil.protocol.java.api.model.nbt;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NbtCompoundTest {
	private static final NbtCompound PAYLOAD = NbtCompound.builder()
			.put("source", new NbtString("dialog"))
			.put("page", new NbtInt(2))
			.put("options", new NbtList(List.of(new NbtString("easy"))))
			.put("nested", NbtCompound.EMPTY)
			.build();

	@Test
	void keepsTheOrderItsEntriesWerePutIn() {
		assertEquals(List.of("source", "page", "options", "nested"), List.copyOf(PAYLOAD.getEntries().keySet()));
	}

	@Test
	void findsATagOnlyWhenItHasTheAskedType() {
		assertEquals(Optional.of(new NbtString("dialog")), PAYLOAD.string("source"));
		assertEquals(Optional.of(new NbtInt(2)), PAYLOAD.number("page"));
		assertEquals(Optional.of(new NbtList(List.of(new NbtString("easy")))), PAYLOAD.list("options"));
		assertEquals(Optional.of(NbtCompound.EMPTY), PAYLOAD.compound("nested"));
		assertEquals(Optional.of(new NbtInt(2)), PAYLOAD.find("page", NbtInt.class));

		assertEquals(Optional.empty(), PAYLOAD.string("page"));
		assertEquals(Optional.empty(), PAYLOAD.number("source"));
		assertEquals(Optional.empty(), PAYLOAD.compound("missing"));
		assertEquals(Optional.empty(), PAYLOAD.find("page", NbtLong.class));
	}

	@Test
	void tellsWhichKeysItHolds() {
		assertTrue(PAYLOAD.contains("page"));
		assertFalse(PAYLOAD.contains("missing"));
		assertEquals(new NbtInt(2), PAYLOAD.get("page"));
		assertNull(PAYLOAD.get("missing"));
	}

	@Test
	void putsATagIntoACopyReplacingAnEntryInPlaceAndAddingANewKeyLast() {
		NbtCompound changed = PAYLOAD
				.with("page", new NbtString("two"))
				.with("remember", NbtByte.of(true));

		assertEquals(List.of("source", "page", "options", "nested", "remember"), List.copyOf(changed.getEntries().keySet()));
		assertEquals(Optional.of(new NbtString("two")), changed.string("page"));
		assertEquals(Optional.of(new NbtByte((byte) 1)), changed.find("remember", NbtByte.class));
		assertEquals(Optional.of(new NbtInt(2)), PAYLOAD.number("page"));
		assertFalse(PAYLOAD.contains("remember"));
	}

	@Test
	void staysUnchangedWhateverHappensToTheMapItWasCreatedFrom() {
		Map<String, NbtTag> entries = new LinkedHashMap<>();
		entries.put("page", new NbtInt(2));
		NbtCompound compound = new NbtCompound(entries);

		entries.put("page", new NbtInt(3));

		assertEquals(Optional.of(new NbtInt(2)), compound.number("page"));
		assertThrows(UnsupportedOperationException.class, () -> compound.getEntries().put("page", new NbtInt(4)));
	}

	@Test
	void equalsACompoundWithTheSameEntries() {
		assertEquals(new NbtCompound(Map.of("page", new NbtInt(2))), NbtCompound.EMPTY.with("page", new NbtInt(2)));
	}
}
