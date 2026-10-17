package me.whereareiam.anvil.capability.dialogs.java;

import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtByte;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtCompound;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtFloat;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtInt;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtIntArray;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtList;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtString;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ComponentTextTest {
	@Test
	void keepsTextAsWrittenWithItsChildrenInOrder() {
		NbtCompound message = text("anvil:", text("wel"), text("come", text("!")));

		assertEquals("anvil:welcome!", ComponentText.plain(message));
	}

	@Test
	void ignoresStyle() {
		NbtCompound styled = NbtCompound.builder()
				.put("text", new NbtString("ready"))
				.put("color", new NbtString("green"))
				.put("bold", NbtByte.of(true))
				.build();

		assertEquals("ready", ComponentText.plain(styled));
	}

	@Test
	void readsAStringAsTextAndAListAsComponentsOneAfterAnother() {
		assertEquals("ready", ComponentText.plain(new NbtString("ready")));
		assertEquals("Log in!", ComponentText.plain(new NbtList(List.of(new NbtString("Log "), text("in"), new NbtString("!")))));
	}

	@Test
	void writesATranslationAsItsKeyFollowedByEachArgument() {
		NbtCompound message = translate("chat.type.text", List.of(text("Alice"), new NbtString("hello")), text("!"));

		assertEquals("chat.type.text Alice hello!", ComponentText.plain(message));
	}

	@Test
	void flattensNestedTranslationArgumentsAndNumbers() {
		NbtCompound message = translate("multiplayer.disconnect.kicked", List.of(
				translate("commands.kick.success", List.of(text("Alice", text("_2")), new NbtInt(5), new NbtFloat(2.5F)))));

		assertEquals("multiplayer.disconnect.kicked commands.kick.success Alice_2 5 2.5", ComponentText.plain(message));
	}

	@Test
	void writesATranslationWithoutArgumentsAsItsKey() {
		assertEquals("disconnect.timeout", ComponentText.plain(translate("disconnect.timeout", List.of())));
	}

	@Test
	void writesKeybindsAndSelectorsAsTheirNamesAndIgnoresScoresAndNbt() {
		NbtCompound message = text("press ",
				NbtCompound.EMPTY.with("keybind", new NbtString("key.jump")),
				new NbtString(" near "),
				NbtCompound.EMPTY.with("selector", new NbtString("@p")),
				NbtCompound.EMPTY.with("score", NbtCompound.builder()
						.put("name", new NbtString("Alice"))
						.put("objective", new NbtString("kills"))
						.build()),
				NbtCompound.EMPTY.with("nbt", new NbtString("Inventory")),
				text("!"));

		assertEquals("press key.jump near @p!", ComponentText.plain(message));
	}

	@Test
	void prefersTextToTheOtherContents() {
		NbtCompound both = NbtCompound.builder()
				.put("translate", new NbtString("gui.ok"))
				.put("text", new NbtString("OK"))
				.build();

		assertEquals("OK", ComponentText.plain(both));
	}

	@Test
	void writesAnArrayAsItsNumbers() {
		assertEquals("12", ComponentText.plain(new NbtIntArray(new int[]{1, 2})));
	}

	@Test
	void flattensAnEmptyOrMissingComponentToEmptyText() {
		assertEquals("", ComponentText.plain(new NbtString("")));
		assertEquals("", ComponentText.plain(null));
	}

	private static NbtCompound text(String content, NbtTag... extra) {
		NbtCompound text = NbtCompound.EMPTY.with("text", new NbtString(content));
		if (extra.length == 0) return text;

		return text.with("extra", new NbtList(List.of(extra)));
	}

	private static NbtCompound translate(String key, List<NbtTag> arguments, NbtTag... extra) {
		NbtCompound translate = NbtCompound.EMPTY.with("translate", new NbtString(key));
		if (!arguments.isEmpty()) translate = translate.with("with", new NbtList(arguments));
		if (extra.length == 0) return translate;

		return translate.with("extra", new NbtList(List.of(extra)));
	}
}
