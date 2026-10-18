package me.whereareiam.anvil.environment.provisioning.java.api;

import me.whereareiam.anvil.api.exception.ProvisioningException;
import me.whereareiam.anvil.api.model.java.JavaRequirement;
import me.whereareiam.anvil.api.model.java.JavaSource;
import me.whereareiam.anvil.environment.provisioning.java.api.model.JavaInstallation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Resolves and validates Java installations for execution providers.
 */
public interface JavaProvisioner extends JavaRuntimeValidator {
	/**
	 * Resolves a host executable of exactly the requirement's feature version. Without an explicit
	 * source, implementations try the current JVM when its feature version is equal, then
	 * {@code JAVA_<feature>_HOME}, then a cached installation, then a catalog download when
	 * downloads are enabled.
	 *
	 * @param requirement planned process requirement carrying an exact feature version
	 * @param source explicit source, or null to use provider defaults
	 * @return validated Java executable
	 * @throws ProvisioningException when the requirement has no
	 * feature version or no matching installation is available
	 */
	@NotNull Path resolve(@NotNull JavaRequirement requirement, @Nullable JavaSource source);

	/**
	 * Inspects the Java a local source names, a home or an executable, by running it once; planning uses its version
	 * for a process that requests none. An archive is not inspected, because only its download tells its version.
	 *
	 * @param source the declared source
	 * @return the inspected installation, or empty for an archive
	 * @throws ProvisioningException when the local source names no runnable Java
	 */
	@NotNull Optional<JavaInstallation> inspect(@NotNull JavaSource source);
}
