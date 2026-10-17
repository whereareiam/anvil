package me.whereareiam.anvil.capability.dialogs.nbt;

import me.whereareiam.anvil.capability.dialogs.model.click.DialogClick;
import me.whereareiam.anvil.capability.dialogs.model.click.DialogValue;
import me.whereareiam.anvil.capability.dialogs.type.DialogInputType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static me.whereareiam.anvil.capability.dialogs.nbt.DialogReaderTest.compound;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClickPayloadTest {
	@Test
	void putsEachValueIntoThePayloadWithTheTypeOfItsInput() {
		Map<String, Object> payload = ClickPayload.of(DialogClick.custom("example:submit", "{source:\"dialog\",password:\"old\"}", List.of(
				new DialogValue("password", DialogInputType.TEXT, "secret"),
				new DialogValue("remember", DialogInputType.BOOLEAN, "true"),
				new DialogValue("amount", DialogInputType.NUMBER_RANGE, "2.5"),
				new DialogValue("mode", DialogInputType.SINGLE_OPTION, "hard")
		)));

		assertEquals(compound("source", "dialog", "password", "secret", "remember", (byte) 1, "amount", 2.5F, "mode", "hard"), payload);
		assertEquals(List.of("source", "password", "remember", "amount", "mode"), List.copyOf(payload.keySet()));
	}

	@Test
	void startsFromAnEmptyCompoundWithoutPayload() {
		assertEquals(Map.of(), ClickPayload.of(DialogClick.custom("example:ping", null, List.of())));
	}

	@Test
	void refusesAPayloadThatIsNotACompound() {
		assertThrows(IllegalArgumentException.class, () -> ClickPayload.of(DialogClick.custom("example:ping", "[1,2]", List.of())));
	}
}
