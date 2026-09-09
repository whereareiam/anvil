package me.whereareiam.anvil.platform.api.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.platform.api.PlatformArtifactSource;
import me.whereareiam.anvil.platform.api.PlatformProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.net.InetSocketAddress;
import java.util.Map;

/**
 * Immutable provisioning inputs supplied to a {@link PlatformProvider} for one server or proxy.
 *
 * <p>The preparing application supplies these values. Providers may write only within the supplied
 * work directory and obtain remote artifacts through the supplied artifact source.</p>
 */
@Value
@Builder(toBuilder = true)
public class PlatformContext {
	@NotNull AnvilScenario scenario;
	@NotNull Path cacheDirectory;
	@NotNull Path workDirectory;

	/**
	 * Directory shared by processes in one scenario run for cross-process generated state.
	 */
	@Nullable Path workspaceGroupDirectory;
	@NotNull String bindAddress;
	int port;
	@NotNull
	@Singular("processAddress")
	Map<String, InetSocketAddress> processAddresses;
	boolean eulaAccepted;
	@NotNull PlatformArtifactSource artifactSource;

	/**
	 * Forwarding settings negotiated from provider declarations before any process starts.
	 */
	@NotNull
	@Builder.Default
	ForwardingConfiguration forwarding = ForwardingConfiguration.builder().build();
}
