package me.whereareiam.anvil.provisioning.api;

import me.whereareiam.anvil.api.model.java.JavaRequirement;
import me.whereareiam.anvil.provisioning.api.model.JavaInstallation;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/** Inspects and validates Java runtimes without acquiring a runtime for the host. */
public interface JavaRuntimeValidator {
	/**
	 * Parses JVM properties emitted by one executable.
	 *
	 * @param properties output of {@code java -XshowSettings:properties -version}
	 * @param executable executable represented by the properties
	 * @return inspected Java identity
	 */
	@NotNull JavaInstallation inspect(@NotNull String properties, @NotNull Path executable);

	/**
	 * Validates an inspected runtime against a process requirement.
	 *
	 * @param installation inspected Java identity
	 * @param selection requested Java identity
	 * @param minimumVersion platform minimum feature version
	 */
	void validate(@NotNull JavaInstallation installation, @NotNull JavaRequirement selection, int minimumVersion);
}
