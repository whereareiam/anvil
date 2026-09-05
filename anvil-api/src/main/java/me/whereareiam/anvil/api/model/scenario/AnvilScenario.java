package me.whereareiam.anvil.api.model.scenario;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
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
