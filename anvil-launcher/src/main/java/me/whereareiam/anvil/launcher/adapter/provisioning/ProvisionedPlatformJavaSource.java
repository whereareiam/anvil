package me.whereareiam.anvil.launcher.adapter.provisioning;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.model.java.JavaSource;
import me.whereareiam.anvil.environment.provisioning.java.api.JavaProvisioner;
import me.whereareiam.anvil.environment.provisioning.java.api.model.JavaInstallation;
import me.whereareiam.anvil.platform.api.PlatformJavaSource;
import org.jetbrains.annotations.NotNull;

import java.util.OptionalInt;

/**
 * Binds planning's question about a declared Java source's version to Java provisioning's inspection.
 */
@RequiredArgsConstructor
public final class ProvisionedPlatformJavaSource implements PlatformJavaSource {
	private final @NotNull JavaProvisioner java;

	@Override
	public @NotNull OptionalInt featureVersion(@NotNull JavaSource source) {
		return java.inspect(source)
				.map(JavaInstallation::getFeatureVersion)
				.map(OptionalInt::of)
				.orElse(OptionalInt.empty());
	}
}
