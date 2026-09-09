package me.whereareiam.anvil.environment.provisioning.java.api.model;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Typed identity of an inspected Java executable.
 */
@Value
@Builder
public class JavaInstallation {
	@NotNull Path executable;
	int featureVersion;
	@NotNull String version;
	@NotNull String runtimeVersion;
	@NotNull String vendor;
	@NotNull String virtualMachine;
}
