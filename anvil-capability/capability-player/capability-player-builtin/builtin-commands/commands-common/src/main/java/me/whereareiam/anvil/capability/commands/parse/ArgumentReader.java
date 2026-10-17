package me.whereareiam.anvil.capability.commands.parse;

import me.whereareiam.anvil.capability.commands.model.CommandTreeNode;
import org.jetbrains.annotations.NotNull;

/**
 * Reads one argument of a command as the client's parsers do, after Brigadier's string reader: the Brigadier
 * types exactly and the Minecraft types as {@link CommandVerifier} describes.
 */
final class ArgumentReader {
	private final String input;
	private int cursor;

	ArgumentReader(@NotNull String input, int cursor) {
		this.input = input;
		this.cursor = cursor;
	}

	/**
	 * Reads the argument.
	 *
	 * @param node the argument node
	 * @return the position after the argument, or {@code -1} when it does not parse
	 */
	int read(@NotNull CommandTreeNode node) {
		try {
			readArgument(node);
			return cursor;
		} catch (IllegalArgumentException invalid) {
			return -1;
		}
	}

	private void readArgument(CommandTreeNode node) {
		switch (node.getParser()) {
			case "brigadier:bool" -> {
				String value = readString();
				if (!value.equals("true") && !value.equals("false")) throw new IllegalArgumentException("Not a boolean");
			}
			case "brigadier:integer", "brigadier:long" -> range(node, readNumber(true));
			case "brigadier:float", "brigadier:double" -> range(node, readNumber(false));
			case "brigadier:string" -> readStringArgument(node.getStringMode());
			case "minecraft:message" -> cursor = input.length();
			case "minecraft:block_pos", "minecraft:vec3" -> readWords(3);
			case "minecraft:column_pos", "minecraft:vec2", "minecraft:rotation" -> readWords(2);
			case "minecraft:entity", "minecraft:game_profile", "minecraft:score_holder" -> readTarget();
			default -> readWord();
		}
	}

	private void readStringArgument(String mode) {
		if ("GREEDY_PHRASE".equals(mode)) {
			cursor = input.length();
			return;
		}
		if ("QUOTABLE_PHRASE".equals(mode)) {
			readString();
			return;
		}
		readUnquoted();
	}

	private String readString() {
		if (cursor < input.length() && isQuote(input.charAt(cursor))) return readQuoted();
		return readUnquoted();
	}

	private String readUnquoted() {
		int start = cursor;
		while (cursor < input.length() && isUnquoted(input.charAt(cursor))) cursor++;
		return input.substring(start, cursor);
	}

	private String readQuoted() {
		char quote = input.charAt(cursor++);
		StringBuilder value = new StringBuilder();
		while (cursor < input.length()) {
			char character = input.charAt(cursor++);
			if (character == quote) return value.toString();
			if (character != '\\') {
				value.append(character);
				continue;
			}
			if (cursor >= input.length()) break;

			char escaped = input.charAt(cursor++);
			if (escaped != quote && escaped != '\\') throw new IllegalArgumentException("Invalid escape");
			value.append(escaped);
		}

		throw new IllegalArgumentException("Unclosed quote");
	}

	private double readNumber(boolean whole) {
		int start = cursor;
		while (cursor < input.length() && isNumber(input.charAt(cursor))) cursor++;
		String number = input.substring(start, cursor);
		if (number.isEmpty()) throw new IllegalArgumentException("Expected a number");

		try {
			return whole ? Long.parseLong(number) : Double.parseDouble(number);
		} catch (NumberFormatException invalid) {
			throw new IllegalArgumentException("Invalid number", invalid);
		}
	}

	private static void range(CommandTreeNode node, double value) {
		if (node.getMin() != null && value < node.getMin()) throw new IllegalArgumentException("Too low");
		if (node.getMax() != null && value > node.getMax()) throw new IllegalArgumentException("Too high");
	}

	private void readWords(int count) {
		for (int word = 0; word < count; word++) {
			if (word > 0) {
				if (cursor >= input.length() || input.charAt(cursor) != ' ') throw new IllegalArgumentException("Incomplete coordinates");
				cursor++;
			}
			readWord();
		}
	}

	/**
	 * Reads a selector such as {@code @a[distance=..5]}, or a name or UUID up to the next space.
	 */
	private void readTarget() {
		if (cursor < input.length() && input.charAt(cursor) == '@') {
			cursor += 2;
			if (cursor > input.length()) throw new IllegalArgumentException("Incomplete selector");
			if (cursor < input.length() && input.charAt(cursor) == '[') readWord();
			return;
		}
		readWord();
	}

	/**
	 * Reads up to the next space outside quotes and brackets.
	 */
	private void readWord() {
		int start = cursor;
		int depth = 0;
		while (cursor < input.length()) {
			char character = input.charAt(cursor);
			if (character == ' ' && depth == 0) break;
			if (isQuote(character)) {
				readQuoted();
				continue;
			}
			if (character == '[' || character == '{' || character == '(') depth++;
			if ((character == ']' || character == '}' || character == ')') && --depth < 0) throw new IllegalArgumentException("Unbalanced brackets");
			cursor++;
		}
		if (depth != 0) throw new IllegalArgumentException("Unbalanced brackets");
		if (cursor == start) throw new IllegalArgumentException("Expected an argument");
	}

	private static boolean isQuote(char character) {
		return character == '"' || character == '\'';
	}

	private static boolean isUnquoted(char character) {
		return character >= '0' && character <= '9'
				|| character >= 'A' && character <= 'Z'
				|| character >= 'a' && character <= 'z'
				|| character == '_' || character == '-' || character == '.' || character == '+';
	}

	private static boolean isNumber(char character) {
		return character >= '0' && character <= '9' || character == '.' || character == '-';
	}
}
