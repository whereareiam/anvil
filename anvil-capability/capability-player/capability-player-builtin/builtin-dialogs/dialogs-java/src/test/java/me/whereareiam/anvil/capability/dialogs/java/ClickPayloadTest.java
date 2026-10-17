package me.whereareiam.anvil.capability.dialogs.java;

import me.whereareiam.anvil.capability.dialogs.model.click.DialogValue;
import me.whereareiam.anvil.capability.dialogs.type.DialogInputType;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtByte;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtCompound;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtFloat;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtInt;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtList;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtString;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClickPayloadTest {
	private static final NbtList LIST = new NbtList(List.of(new NbtInt(1), new NbtInt(2)));

	@Test
	void putsEachValueIntoTheDeclaredPayloadTypedByItsInput() {
		NbtCompound payload = ClickPayload.of(NbtCompound.builder()
				.put("source", new NbtString("dialog"))
				.put("password", new NbtString("old"))
				.build(), List.of(
				new DialogValue("password", DialogInputType.TEXT, "secret"),
				new DialogValue("remember", DialogInputType.BOOLEAN, "true"),
				new DialogValue("hidden", DialogInputType.BOOLEAN, "false"),
				new DialogValue("amount", DialogInputType.NUMBER_RANGE, "2.5"),
				new DialogValue("mode", DialogInputType.SINGLE_OPTION, "hard")
		));

		assertEquals(NbtCompound.builder()
				.put("source", new NbtString("dialog"))
				.put("password", new NbtString("secret"))
				.put("remember", new NbtByte((byte) 1))
				.put("hidden", new NbtByte((byte) 0))
				.put("amount", new NbtFloat(2.5F))
				.put("mode", new NbtString("hard"))
				.build(), payload);
	}

	@Test
	void startsFromAnEmptyCompoundWhenTheActionDeclaredNone() {
		assertEquals(NbtCompound.EMPTY.with("amount", new NbtFloat(1F)),
				ClickPayload.of(null, List.of(new DialogValue("amount", DialogInputType.NUMBER_RANGE, "1"))));
		assertEquals(NbtCompound.EMPTY, ClickPayload.of(null, List.of()));
	}

	@Test
	void keepsTheDeclaredPayloadOfAStaticClick() {
		assertEquals(NbtCompound.builder().put("source", new NbtString("dialog")).put("page", new NbtInt(2)).build(),
				ClickPayload.of(NbtCompound.builder().put("source", new NbtString("dialog")).put("page", new NbtInt(2)).build(),
						List.of()));
	}

	@Test
	void refusesADeclaredPayloadThatIsNotACompound() {
		assertThrows(IllegalArgumentException.class, () -> ClickPayload.of(LIST,
				List.of(new DialogValue("mode", DialogInputType.TEXT, "hard"))));
		assertThrows(IllegalArgumentException.class, () -> ClickPayload.of(LIST, List.of()));
	}

	@Test
	void refusesValuesThatDoNotFitTheirInput() {
		assertThrows(IllegalArgumentException.class, () -> ClickPayload.of(null,
				List.of(new DialogValue("remember", DialogInputType.BOOLEAN, "yes"))));
		assertThrows(IllegalArgumentException.class, () -> ClickPayload.of(null,
				List.of(new DialogValue("amount", DialogInputType.NUMBER_RANGE, "lots"))));
		assertThrows(IllegalArgumentException.class, () -> ClickPayload.of(null,
				List.of(new DialogValue("colour", DialogInputType.OTHER, "red"))));
	}
}
