package me.whereareiam.anvil.api.model.scenario;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import me.whereareiam.anvil.api.model.process.lifecycle.ProcessTimeouts;
import me.whereareiam.anvil.api.model.NetworkPolicy;
import me.whereareiam.anvil.api.model.PresentationMetadata;
import me.whereareiam.anvil.api.model.java.JavaSelection;
import me.whereareiam.anvil.api.model.process.MinecraftProxy;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.scenario.ScenarioHook;
import me.whereareiam.anvil.api.type.SupportPolicy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A complete named Minecraft environment together with its lifecycle policy and hooks: the servers and proxies
 * Anvil runs, the remotes it only connects players to, and the entrypoint players join by default.
 */
@Value
@Builder(toBuilder = true)
public class AnvilScenario {
	@NotNull String name;

	/**
	 * Optional labels for tooling; the scenario name remains its lookup identity.
	 */
	@Nullable PresentationMetadata metadata;

	/**
	 * Name of the server, proxy or remote that players join unless their connection names another target.
	 */
	@NotNull String entrypoint;

	@NotNull
	@Singular
	List<MinecraftServer> servers;

	@NotNull
	@Singular
	List<MinecraftProxy> proxies;

	/**
	 * Servers and proxies that Anvil does not run, which players may join by name; a scenario may consist of
	 * remotes alone and then starts no process.
	 */
	@NotNull
	@Singular
	List<RemoteEndpoint> remotes;

	/**
	 * Execution provider used for the whole topology: local or docker.
	 */
	@Nullable String executionProviderId;

	/**
	 * Default protocol library for this scenario's players, overriding the engine default.
	 */
	@Nullable String protocolLibrary;

	/**
	 * Support policy for this scenario, overriding the engine default.
	 */
	@Nullable SupportPolicy supportPolicy;

	/**
	 * Default Java selection; requirements and installation source inherit independently.
	 */
	@NotNull
	@Builder.Default
	JavaSelection javaSelection = JavaSelection.builder().build();

	@NotNull
	@Builder.Default
	NetworkPolicy networkPolicy = NetworkPolicy.builder().build();

	@Builder.Default
	boolean manual = false;

	/**
	 * Scenario-specific process deadlines; omitted members inherit engine defaults.
	 */
	@NotNull
	@Builder.Default
	ProcessTimeouts processTimeouts = ProcessTimeouts.builder().build();

	@Nullable ScenarioHook setupHook;
}
