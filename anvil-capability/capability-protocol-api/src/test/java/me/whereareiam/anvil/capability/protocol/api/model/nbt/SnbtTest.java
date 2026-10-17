package me.whereareiam.anvil.capability.protocol.api.model.nbt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SnbtTest {
	private static final NbtCompound EVERY_TYPE = NbtCompound.builder()
			.entry("byte", NbtByte.of((byte) 1))
			.entry("short", NbtShort.of((short) 2))
			.entry("int", NbtInt.of(3))
			.entry("long", NbtLong.of(4L))
			.entry("float", NbtFloat.of(4.5F))
			.entry("double", NbtDouble.of(6.0))
			.entry("text", NbtString.of("say \"hi\" \\o/"))
			.entry("quoted key", NbtString.of(""))
			.entry("list", NbtList.of(NbtInt.of(1), NbtInt.of(2)))
			.entry("compound", NbtCompound.builder().entry("nested", NbtList.EMPTY).build())
			.entry("bytes", NbtByteArray.of((byte) 1, (byte) -2))
			.entry("ints", NbtIntArray.of(3, 4))
			.entry("longs", NbtLongArray.of(5L))
			.build();
	private static final String EVERY_TYPE_SNBT = "{byte:1b,short:2s,int:3,long:4l,float:4.5f,double:6.0d,text:\"say \\\"hi\\\" \\\\o/\","
			+ "\"quoted key\":\"\",list:[1,2],compound:{nested:[]},bytes:[B;1b,-2b],ints:[I;3,4],longs:[L;5l]}";

	@Test
	void writesTheCompactForm() {
		assertEquals(EVERY_TYPE_SNBT, EVERY_TYPE.toSnbt());
	}

	@Test
	void readsWhatItWritesBackInOrder() {
		NbtCompound read = Snbt.readCompound(EVERY_TYPE_SNBT);

		assertEquals(EVERY_TYPE, read);
		assertEquals(List.copyOf(EVERY_TYPE.getEntries().keySet()), List.copyOf(read.getEntries().keySet()));
	}

	@Test
	void readsTheUsualSnbtAroundTheCompactForm() {
		NbtCompound read = Snbt.readCompound(" { 'single' : 'it\\'s' , flag: true, off: false, big: 7L, ratio: 1.5, word: hello, list: [ 'a' , b ] } ");

		assertEquals(NbtCompound.builder()
				.entry("single", NbtString.of("it's"))
				.entry("flag", NbtByte.of(true))
				.entry("off", NbtByte.of(false))
				.entry("big", NbtLong.of(7L))
				.entry("ratio", NbtDouble.of(1.5))
				.entry("word", NbtString.of("hello"))
				.entry("list", NbtList.of(NbtString.of("a"), NbtString.of("b")))
				.build(), read);
	}

	@ParameterizedTest
	@ValueSource(strings = {"[1,2]", "", "{key}", "{key:1", "{key:1} trailing", "{key:\"open}", "{mixed:[1,a]}"})
	void refusesTextThatIsNotExactlyOneCompound(String text) {
		assertThrows(IllegalArgumentException.class, () -> Snbt.readCompound(text));
	}
}
