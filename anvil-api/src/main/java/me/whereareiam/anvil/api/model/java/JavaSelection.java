package me.whereareiam.anvil.api.model.java;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Java requirements and an optional installation source selected at one configuration level.
 * Each omitted member inherits independently from the enclosing level. An explicitly empty
 * requirement selects the platform's preferred LTS release rather than inheriting a parent requirement.
 */
@Value
@Builder(toBuilder = true)
public class JavaSelection {
	/**
	 * Requested Java version and distribution, or null to inherit the enclosing requirement.
	 */
	@Nullable JavaRequirement requirement;

	/**
	 * Explicit installation source, or null to inherit the enclosing source.
	 */
	@Nullable JavaSource source;

	/**
	 * Fills omitted members from another selection without changing either value.
	 *
	 * <pre>{@code
	 * JavaSelection effective = process.getJavaSelection()
	 *     .withDefaults(scenario.getJavaSelection())
	 *     .withDefaults(engineOptions.getJavaSelection());
	 * }</pre>
	 *
	 * @param defaults selection supplying omitted requirements and source
	 * @return a selection preserving each explicitly configured member
	 */
	public @NotNull JavaSelection withDefaults(@NotNull JavaSelection defaults) {
		return JavaSelection.builder()
				.requirement(requirement == null ? defaults.requirement : requirement)
				.source(source == null ? defaults.source : source)
				.build();
	}
}
