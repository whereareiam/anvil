package me.whereareiam.anvil.capability.protocol.api.model.nbt;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Writes NBT tags as SNBT, Minecraft's text form of NBT, and reads SNBT compounds, independent of any protocol
 * library.
 *
 * <p>The writer produces the compact form: keys stay unquoted unless they hold a character outside
 * {@code [A-Za-z0-9._+-]}, strings are double-quoted, ints carry no suffix while other numbers carry a lower-case one
 * ({@code 1b}, {@code 2s}, {@code 3l}, {@code 4.5f}, {@code 6.0d}), and arrays name their type in upper case
 * ({@code [B;1b,2b]}). The reader accepts that form and the usual SNBT around it: either quote character,
 * whitespace, upper- and lower-case suffixes, {@code true} and {@code false} as bytes, unsuffixed decimals as doubles
 * and other unquoted words as strings.</p>
 *
 * <pre>{@code
 * NbtCompound payload = Snbt.readCompound("{source:\"dialog\",page:2}");
 * String text = Snbt.write(payload); // {source:"dialog",page:2}
 * }</pre>
 */
public final class Snbt {
	private static final Pattern INTEGER = Pattern.compile("[-+]?(?:0|[1-9][0-9]*)([bBsSlL]?)");
	private static final Pattern DECIMAL = Pattern.compile("[-+]?(?:[0-9]+[.]?|[0-9]*[.][0-9]+)(?:[eE][-+]?[0-9]+)?([fFdD]?)");

	private final String text;
	private int position;

	private Snbt(String text) {
		this.text = text;
	}

	/**
	 * Writes a tag as compact SNBT.
	 *
	 * @param tag the tag
	 * @return the tag as SNBT
	 */
	public static @NotNull String write(@NotNull NbtTag tag) {
		StringBuilder out = new StringBuilder();
		write(tag, out);
		return out.toString();
	}

	/**
	 * Reads an SNBT compound.
	 *
	 * @param snbt SNBT text of exactly one compound, optionally surrounded by whitespace
	 * @return the compound, its entries in the order the text lists them
	 * @throws IllegalArgumentException when the text is not exactly one SNBT compound
	 */
	public static @NotNull NbtCompound readCompound(@NotNull String snbt) {
		Snbt reader = new Snbt(snbt);
		reader.skipWhitespace();
		if (!reader.peek('{')) throw new IllegalArgumentException("Not an SNBT compound: " + snbt);

		NbtCompound compound = reader.readCompound();
		reader.skipWhitespace();
		if (reader.position != snbt.length()) throw reader.failure("unexpected text after the compound");
		return compound;
	}

	private static void write(NbtTag tag, StringBuilder out) {
		switch (tag) {
			case NbtByte value -> out.append(value.getValue()).append('b');
			case NbtShort value -> out.append(value.getValue()).append('s');
			case NbtInt value -> out.append(value.getValue());
			case NbtLong value -> out.append(value.getValue()).append('l');
			case NbtFloat value -> out.append(value.getValue()).append('f');
			case NbtDouble value -> out.append(value.getValue()).append('d');
			case NbtString value -> quote(value.getValue(), out);
			case NbtByteArray array -> {
				byte[] values = array.getValues();
				out.append("[B;");
				for (int index = 0; index < values.length; index++) out.append(index == 0 ? "" : ",").append(values[index]).append('b');
				out.append(']');
			}
			case NbtIntArray array -> {
				int[] values = array.getValues();
				out.append("[I;");
				for (int index = 0; index < values.length; index++) out.append(index == 0 ? "" : ",").append(values[index]);
				out.append(']');
			}
			case NbtLongArray array -> {
				long[] values = array.getValues();
				out.append("[L;");
				for (int index = 0; index < values.length; index++) out.append(index == 0 ? "" : ",").append(values[index]).append('l');
				out.append(']');
			}
			case NbtList list -> {
				out.append('[');
				List<NbtTag> elements = list.getElements();
				for (int index = 0; index < elements.size(); index++) {
					if (index > 0) out.append(',');
					write(elements.get(index), out);
				}
				out.append(']');
			}
			case NbtCompound compound -> {
				out.append('{');
				boolean first = true;
				for (Map.Entry<String, NbtTag> entry : compound.getEntries().entrySet()) {
					if (!first) out.append(',');
					first = false;
					key(entry.getKey(), out);
					out.append(':');
					write(entry.getValue(), out);
				}
				out.append('}');
			}
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

	private NbtTag readValue() {
		skipWhitespace();
		if (peek('{')) return readCompound();
		if (peek('[')) return readList();
		if (peek('"') || peek('\'')) return NbtString.of(readQuoted());

		return typed(readWord());
	}

	private NbtCompound readCompound() {
		expect('{');
		NbtCompound.NbtCompoundBuilder compound = NbtCompound.builder();
		skipWhitespace();
		if (accept('}')) return compound.build();

		do {
			skipWhitespace();
			String key = peek('"') || peek('\'') ? readQuoted() : readWord();
			skipWhitespace();
			expect(':');
			compound.entry(key, readValue());
			skipWhitespace();
		} while (accept(','));
		expect('}');
		return compound.build();
	}

	private NbtTag readList() {
		expect('[');
		if (position + 1 < text.length() && text.charAt(position + 1) == ';') return readArray(text.charAt(position));

		List<NbtTag> elements = new ArrayList<>();
		skipWhitespace();
		if (accept(']')) return NbtList.EMPTY;

		do {
			elements.add(readValue());
		} while (acceptAfterWhitespace(','));
		skipWhitespace();
		expect(']');
		try {
			return NbtList.of(elements);
		} catch (IllegalArgumentException mixed) {
			throw failure(mixed.getMessage());
		}
	}

	private NbtTag readArray(char type) {
		position += 2;
		List<Number> elements = new ArrayList<>();
		skipWhitespace();
		if (!accept(']')) {
			do {
				skipWhitespace();
				if (!(typed(readWord()) instanceof NbtNumber number)) throw failure("expected a number in the array");
				elements.add(number.asNumber());
			} while (acceptAfterWhitespace(','));
			skipWhitespace();
			expect(']');
		}

		return switch (Character.toUpperCase(type)) {
			case 'B' -> {
				byte[] values = new byte[elements.size()];
				for (int index = 0; index < values.length; index++) values[index] = elements.get(index).byteValue();
				yield NbtByteArray.of(values);
			}
			case 'I' -> NbtIntArray.of(elements.stream().mapToInt(Number::intValue).toArray());
			case 'L' -> NbtLongArray.of(elements.stream().mapToLong(Number::longValue).toArray());
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

	private static NbtTag typed(String word) {
		if (word.equals("true")) return NbtByte.of(true);
		if (word.equals("false")) return NbtByte.of(false);

		Matcher integer = INTEGER.matcher(word);
		if (integer.matches()) {
			String digits = word.substring(0, word.length() - integer.group(1).length());
			return switch (integer.group(1).toLowerCase(Locale.ROOT)) {
				case "b" -> NbtByte.of(Byte.parseByte(digits));
				case "s" -> NbtShort.of(Short.parseShort(digits));
				case "l" -> NbtLong.of(Long.parseLong(digits));
				default -> NbtInt.of(Integer.parseInt(digits));
			};
		}

		Matcher decimal = DECIMAL.matcher(word);
		if (decimal.matches() && word.chars().anyMatch(Character::isDigit)) {
			String digits = word.substring(0, word.length() - decimal.group(1).length());
			return decimal.group(1).equalsIgnoreCase("f") ? NbtFloat.of(Float.parseFloat(digits)) : NbtDouble.of(Double.parseDouble(digits));
		}
		return NbtString.of(word);
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
