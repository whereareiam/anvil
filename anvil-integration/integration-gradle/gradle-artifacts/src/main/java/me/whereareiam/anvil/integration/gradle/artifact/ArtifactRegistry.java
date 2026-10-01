package me.whereareiam.anvil.integration.gradle.artifact;

import lombok.RequiredArgsConstructor;
import org.gradle.api.Named;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.FileCollection;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Owns named artifact inputs and notifies execution integrations when they are registered.
 */
@RequiredArgsConstructor
public final class ArtifactRegistry {
	private final @NotNull Project project;
	private final @NotNull Map<String, FileCollection> artifacts = new LinkedHashMap<>();
	private final @NotNull List<BiConsumer<String, FileCollection>> listeners = new ArrayList<>();

	public void register(@NotNull String name, Object notation) {
		Objects.requireNonNull(name, "name");
		if (!name.matches("[A-Za-z0-9_.-]+")) {
			throw new IllegalArgumentException("Anvil artifact names may contain only letters, digits, '.', '_' and '-'");
		}

		if (artifacts.containsKey(name)) throw new IllegalArgumentException("Duplicate Anvil artifact: " + name);

		FileCollection files = switch (notation) {
			case Project producer -> jar(project.getDependencies().project(Map.of("path", producer.getPath())));
			case Dependency dependency -> jar(dependency);
			case CharSequence value when value.toString().chars().filter(character -> character == ':').count() >= 2 ->
					jar(project.getDependencies().create(value.toString()));
			case null, default -> project.files(notation);
		};

		artifacts.put(name, files);
		listeners.forEach(listener -> listener.accept(name, files));
	}

	/**
	 * Resolves only the dependency's own runtime JAR. An artifact is one file, so the producer's runtime
	 * dependencies and non-JAR variants are excluded.
	 */
	private FileCollection jar(Dependency dependency) {
		Configuration configuration = project.getConfigurations().detachedConfiguration(dependency);
		configuration.setTransitive(false);
		configuration.attributes(attributes -> {
			attributes.attribute(Usage.USAGE_ATTRIBUTE, named(Usage.class, Usage.JAVA_RUNTIME));
			attributes.attribute(Category.CATEGORY_ATTRIBUTE, named(Category.class, Category.LIBRARY));
			attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, named(LibraryElements.class, LibraryElements.JAR));
		});

		return configuration;
	}

	private <T extends Named> T named(Class<T> type, String name) {
		return project.getObjects().named(type, name);
	}

	public void onRegistered(@NotNull BiConsumer<String, FileCollection> listener) {
		listeners.add(listener);
		artifacts.forEach(listener);
	}
}
