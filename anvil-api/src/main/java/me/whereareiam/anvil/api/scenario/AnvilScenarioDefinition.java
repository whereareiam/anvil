package me.whereareiam.anvil.api.scenario;

import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import org.jetbrains.annotations.NotNull;

/**
 * Defines one complete Anvil scenario for direct, type-safe selection.
 *
 * <p>A definition owns the complete environment used by a test. Its scenario may contain one
 * server or a network of proxies and servers. Catalog providers can register definitions in a
 * {@link ScenarioRegistry}, while JUnit can select a definition class directly.</p>
 *
 * <pre>{@code
 * public final class RegistrationScenario implements AnvilScenarioDefinition {
 *     @Override
 *     public AnvilScenario define() {
 *         return AnvilScenario.builder()
 *                 .name("registration")
 *                 .server(registrationServer())
 *                 .entrypoint("registration")
 *                 .build();
 *     }
 * }
 * }</pre>
 */
public interface AnvilScenarioDefinition {
	/**
	 * Creates the scenario to run.
	 *
	 * @return complete scenario definition
	 */
	@NotNull AnvilScenario define();
}
