package me.whereareiam.anvil.api.scenario;

import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import org.jetbrains.annotations.NotNull;

/**
 * Defines one complete Anvil scenario for direct, type-safe selection.
 *
 * <p>A definition owns the complete environment used by a test. Its scenario may contain one
 * server or a network of proxies and servers. A definition is a Java service provider: a public,
 * concrete class with a public no-argument constructor, registered in
 * {@code META-INF/services/me.whereareiam.anvil.api.scenario.AnvilScenarioDefinition}. Build
 * integrations write that registration for every definition class they find in the compiled
 * project, and tooling discovers and creates the registered definitions through
 * {@link java.util.ServiceLoader} only after the project runtime has been prepared. Its class's
 * binary name is a definition's identity. JUnit can select a definition class directly.</p>
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
