package me.whereareiam.anvil.api.model.scenario;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * What a scenario needs from the workbench that runs it, checked before any of its processes is prepared. A
 * workbench that cannot meet one of them refuses the scenario with every unmet requirement, and a JUnit test
 * declaring it is skipped with those reasons.
 *
 * <p>A scenario declares only what it really depends on; most declare nothing. What its declaration already
 * implies, such as the execution providers its processes run on, is checked without being repeated here.</p>
 *
 * <pre>{@code
 * AnvilScenario.builder()
 *         .name("premium-login")
 *         .requirements(ScenarioRequirements.builder()
 *                 .reach("sessionserver.mojang.com")
 *                 .memoryMegabytes(4096)
 *                 .build())
 *         ...
 *         .build();
 * }</pre>
 */
@Value
@Builder(toBuilder = true)
public class ScenarioRequirements {
	/**
	 * Hosts outside the workbench that the scenario's processes contact, such as Mojang's session server for a
	 * proxy in online mode. Each is a host name, without scheme or port.
	 */
	@NotNull
	@Singular("reach")
	List<String> reaches;

	/**
	 * Processors the scenario's processes need together; omitted means no requirement.
	 */
	@Nullable Integer processors;

	/**
	 * Memory the scenario's processes need together, in MiB; omitted means no requirement.
	 */
	@Nullable Integer memoryMegabytes;
}
