package me.whereareiam.anvil.environment.provisioning.java.distribution;

import me.whereareiam.anvil.environment.provisioning.java.api.model.JavaInstallation;
import org.jetbrains.annotations.NotNull;

/**
 * Built-in distribution descriptor for temurin.
 */
public final class TemurinDistribution implements JavaDistributionDescriptor {
	@Override
	public @NotNull String id() {
		return "temurin";
	}

	@Override
	public @NotNull String catalogId() {
		return "temurin";
	}

	@Override
	public boolean matches(@NotNull JavaInstallation installation) {
		return installation.getVendor().contains("Adoptium");
	}

}
