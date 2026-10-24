package me.whereareiam.anvil.platform.api.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.process.ProcessFiles;
import me.whereareiam.anvil.platform.api.PlatformArtifactSource;
import me.whereareiam.anvil.platform.api.PlatformProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.Map;

/**
 * Immutable provisioning inputs supplied to a {@link PlatformProvider} for one server or proxy.
 *
 * <p>The preparing application supplies these values. Providers write a process's configuration through its
 * {@link #files} and obtain remote artifacts through the supplied artifact source.</p>
 */
@Value
@Builder(toBuilder = true)
public class PlatformContext {
	// Scenario and artifact acquisition.
	@NotNull AnvilScenario scenario;
	@NotNull PlatformArtifactSource artifactSource;
	@NotNull Path cacheDirectory;

	// Prepared workspace locations.
	/**
	 * The files of the process's workspace, wherever its workbench keeps them; configuration is written here.
	 */
	@NotNull ProcessFiles files;

	/**
	 * The workspace on this machine, for installations linked into it.
	 */
	@NotNull Path workDirectory;

	/**
	 * Directory shared by processes in one scenario run for cross-process generated state.
	 */
	@Nullable Path workspaceGroupDirectory;

	// Platform configuration and execution-selected network topology.
	boolean eulaAccepted;
	@NotNull String bindAddress;
	int port;
	@NotNull
	@Singular("processAddress")
	Map<String, InetSocketAddress> processAddresses;

	/**
	 * Forwarding settings negotiated from provider declarations before any process starts.
	 */
	@NotNull
	@Builder.Default
	ForwardingConfiguration forwarding = ForwardingConfiguration.builder().build();
}
