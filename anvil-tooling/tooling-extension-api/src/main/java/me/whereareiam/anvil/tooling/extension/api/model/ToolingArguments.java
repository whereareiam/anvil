package me.whereareiam.anvil.tooling.extension.api.model;

import java.math.BigDecimal;
import java.util.Map;
import java.util.NoSuchElementException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable validated scalar inputs supplied to a tooling handler.
 */
public final class ToolingArguments {
	private final Map<String, String> values;

	/**
	 * Copies validated inputs so handlers cannot mutate the request.
	 * @param values inputs after validation and default application
	 */
	public ToolingArguments(@NotNull Map<String, String> values) {
		this.values = Map.copyOf(values);
	}

	/**
	 * Reads a required text value.
	 * @param name input name
	 * @return supplied value
	 * @throws NoSuchElementException when an optional value was omitted
	 */
	public @NotNull String text(@NotNull String name) {
		String value = values.get(name);
		if (value == null) throw new NoSuchElementException("No value for input '" + name + "'");
		return value;
	}

	/**
	 * Reads an optional text value.
	 * @param name input name
	 * @return value, or null when omitted
	 */
	public @Nullable String optionalText(@NotNull String name) { return values.get(name); }

	/**
	 * Reads a validated integer value.
	 * @param name input name
	 * @return integer value
	 */
	public long integer(@NotNull String name) { return Long.parseLong(text(name)); }

	/**
	 * Reads a validated decimal value.
	 * @param name input name
	 * @return decimal value
	 */
	public @NotNull BigDecimal decimal(@NotNull String name) { return new BigDecimal(text(name)); }

	/**
	 * Reads a validated boolean value.
	 * @param name input name
	 * @return boolean value
	 */
	public boolean booleanValue(@NotNull String name) { return Boolean.parseBoolean(text(name)); }
}
