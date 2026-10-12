package me.whereareiam.anvil.capability.dialogs.screen;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.capability.dialogs.model.click.DialogClick;
import me.whereareiam.anvil.capability.dialogs.model.click.DialogValue;
import me.whereareiam.anvil.capability.dialogs.model.dialog.DialogAction;
import me.whereareiam.anvil.capability.dialogs.model.dialog.DialogInput;
import me.whereareiam.anvil.capability.dialogs.model.dialog.DialogOption;
import me.whereareiam.anvil.capability.dialogs.type.DialogInputType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a pressed dialog button into what the client sends for it, as the Minecraft client does: it fills the
 * dialog's inputs, checks each value against its input and builds the command or custom click of the button's
 * action.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DialogPress {
	private static final Pattern MACRO = Pattern.compile("\\$\\(([a-zA-Z0-9_]+)\\)");

	/**
	 * Builds the click a button's action sends.
	 *
	 * @param action the pressed button's action, or {@code null} for a button without one
	 * @param inputs the dialog's inputs
	 * @param values values by input key; inputs left out keep their initial value
	 * @return the click to send, or {@code null} when the action sends nothing to the server, which includes opening
	 * another dialog
	 * @throws IllegalArgumentException when a value does not fit its input, a key names no input, a template names an
	 *                                  input the dialog does not have, or the action's type is unknown
	 */
	public static @Nullable DialogClick click(
			@Nullable DialogAction action,
			@NotNull List<DialogInput> inputs,
			@NotNull Map<String, String> values
	) {
		Map<String, Filled> filled = fill(inputs, values);
		if (action == null) return null;

		return switch (action.getType()) {
			case RUN_COMMAND -> DialogClick.command(withoutSlash(action.getValue()));
			case DYNAMIC_RUN_COMMAND -> DialogClick.command(withoutSlash(substitute(action.getValue(), filled)));
			case CUSTOM -> DialogClick.custom(action.getValue(), action.getPayload(), List.of());
			case DYNAMIC_CUSTOM -> DialogClick.custom(action.getValue(), action.getPayload(), filled.values().stream()
					.map(Filled::value)
					.toList());
			case SUGGEST_COMMAND, OPEN_URL, COPY_TO_CLIPBOARD, CHANGE_PAGE, SHOW_DIALOG -> null;
			case OTHER -> throw new IllegalArgumentException("The button runs the action " + action.getValue() + ", which Anvil does not know");
		};
	}

	private static Map<String, Filled> fill(List<DialogInput> inputs, Map<String, String> values) {
		Map<String, DialogInput> byKey = new LinkedHashMap<>();
		for (DialogInput input : inputs) byKey.put(input.getKey(), input);
		for (String key : values.keySet())
			if (!byKey.containsKey(key)) throw new IllegalArgumentException("The dialog has no input " + key + "; inputs: " + byKey.keySet());

		Map<String, Filled> filled = new LinkedHashMap<>();
		for (DialogInput input : inputs) {
			if (input.getType() == DialogInputType.OTHER) {
				if (values.containsKey(input.getKey()))
					throw new IllegalArgumentException("Input " + input.getKey() + " has a type Anvil does not know");
				continue;
			}

			String value = values.getOrDefault(input.getKey(), input.getInitial());
			filled.put(input.getKey(), new Filled(input, check(input, value)));
		}
		return filled;
	}

	private static DialogValue check(DialogInput input, String value) {
		switch (input.getType()) {
			case TEXT -> {
				if (input.getMaxLength() != null && value.length() > input.getMaxLength())
					throw new IllegalArgumentException("Input " + input.getKey() + " accepts at most " + input.getMaxLength() + " characters");
			}
			case BOOLEAN -> {
				if (!value.equals("true") && !value.equals("false"))
					throw new IllegalArgumentException("Input " + input.getKey() + " accepts true or false, not " + value);
			}
			case SINGLE_OPTION -> {
				if (input.getOptions().stream().map(DialogOption::getId).noneMatch(value::equals))
					throw new IllegalArgumentException("Input " + input.getKey() + " has no option " + value);
			}
			case NUMBER_RANGE -> checkNumber(input, value);
			case OTHER -> throw new IllegalStateException("Inputs of unknown types are not filled");
		}
		return new DialogValue(input.getKey(), input.getType(), value);
	}

	private static void checkNumber(DialogInput input, String value) {
		float number;
		try {
			number = Float.parseFloat(value);
		} catch (NumberFormatException failure) {
			throw new IllegalArgumentException("Input " + input.getKey() + " accepts a number, not " + value);
		}

		float start = input.getStart() == null ? Float.NEGATIVE_INFINITY : input.getStart();
		float end = input.getEnd() == null ? Float.POSITIVE_INFINITY : input.getEnd();
		if (number < Math.min(start, end) || number > Math.max(start, end))
			throw new IllegalArgumentException("Input " + input.getKey() + " accepts numbers from " + start + " to " + end + ", not " + value);
	}

	private static String substitute(String template, Map<String, Filled> filled) {
		Matcher macro = MACRO.matcher(template);
		StringBuilder command = new StringBuilder();
		while (macro.find()) {
			Filled input = filled.get(macro.group(1));
			if (input == null)
				throw new IllegalArgumentException("The template " + template + " names the input " + macro.group(1) + ", which the dialog does not have");
			macro.appendReplacement(command, Matcher.quoteReplacement(input.substitution()));
		}
		macro.appendTail(command);
		return command.toString();
	}

	private static String withoutSlash(String command) {
		return command.startsWith("/") ? command.substring(1) : command;
	}

	private record Filled(DialogInput input, DialogValue value) {
		String substitution() {
			return switch (input.getType()) {
				case BOOLEAN -> Boolean.parseBoolean(value.getValue())
						? orDefault(input.getOnTrue(), "true")
						: orDefault(input.getOnFalse(), "false");
				case NUMBER_RANGE -> number(Float.parseFloat(value.getValue()));
				default -> value.getValue();
			};
		}

		private static String orDefault(@Nullable String text, String fallback) {
			return text == null ? fallback : text;
		}

		private static String number(float value) {
			if (value == Math.rint(value) && !Float.isInfinite(value)) return Long.toString((long) value);
			return Float.toString(value);
		}
	}
}
