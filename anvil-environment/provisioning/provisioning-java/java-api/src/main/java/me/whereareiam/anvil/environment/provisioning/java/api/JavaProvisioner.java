package me.whereareiam.anvil.environment.provisioning.java.api;

import me.whereareiam.anvil.api.model.java.JavaRequirement;
import me.whereareiam.anvil.api.model.java.JavaSource;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Resolves and validates Java installations for execution providers.
 */
public interface JavaProvisioner extends JavaRuntimeValidator {
	/**
	 * Resolves a host executable matching the selection and the platform minimum.
	 *
	 * @param selection exact process requirements
	 * @param minimumVersion platform minimum feature version
	 * @return validated Java executable
	 */
	@NotNull Path resolve(@NotNull JavaRequirement selection, int minimumVersion);

	/**
	 * Resolves a requirement using an optional user-supplied installation source.
	 *
	 * @param selection exact process requirements
	 * @param minimumVersion platform minimum feature version
	 * @param source explicit source, or null to use provider defaults
	 * @return validated Java executable
	 */
	default @NotNull Path resolve(@NotNull JavaRequirement selection, int minimumVersion, @Nullable JavaSource source) {
		return resolve(selection, minimumVersion);
	}

}
