package me.whereareiam.anvil.capability.dialogs.nbt;

import me.whereareiam.anvil.capability.protocol.api.model.nbt.NbtTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static me.whereareiam.anvil.capability.dialogs.nbt.DialogReaderTest.compound;
import static me.whereareiam.anvil.capability.dialogs.nbt.DialogReaderTest.tag;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ComponentTextTest {
	@Test
	void flattensTextKeybindAndSelectorPartsWithTheirChildren() {
		NbtTag component = compound("text", "Press ", "extra", List.of(
				compound("keybind", "key.jump"),
				compound("", " near "),
				compound("selector", "@p"),
				compound("score", compound("name", "@s", "objective", "kills"))
		));

		assertEquals("Press key.jump near @p", ComponentText.plain(component));
	}

	@Test
	void writesATranslationAsItsKeyFollowedByEachArgument() {
		NbtTag component = compound("translate", "multiplayer.player.joined", "with", List.of(
				compound("text", "Alice"),
				compound("", 3)
		));

		assertEquals("multiplayer.player.joined Alice 3", ComponentText.plain(component));
	}

	@Test
	void followsTheTypeEntryAndJoinsTheElementsOfAList() {
		NbtTag typed = compound("type", "keybind", "text", "ignored", "keybind", "key.use");
		NbtTag list = tag(List.of(compound("", "A"), compound("text", "B"), compound("", "C")));

		assertEquals("key.use", ComponentText.plain(typed));
		assertEquals("ABC", ComponentText.plain(list));
		assertEquals("Plain", ComponentText.plain(tag("Plain")));
		assertEquals("", ComponentText.plain(null));
	}
}
