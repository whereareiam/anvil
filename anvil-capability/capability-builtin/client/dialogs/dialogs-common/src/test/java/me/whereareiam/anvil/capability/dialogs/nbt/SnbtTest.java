package me.whereareiam.anvil.capability.dialogs.nbt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static me.whereareiam.anvil.capability.dialogs.nbt.DialogReaderTest.compound;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SnbtTest {
	private static final Map<String, Object> EVERY_TYPE = compound(
			"byte", (byte) 1,
			"short", (short) 2,
			"int", 3,
			"long", 4L,
			"float", 4.5F,
			"double", 6.0,
			"text", "say \"hi\" \\o/",
			"quoted key", "",
			"list", List.of(1, 2),
			"compound", compound("nested", List.of()),
			"bytes", new byte[]{1, -2},
			"ints", new int[]{3, 4},
			"longs", new long[]{5L}
	);
	private static final String EVERY_TYPE_SNBT = "{byte:1b,short:2s,int:3,long:4l,float:4.5f,double:6.0d,text:\"say \\\"hi\\\" \\\\o/\","
			+ "\"quoted key\":\"\",list:[1,2],compound:{nested:[]},bytes:[B;1b,-2b],ints:[I;3,4],longs:[L;5l]}";

	@Test
	void writesTheCompactFormOfAdventuresWriter() {
		assertEquals(EVERY_TYPE_SNBT, Snbt.write(EVERY_TYPE));
	}

	@Test
	void readsWhatItWritesBack() {
		Map<String, Object> read = Snbt.compound(EVERY_TYPE_SNBT);

		assertEquals(List.copyOf(EVERY_TYPE.keySet()), List.copyOf(read.keySet()));
		assertEquals((byte) 1, read.get("byte"));
		assertEquals((short) 2, read.get("short"));
		assertEquals(3, read.get("int"));
		assertEquals(4L, read.get("long"));
		assertEquals(4.5F, read.get("float"));
		assertEquals(6.0, read.get("double"));
		assertEquals("say \"hi\" \\o/", read.get("text"));
		assertEquals(List.of(1, 2), read.get("list"));
		assertEquals(compound("nested", List.of()), read.get("compound"));
		assertArrayEquals(new byte[]{1, -2}, (byte[]) read.get("bytes"));
		assertArrayEquals(new int[]{3, 4}, (int[]) read.get("ints"));
		assertArrayEquals(new long[]{5L}, (long[]) read.get("longs"));
	}

	@Test
	void readsTheUsualSnbtAroundTheCompactForm() {
		Map<String, Object> read = Snbt.compound(" { 'single' : 'it\\'s' , flag: true, off: false, big: 7L, ratio: 1.5, word: hello, list: [ 'a' , b ] } ");

		assertEquals(List.of("single", "flag", "off", "big", "ratio", "word", "list"), List.copyOf(read.keySet()));
		assertEquals("it's", read.get("single"));
		assertEquals((byte) 1, read.get("flag"));
		assertEquals((byte) 0, read.get("off"));
		assertEquals(7L, read.get("big"));
		assertEquals(1.5, read.get("ratio"));
		assertEquals("hello", read.get("word"));
		assertEquals(List.of("a", "b"), read.get("list"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"[1,2]", "", "{key}", "{key:1", "{key:1} trailing", "{key:\"open}"})
	void refusesTextThatIsNotExactlyOneCompound(String text) {
		assertThrows(IllegalArgumentException.class, () -> Snbt.compound(text));
	}
}
