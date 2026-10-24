package me.whereareiam.anvil.integration.junit;

import me.whereareiam.anvil.api.exception.scenario.ScenarioRequirementsException;
import me.whereareiam.anvil.engine.config.EngineProperties;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.opentest4j.TestAbortedException;

import java.util.Locale;

/**
 * What a test run does with a test whose scenario the workbench refused for unmet requirements, chosen through
 * {@link EngineProperties#REQUIREMENTS_PROPERTY}.
 */
enum RequirementMode {
	/**
	 * Skips the test with the unmet requirements, as a test lacking accounts is skipped.
	 */
	SKIP,
	/**
	 * Fails the test, for runs that must not pass with tests left out.
	 */
	STRICT;

	/**
	 * Reads the mode of this test run; unset means {@link #SKIP}.
	 *
	 * @throws ExtensionConfigurationException when the property names no mode
	 */
	static @NotNull RequirementMode fromSystemProperties() {
		String value = System.getProperty(EngineProperties.REQUIREMENTS_PROPERTY);
		if (value == null) return SKIP;

		try {
			return valueOf(value.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException unknown) {
			throw new ExtensionConfigurationException(EngineProperties.REQUIREMENTS_PROPERTY + " must be 'skip' or 'strict', not '"
					+ value + "'", unknown);
		}
	}

	/**
	 * Returns what the test ends with when its scenario was refused.
	 *
	 * @param refused the workbench's refusal
	 * @return a skip, or the refusal itself in strict mode
	 */
	@NotNull RuntimeException refuse(@NotNull ScenarioRequirementsException refused) {
		if (this == STRICT) return refused;

		return new TestAbortedException(refused.getMessage(), refused);
	}
}
