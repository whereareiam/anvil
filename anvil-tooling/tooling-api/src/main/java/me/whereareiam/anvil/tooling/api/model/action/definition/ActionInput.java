package me.whereareiam.anvil.tooling.api.model.action.definition;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import lombok.Builder;
import lombok.Value;
import lombok.Singular;
import lombok.extern.jackson.Jacksonized;
import me.whereareiam.anvil.tooling.api.type.action.ActionInputType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Declared scalar input; labels and defaults are optional.
 */
@Value
@Builder(toBuilder = true)
@Jacksonized
public class ActionInput {
	/**
	 * Stable input name used by invocation arguments.
	 */
	@NotNull
	String name;

	/**
	 * Optional label; clients fall back to the input name.
	 */
	@Nullable
	String displayName;

	/**
	 * Input type checked by the runner before invoking a handler.
	 */
	@NotNull
	ActionInputType type;

	/**
	 * Whether a value must be supplied.
	 */
	@Builder.Default
	boolean required = true;

	/**
	 * Optional default represented in the declared scalar format.
	 */
	@Nullable
	String defaultValue;

	/**
	 * Allowed values for choice inputs; empty for other types.
	 */
	@NotNull
	@Singular("choice")
	List<String> choices;

	/**
	 * Sensitive text uses a password field and is excluded from input history.
	 */
	boolean sensitive;

	/**
	 * Validates one scalar value against this input's type and required flag.
	 * Default selection is the caller's responsibility; diagnostics do not include the supplied value.
	 * @param value supplied value, or null when omitted
	 * @throws IllegalArgumentException when the value does not satisfy the input contract
	 */
	public void validate(@Nullable String value) {
		if (value == null) {
			if (required) throw new IllegalArgumentException("Missing action input: " + name);
			return;
		}
		if (required && value.isBlank()) throw new IllegalArgumentException("Action input is required: " + name);
		try {
			switch (type) {
				case TEXT -> { }
				case INTEGER -> Long.parseLong(value);
				case DECIMAL -> new BigDecimal(value);
				case BOOLEAN -> {
					if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) throw new IllegalArgumentException();
				}
				case CHOICE -> {
					if (!choices.contains(value)) throw new IllegalArgumentException();
				}
			}
		} catch (IllegalArgumentException failure) {
			throw new IllegalArgumentException("Invalid " + type.name().toLowerCase(Locale.ROOT)
					+ " value for action input '" + name + "'", failure);
		}
	}
}
