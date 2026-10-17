package me.whereareiam.anvil.capability.dialogs.nbt;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Writes NBT trees as SNBT and reads SNBT compounds back into NBT trees, independent of any protocol library.
 *
 * <p>The writer produces the compact form adventure's SNBT writer produces: keys stay unquoted unless they hold a
 * character outside {@code [A-Za-z0-9._+-]}, strings are double-quoted, integers carry no suffix while other numbers
 * carry a lower-case one ({@code 1b}, {@code 2s}, {@code 3l}, {@code 4.5f}, {@code 6.0d}), and arrays name their type
 * in upper case ({@code [B;1b,2b]}). The reader accepts what the writer produces and the usual SNBT around it:
 * either quote character, upper- and lower-case suffixes, {@code true} and {@code false} as bytes, unsuffixed
 * decimals as doubles and other unquoted words as strings.</p>
 */
final class Snbt {
	private static final Pattern INTEGER = Pattern.compile("[-+]?(?:0|[1-9][0-9]*)([bBsSlL]?)");
	private static final Pattern DECIMAL = Pattern.compile("[-+]?(?:[0-9]+[.]?|[0-9]*[.][0-9]+)(?:[eE][-+]?[0-9]+)?([fFdD]?)");

	private final String text;
	private int position;

	private Snbt(String text) {
		this.text = text;
	}

	/**
	 * Writes a tag of an NBT tree as SNBT.
	 *
	 * @param tag compound, list or value of an NBT tree
	 * @return the tag as SNBT
	 * @throws IllegalArgumentException when the tree holds a value outside the NBT tree model
	 */
	static @NotNull String write(@NotNull Object tag) {
		StringBuilder out = new StringBuilder();
		write(tag, out);
		return out.toString();
	}

	/**
	 * Reads an SNBT compound.
	 *
	 * @param snbt SNBT text of one compound
	 * @return the compound, its entries in the order the text lists them
	 * @throws IllegalArgumentException when the text is not exactly one SNBT compound
	 */
	static @NotNull Map<String, Object> compound(@NotNull String snbt) {
		Snbt reader = new Snbt(snbt);
		reader.skipWhitespace();
		if (!reader.peek('{')) throw new IllegalArgumentException("Not an SNBT compound: " + snbt);

		Map<String, Object> compound = reader.readCompound();
		reader.skipWhitespace();
		if (reader.position != snbt.length()) throw reader.failure("unexpected text after the compound");
		return compound;
	}

	private static void write(Object tag, StringBuilder out) {
		switch (tag) {
			case Byte value -> out.append(value).append('b');
			case Short value -> out.append(value).append('s');
			case Integer value -> out.append(value);
			case Long value -> out.append(value).append('l');
			case Float value -> out.append(value).append('f');
			case Double value -> out.append(value).append('d');
			case String value -> quote(value, out);
			case byte[] values -> {
				out.append("[B;");
				for (int index = 0; index < values.length; index++) out.append(index == 0 ? "" : ",").append(values[index]).append('b');
				out.append(']');
			}
			case int[] values -> {
				out.append("[I;");
				for (int index = 0; index < values.length; index++) out.append(index == 0 ? "" : ",").append(values[index]);
				out.append(']');
			}
			case long[] values -> {
				out.append("[L;");
				for (int index = 0; index < values.length; index++) out.append(index == 0 ? "" : ",").append(values[index]).append('l');
				out.append(']');
			}
			case List<?> list -> {
				out.append('[');
				for (int index = 0; index < list.size(); index++) {
					if (index > 0) out.append(',');
					write(list.get(index), out);
				}
				out.append(']');
			}
			case Map<?, ?> compound -> {
				out.append('{');
				boolean first = true;
				for (Map.Entry<?, ?> entry : compound.entrySet()) {
					if (!first) out.append(',');
					first = false;
					key((String) entry.getKey(), out);
					out.append(':');
					write(entry.getValue(), out);
				}
				out.append('}');
			}
			default -> throw new IllegalArgumentException("Not an NBT value: " + tag.getClass().getName());
		}
	}

	private static void key(String key, StringBuilder out) {
		if (!key.isEmpty() && key.chars().allMatch(Snbt::unquoted)) {
			out.append(key);
			return;
		}

		quote(key, out);
	}

	private static void quote(String value, StringBuilder out) {
		out.append('"');
		for (int index = 0; index < value.length(); index++) {
			char character = value.charAt(index);
			if (character == '"' || character == '\\') out.append('\\');
			out.append(character);
		}
		out.append('"');
	}

	private static boolean unquoted(int character) {
		return character >= 'a' && character <= 'z' || character >= 'A' && character <= 'Z' || character >= '0' && character <= '9'
				|| character == '-' || character == '_' || character == '.' || character == '+';
	}

	private Object readValue() {
		skipWhitespace();
		if (peek('{')) return readCompound();
		if (peek('[')) return readList();
		if (peek('"') || peek('\'')) return readQuoted();

		return typed(readWord());
	}

	private Map<String, Object> readCompound() {
		expect('{');
		Map<String, Object> compound = new LinkedHashMap<>();
		skipWhitespace();
		if (accept('}')) return compound;

		do {
			skipWhitespace();
			String key = peek('"') || peek('\'') ? readQuoted() : readWord();
			if (key.isEmpty()) throw failure("expected a key");

			skipWhitespace();
			expect(':');
			compound.put(key, readValue());
			skipWhitespace();
		} while (accept(','));
		expect('}');
		return compound;
	}

	private Object readList() {
		expect('[');
		if (position + 1 < text.length() && text.charAt(position + 1) == ';') return readArray(text.charAt(position));

		List<Object> list = new ArrayList<>();
		skipWhitespace();
		if (accept(']')) return list;

		do {
			list.add(readValue());
		} while (acceptAfterWhitespace(','));
		skipWhitespace();
		expect(']');
		return list;
	}

	private Object readArray(char type) {
		position += 2;
		List<Number> elements = new ArrayList<>();
		skipWhitespace();
		if (!accept(']')) {
			do {
				skipWhitespace();
				if (!(typed(readWord()) instanceof Number number)) throw failure("expected a number in the array");
				elements.add(number);
			} while (acceptAfterWhitespace(','));
			skipWhitespace();
			expect(']');
		}

		return switch (Character.toUpperCase(type)) {
			case 'B' -> {
				byte[] values = new byte[elements.size()];
				for (int index = 0; index < values.length; index++) values[index] = elements.get(index).byteValue();
				yield values;
			}
			case 'I' -> elements.stream().mapToInt(Number::intValue).toArray();
			case 'L' -> elements.stream().mapToLong(Number::longValue).toArray();
			default -> throw failure("unknown array type " + type);
		};
	}

	private String readQuoted() {
		char quote = text.charAt(position++);
		StringBuilder value = new StringBuilder();
		while (position < text.length()) {
			char character = text.charAt(position++);
			if (character == quote) return value.toString();
			if (character == '\\') {
				if (position == text.length()) break;
				character = text.charAt(position++);
			}
			value.append(character);
		}
		throw failure("unterminated string");
	}

	private String readWord() {
		int start = position;
		while (position < text.length() && unquoted(text.charAt(position))) position++;
		if (start == position) throw failure("expected a value");

		return text.substring(start, position);
	}

	private static Object typed(String word) {
		if (word.equals("true")) return (byte) 1;
		if (word.equals("false")) return (byte) 0;

		Matcher integer = INTEGER.matcher(word);
		if (integer.matches()) {
			String digits = word.substring(0, word.length() - integer.group(1).length());
			return switch (integer.group(1).toLowerCase(Locale.ROOT)) {
				case "b" -> Byte.parseByte(digits);
				case "s" -> Short.parseShort(digits);
				case "l" -> Long.parseLong(digits);
				default -> Integer.parseInt(digits);
			};
		}

		Matcher decimal = DECIMAL.matcher(word);
		if (decimal.matches() && word.chars().anyMatch(Character::isDigit)) {
			String digits = word.substring(0, word.length() - decimal.group(1).length());
			return decimal.group(1).equalsIgnoreCase("f") ? (Object) Float.parseFloat(digits) : Double.parseDouble(digits);
		}
		return word;
	}

	private void skipWhitespace() {
		while (position < text.length() && Character.isWhitespace(text.charAt(position))) position++;
	}

	private boolean peek(char character) {
		return position < text.length() && text.charAt(position) == character;
	}

	private boolean accept(char character) {
		if (!peek(character)) return false;

		position++;
		return true;
	}

	private boolean acceptAfterWhitespace(char character) {
		skipWhitespace();
		return accept(character);
	}

	private void expect(char character) {
		if (!accept(character)) throw failure("expected '" + character + "'");
	}

	private IllegalArgumentException failure(String problem) {
		return new IllegalArgumentException("Not an SNBT compound: " + text + " (" + problem + " at " + position + ")");
	}
}
