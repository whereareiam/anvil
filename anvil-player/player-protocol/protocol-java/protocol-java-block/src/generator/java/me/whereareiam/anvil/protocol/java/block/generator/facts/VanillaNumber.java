package me.whereareiam.anvil.protocol.java.block.generator.facts;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A number as vanilla's code declares it, a Java literal such as {@code 0.98F} or {@code 0.25D}, so it keeps
 * vanilla's precision: a float that vanilla widens to a double is widened the same way.
 */
@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class VanillaNumber {
	private static final Pattern LITERAL = Pattern.compile("(\\d+(?:\\.\\d+)?)([FD])");

	/**
	 * The literal's value, as Java parses its digits.
	 */
	@NotNull String digits;
	/**
	 * Whether vanilla declares it as a {@code float} or a {@code double}.
	 */
	@NotNull Precision precision;

	/**
	 * Binds a literal such as {@code "0.98F"}.
	 *
	 * @param literal the literal
	 * @return the number
	 * @throws IllegalArgumentException when the text is no float or double literal
	 */
	@JsonCreator
	public static @NotNull VanillaNumber parse(@NotNull String literal) {
		Matcher matcher = LITERAL.matcher(literal);
		if (!matcher.matches())
			throw new IllegalArgumentException("'" + literal + "' is no Java literal with its vanilla precision, such as "
					+ "\"0.98F\" or \"0.25D\"");

		return new VanillaNumber(matcher.group(1), matcher.group(2).equals("F") ? Precision.FLOAT : Precision.DOUBLE);
	}

	/**
	 * Returns the value of a number vanilla declares as a {@code float}.
	 *
	 * @param field the field it is read for, named in the failure
	 * @return the value
	 * @throws IllegalStateException when vanilla declares it as a {@code double}
	 */
	public float asFloat(@NotNull String field) {
		if (precision != Precision.FLOAT) throw new IllegalStateException(field + " is a float in vanilla, not " + digits + "D");

		return Float.parseFloat(digits);
	}

	/**
	 * Returns the value as vanilla uses it in double arithmetic, a float widened as Java widens it.
	 *
	 * @return the value
	 */
	public double asDouble() {
		return precision == Precision.FLOAT ? Float.parseFloat(digits) : Double.parseDouble(digits);
	}

	/**
	 * The two precisions of vanilla's numbers.
	 */
	public enum Precision {
		/**
		 * A {@code float}, written with an {@code F}.
		 */
		FLOAT,
		/**
		 * A {@code double}, written with a {@code D}.
		 */
		DOUBLE
	}
}
