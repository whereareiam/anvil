package me.whereareiam.anvil.workbench.remote.provisioning;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.model.java.JavaRequirement;
import me.whereareiam.anvil.api.model.java.JavaSource;
import me.whereareiam.anvil.environment.provisioning.java.api.JavaProvisioner;
import me.whereareiam.anvil.environment.provisioning.java.api.model.JavaInstallation;
import me.whereareiam.anvil.workbench.remote.WorkbenchConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Optional;

/**
 * The Java installations of a workbench on another machine.
 */
@RequiredArgsConstructor
public final class RemoteJava implements JavaProvisioner {
	private final @NotNull WorkbenchConnection connection;

	@Override
	public @NotNull Path resolve(@NotNull JavaRequirement requirement, @Nullable JavaSource source) {
		var arguments = connection.mapper().createObjectNode();
		arguments.set("requirement", connection.mapper().valueToTree(requirement));
		if (source != null) arguments.set("source", connection.mapper().valueToTree(source));

		return Path.of(connection.call("java.resolve", arguments).asText());
	}

	@Override
	public @NotNull Optional<JavaInstallation> inspect(@NotNull JavaSource source) {
		var arguments = connection.mapper().createObjectNode();
		arguments.set("source", connection.mapper().valueToTree(source));
		JsonNode installation = connection.call("java.inspectSource", arguments);

		return installation.isNull() ? Optional.empty() : Optional.of(connection.mapper().convertValue(installation, JavaInstallation.class));
	}

	@Override
	public @NotNull JavaInstallation inspect(@NotNull String properties, @NotNull Path executable) {
		var arguments = connection.mapper().createObjectNode().put("properties", properties).put("executable", executable.toString());
		return connection.mapper().convertValue(connection.call("java.inspect", arguments), JavaInstallation.class);
	}

	@Override
	public void validate(@NotNull JavaInstallation installation, @NotNull JavaRequirement requirement) {
		var arguments = connection.mapper().createObjectNode();
		arguments.set("installation", connection.mapper().valueToTree(installation));
		arguments.set("requirement", connection.mapper().valueToTree(requirement));
		connection.call("java.validate", arguments);
	}
}
