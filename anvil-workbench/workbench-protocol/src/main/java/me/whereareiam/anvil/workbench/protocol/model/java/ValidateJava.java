package me.whereareiam.anvil.workbench.protocol.model.java;

import lombok.Value;
import me.whereareiam.anvil.api.model.java.JavaRequirement;
import me.whereareiam.anvil.environment.provisioning.java.api.model.JavaInstallation;
import me.whereareiam.anvil.workbench.protocol.model.Done;
import org.jetbrains.annotations.NotNull;

/**
 * Checks that an installation meets a requirement.
 */
@Value
public class ValidateJava implements JavaRequest<Done> {
	/**
	 * Inspected installation.
	 */
	@NotNull JavaInstallation installation;

	/**
	 * Required Java.
	 */
	@NotNull JavaRequirement requirement;

	@Override
	public @NotNull Class<Done> responseType() {
		return Done.class;
	}
}
