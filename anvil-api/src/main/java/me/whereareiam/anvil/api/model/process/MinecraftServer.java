package me.whereareiam.anvil.api.model.process;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.api.model.PresentationMetadata;
import me.whereareiam.anvil.api.model.java.JavaSelection;
import me.whereareiam.anvil.api.model.workspace.WorkspacePlan;
import me.whereareiam.anvil.api.type.ProcessLifetime;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Immutable declaration of one direct or proxy-backed Minecraft server process.
 */
@Value
@Builder(toBuilder = true)
public class MinecraftServer implements MinecraftProcess {
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

	@Nullable String minecraftVersion;

	@Builder.Default
	boolean onlineMode = false;

	/**
	 * Session server that verifies online logins instead of Mojang's; see {@link MinecraftProcess#getSessionServer()}.
	 */
	@Nullable URI sessionServer;

	/**
	 * How long the process keeps running; see {@link MinecraftProcess#getLifetime()}.
	 */
	@NotNull
	@Builder.Default
	ProcessLifetime lifetime = ProcessLifetime.SCENARIO;

	@Builder.Default
	int memoryMegabytes = 1024;

	@NotNull
	@Builder.Default
	WorkspacePlan workspace = WorkspacePlan.builder().build();

	@NotNull
	@Singular("setting")
	Map<String, String> settings;

	@NotNull
	@Singular("jvmArgument")
	List<String> jvmArguments;

	/**
	 * Returns the Minecraft version this server speaks: the remote distribution version, or the
	 * declared {@link #getMinecraftVersion() minecraftVersion} for local and artifact distributions.
	 *
	 * @return the native version, or null when a local or artifact distribution declares none
	 * @throws IllegalArgumentException when the version is not a release version
	 */
	public @Nullable MinecraftVersion nativeVersion() {
		String version = distribution.isLocal() || distribution.isArtifact()
				? minecraftVersion
				: distribution.getVersion();
		if (version == null || version.isBlank()) return null;

		return MinecraftVersion.parse(version);
	}
}
