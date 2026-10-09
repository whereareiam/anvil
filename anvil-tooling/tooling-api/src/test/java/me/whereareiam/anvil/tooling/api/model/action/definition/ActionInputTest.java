package me.whereareiam.anvil.tooling.api.model.action.definition;

import me.whereareiam.anvil.tooling.api.type.action.ActionInputType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ActionInputTest {
	@Test
	void validatesEveryPortableScalarType() {
		for (Case value : List.of(
				new Case(ActionInputType.TEXT, "hello world", true),
				new Case(ActionInputType.INTEGER, "-9223372036854775808", true),
				new Case(ActionInputType.INTEGER, "1.5", false),
				new Case(ActionInputType.DECIMAL, "0.0000000000000000001", true),
				new Case(ActionInputType.DECIMAL, "NaN", false),
				new Case(ActionInputType.BOOLEAN, "TRUE", true),
				new Case(ActionInputType.BOOLEAN, "yes", false),
				new Case(ActionInputType.CHOICE, "first", true),
				new Case(ActionInputType.CHOICE, "other", false))) {
			var input = ActionInput.builder().name("value").type(value.type).choices(List.of("first", "second")).build();
			if (value.valid) assertDoesNotThrow(() -> input.validate(value.value));
			else assertThrows(IllegalArgumentException.class, () -> input.validate(value.value));
		}
	}

	@Test
	void distinguishesRequiredAndOptionalValuesAndFreezesDeclaredChoices() {
		List<String> choices = new ArrayList<>(List.of("first"));
		var input = ActionInput.builder().name("value").type(ActionInputType.CHOICE).choices(choices).build();
		choices.add("second");
		assertEquals(List.of("first"), input.getChoices());
		assertThrows(UnsupportedOperationException.class, () -> input.getChoices().add("other"));
		assertThrows(IllegalArgumentException.class, () -> input.validate(null));
		assertDoesNotThrow(() -> input.toBuilder().required(false).build().validate(null));
	}

	private record Case(ActionInputType type, String value, boolean valid) { }
}
