package me.whereareiam.anvil.capability.agent.api.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * An agent extension JAR that an agent-backed capability needs in the processes of some platforms: the platform
 * half of the capability, such as the handlers of its operations and the listeners that record what the platform
 * sees. Anvil installs the JAR into {@code plugins/anvil-agent-extensions} of every process of those platforms
 * before the process starts, so the platform agent loads it at startup.
 *
 * <pre>{@code
 * AgentExtension bukkit = AgentExtension.builder()
 *         .platform(Platforms.PAPER)
 *         .platform(Platforms.SPIGOT)
 *         .entrypoint("com.example.echo.agent.bukkit.EchoBukkitOperations")
 *         .build();
 * }</pre>
 */
@Value
@Builder
public class AgentExtension {
	/**
	 * Platform provider IDs of the processes that install the JAR, such as {@code paper}.
	 */
	@NotNull @Singular Set<String> platforms;

	/**
	 * A class the JAR contains, such as its operation provider. Anvil finds the JAR on the runtime class path by this
	 * class, as it finds the platform agents.
	 */
	@NotNull String entrypoint;
}
