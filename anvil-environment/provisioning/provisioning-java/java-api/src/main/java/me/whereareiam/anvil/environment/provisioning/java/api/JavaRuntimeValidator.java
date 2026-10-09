package me.whereareiam.anvil.environment.provisioning.java.api;

import me.whereareiam.anvil.api.exception.JavaVersionMismatchException;
import me.whereareiam.anvil.api.exception.ProvisioningException;
import me.whereareiam.anvil.api.model.java.JavaRequirement;
import me.whereareiam.anvil.environment.provisioning.java.api.model.JavaInstallation;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Inspects and validates Java runtimes without acquiring a runtime for the host.
 */
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
	 * Validates an inspected runtime against a process requirement: the feature version must be
	 * exactly the requirement's, and a requested release or distribution must match.
	 *
	 * @param installation inspected Java identity
	 * @param requirement planned process requirement carrying an exact feature version
	 * @throws JavaVersionMismatchException when the runtime is another Java feature version
	 * @throws ProvisioningException when a requested release or distribution does not match
	 */
	void validate(@NotNull JavaInstallation installation, @NotNull JavaRequirement requirement);
}
