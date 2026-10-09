package me.whereareiam.anvil.api.model.process;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import me.whereareiam.anvil.api.model.PresentationMetadata;
import me.whereareiam.anvil.api.model.java.JavaSelection;
import me.whereareiam.anvil.api.model.workspace.WorkspacePlan;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Immutable declaration of one Minecraft proxy and the servers registered behind it.
 */
@Value
@Builder(toBuilder = true)
public class MinecraftProxy implements MinecraftProcess {
	@NotNull String name;

	/**
	 * Optional labels for tooling; the process name remains its routing identity.
	 */
	@Nullable PresentationMetadata metadata;

	@NotNull String platform;
	@NotNull Distribution distribution;

	/**
	 * Process-specific Java selection; omitted members inherit from the scenario and engine.
	 */
	@NotNull
	@Builder.Default
	JavaSelection javaSelection = JavaSelection.builder().build();

	@Builder.Default
	boolean onlineMode = false;

	/**
	 * Session server that verifies online logins instead of Mojang's; see {@link MinecraftProcess#getSessionServer()}.
	 */
	@Nullable URI sessionServer;

	@Builder.Default
	int memoryMegabytes = 512;

	@NotNull
	@Builder.Default
	WorkspacePlan workspace = WorkspacePlan.builder().build();

	@NotNull
	@Singular("setting")
	Map<String, String> settings;

	@NotNull
	@Singular("jvmArgument")
	List<String> jvmArguments;

	@NotNull
	@Singular("server")
	List<String> servers;

	@NotNull String defaultServer;
}
