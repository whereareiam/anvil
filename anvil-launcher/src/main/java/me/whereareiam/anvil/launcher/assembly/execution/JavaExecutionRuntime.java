package me.whereareiam.anvil.launcher.assembly.execution;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.model.java.JavaSource;
import me.whereareiam.anvil.environment.execution.api.model.process.ProcessRequest;
import me.whereareiam.anvil.environment.execution.api.runtime.LocalRuntimePreparation;
import me.whereareiam.anvil.environment.execution.api.runtime.RuntimeValidator;
import me.whereareiam.anvil.environment.provisioning.java.api.JavaProvisioner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * Translates local execution requirements and container probes to the scoped Java provisioning API.
 */
@RequiredArgsConstructor
public final class JavaExecutionRuntime implements LocalRuntimePreparation, RuntimeValidator {
	private final @NotNull JavaProvisioner java;

	@Override
	public @NotNull Path executable(@NotNull ProcessRequest request, @Nullable JavaSource source) {
		return java.resolve(request.getJavaRequirement(), request.getMinimumJavaVersion(), source);
	}

	@Override
	public void validate(@NotNull String properties, @NotNull ProcessRequest request) {
		var installation = java.inspect(properties, Path.of("java"));
		java.validate(installation, request.getJavaRequirement(), request.getMinimumJavaVersion());
	}
}
