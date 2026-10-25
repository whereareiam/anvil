package me.whereareiam.anvil.workbench.remote.provisioning;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.model.java.JavaRequirement;
import me.whereareiam.anvil.api.model.java.JavaSource;
import me.whereareiam.anvil.environment.provisioning.java.api.JavaProvisioner;
import me.whereareiam.anvil.environment.provisioning.java.api.model.JavaInstallation;
import me.whereareiam.anvil.workbench.protocol.model.java.InspectJava;
import me.whereareiam.anvil.workbench.protocol.model.java.InspectJavaSource;
import me.whereareiam.anvil.workbench.protocol.model.java.ResolveJava;
import me.whereareiam.anvil.workbench.protocol.model.java.ValidateJava;
import me.whereareiam.anvil.workbench.remote.WorkbenchConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * The Java installations of a workbench on another machine.
 */
@RequiredArgsConstructor
public final class RemoteJava implements JavaProvisioner {
	private final @NotNull WorkbenchConnection connection;

	@Override
	public @NotNull Path resolve(@NotNull JavaRequirement requirement, @Nullable JavaSource source) {
		return connection.call(new ResolveJava(requirement, source)).getExecutable();
	}

	@Override
	public @NotNull Optional<JavaInstallation> inspect(@NotNull JavaSource source) {
		return Optional.ofNullable(connection.call(new InspectJavaSource(source)).getInstallation());
	}

	@Override
	public @NotNull JavaInstallation inspect(@NotNull String properties, @NotNull Path executable) {
		return Objects.requireNonNull(connection.call(new InspectJava(properties, executable)).getInstallation(),
				"The workbench inspected no installation at " + executable);
	}

	@Override
	public void validate(@NotNull JavaInstallation installation, @NotNull JavaRequirement requirement) {
		connection.call(new ValidateJava(installation, requirement));
	}
}
