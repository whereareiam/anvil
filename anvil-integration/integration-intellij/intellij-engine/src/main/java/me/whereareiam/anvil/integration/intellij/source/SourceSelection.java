package me.whereareiam.anvil.integration.intellij.source;

import java.util.List;

import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import me.whereareiam.anvil.integration.intellij.model.source.SourceListing;
import org.jetbrains.annotations.NotNull;

/**
 * Resolves persisted project selections without depending on IntelliJ or a build integration.
 */
final class SourceSelection {
	/**
	 * Resolves an exact saved project identifier.
	 *
	 * @param discovery current project discovery result
	 * @param id        saved project identifier, or blank when implicit selection is requested
	 * @return selected project
	 * @throws IllegalStateException when discovery is unavailable or the selection is ambiguous or stale
	 */
	@NotNull ScenarioSource resolve(
			@NotNull SourceListing discovery,
			@NotNull String id
	) {
		List<ScenarioSource> available = discovery.getSources();
		if (available.isEmpty()) throw new IllegalStateException(discovery.getMessage());
		if (id.isBlank()) {
			if (available.size() == 1) return available.getFirst();
			throw new IllegalStateException("Choose which Anvil project to run.");
		}

		return available.stream()
				.filter(project -> project.getId().equals(id))
				.findFirst()
				.orElseThrow(() ->
						new IllegalStateException(
								"The saved Anvil project is no longer available. Reload the project and choose"
										+ " its scenario source again.")
				);
	}
}
