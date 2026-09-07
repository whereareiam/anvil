package me.whereareiam.anvil.api.model.scenario;

import lombok.Builder;
import me.whereareiam.anvil.api.model.java.JavaRequirement;
import me.whereareiam.anvil.api.model.NetworkPolicy;
import lombok.Singular;
import lombok.Value;
import me.whereareiam.anvil.api.model.java.JavaSource;
import me.whereareiam.anvil.api.model.process.MinecraftProxy;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.scenario.ScenarioHook;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;

/**
 * A complete named Minecraft environment together with its lifecycle policy and hooks.
 */
@Value
@Builder(toBuilder = true)
public class AnvilScenario {
	/**
	 * Execution provider used for the whole topology: local or docker.
	 */
	@Nullable String execution;

	@NotNull
	@Builder.Default
	NetworkPolicy networkPolicy = NetworkPolicy.builder().build();

	/**
	 * Default Java selection, overridden independently by each process.
	 */
	@Nullable JavaRequirement javaRequirement;

	/**
	 * Default explicit Java source, overridden independently by each process.
	 */
	@Nullable JavaSource javaSource;

	@NotNull String name;
	@NotNull String entrypoint;
	@NotNull
	@Builder.Default
	String bindAddress = "127.0.0.1";

	@Builder.Default
	boolean allowLanBinding = false;

	@NotNull
	@Singular
	List<MinecraftServer> servers;

	@NotNull
	@Singular
	List<MinecraftProxy> proxies;

	@Builder.Default
	boolean manual = false;

	@NotNull
	@Builder.Default
	Duration startupTimeout = Duration.ofMinutes(2);

	@Nullable ScenarioHook setupHook;
}
