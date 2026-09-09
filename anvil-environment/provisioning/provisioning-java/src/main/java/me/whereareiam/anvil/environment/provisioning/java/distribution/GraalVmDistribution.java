package me.whereareiam.anvil.environment.provisioning.java.distribution;

import me.whereareiam.anvil.environment.provisioning.java.api.model.JavaInstallation;
import org.jetbrains.annotations.NotNull;

/**
 * Built-in distribution descriptor for graalvm-community.
 */
public final class GraalVmDistribution implements JavaDistributionDescriptor {
	@Override
	public @NotNull String id() {
		return "graalvm-community";
	}

	@Override
	public @NotNull String catalogId() {
		return "graalvm_community";
	}

	@Override
	public boolean matches(@NotNull JavaInstallation installation) {
		return installation.getVendor().contains("GraalVM Community")
				|| installation.getVirtualMachine().contains("GraalVM CE");
	}

}
