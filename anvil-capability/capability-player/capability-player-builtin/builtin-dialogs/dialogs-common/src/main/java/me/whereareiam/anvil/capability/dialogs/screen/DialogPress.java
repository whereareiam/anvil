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
	 *                                  input the dialog does not have, the action's type is unknown, or a custom action
	 *                                  has no handle
	 */
	public static @Nullable DialogClick click(
			@Nullable DialogAction action,
			@NotNull List<DialogInput> inputs,
			@NotNull Map<String, String> values
	) {
		Map<String, Filled> filled = fill(inputs, values);
		if (action == null) return null;

		return switch (action.type()) {
			case RUN_COMMAND -> DialogClick.command(withoutSlash(action.value()));
			case DYNAMIC_RUN_COMMAND -> DialogClick.command(withoutSlash(substitute(action.value(), filled)));
			case CUSTOM -> DialogClick.custom(handle(action), List.of());
			case DYNAMIC_CUSTOM -> DialogClick.custom(handle(action), filled.values().stream()
					.map(Filled::value)
					.toList());
			case SUGGEST_COMMAND, OPEN_URL, COPY_TO_CLIPBOARD, CHANGE_PAGE, SHOW_DIALOG -> null;
			case OTHER -> throw new IllegalArgumentException("The button runs the action " + action.value()
					+ ", which Anvil does not know");
		};
	}

	private static long handle(DialogAction action) {
		if (action.handle() == null)
			throw new IllegalArgumentException("The custom action " + action.value()
					+ " has no handle; only actions of a dialog the server showed can be pressed");

		return action.handle();
	}

	private static Map<String, Filled> fill(List<DialogInput> inputs, Map<String, String> values) {
		Map<String, DialogInput> byKey = new LinkedHashMap<>();
		for (DialogInput input : inputs) byKey.put(input.key(), input);
		for (String key : values.keySet())
			if (!byKey.containsKey(key))
				throw new IllegalArgumentException("The dialog has no input " + key + "; inputs: " + byKey.keySet());

		Map<String, Filled> filled = new LinkedHashMap<>();
		for (DialogInput input : inputs) {
			if (input.type() == DialogInputType.OTHER) {
				if (values.containsKey(input.key()))
					throw new IllegalArgumentException("Input " + input.key() + " has a type Anvil does not know");
				continue;
			}

			String value = values.getOrDefault(input.key(), input.initial());
			filled.put(input.key(), new Filled(input, check(input, value)));
		}
		return filled;
	}

	private static DialogValue check(DialogInput input, String value) {
		switch (input.type()) {
			case TEXT -> {
				if (input.maxLength() != null && value.length() > input.maxLength())
					throw new IllegalArgumentException("Input " + input.key() + " accepts at most " + input.maxLength()
							+ " characters");
			}
			case BOOLEAN -> {
				if (!value.equals("true") && !value.equals("false"))
					throw new IllegalArgumentException("Input " + input.key() + " accepts true or false, not " + value);
			}
			case SINGLE_OPTION -> {
				if (input.options().stream().map(DialogOption::id).noneMatch(value::equals))
					throw new IllegalArgumentException("Input " + input.key() + " has no option " + value);
			}
			case NUMBER_RANGE -> checkNumber(input, value);
			case OTHER -> throw new IllegalStateException("Inputs of unknown types are not filled");
		}
		return new DialogValue(input.key(), input.type(), value);
	}

	private static void checkNumber(DialogInput input, String value) {
		float number;
		try {
			number = Float.parseFloat(value);
		} catch (NumberFormatException failure) {
			throw new IllegalArgumentException("Input " + input.key() + " accepts a number, not " + value);
		}

		float start = input.start() == null ? Float.NEGATIVE_INFINITY : input.start();
		float end = input.end() == null ? Float.POSITIVE_INFINITY : input.end();
		if (number < Math.min(start, end) || number > Math.max(start, end))
			throw new IllegalArgumentException("Input " + input.key() + " accepts numbers from " + start + " to " + end
					+ ", not " + value);
	}

	private static String substitute(String template, Map<String, Filled> filled) {
		Matcher macro = MACRO.matcher(template);
		StringBuilder command = new StringBuilder();
		while (macro.find()) {
			Filled input = filled.get(macro.group(1));
			if (input == null)
				throw new IllegalArgumentException("The template " + template + " names the input " + macro.group(1)
						+ ", which the dialog does not have");
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
			return switch (input.type()) {
				case BOOLEAN -> Boolean.parseBoolean(value.value())
						? orDefault(input.onTrue(), "true")
						: orDefault(input.onFalse(), "false");
				case NUMBER_RANGE -> number(Float.parseFloat(value.value()));
				default -> value.value();
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
