package me.whereareiam.anvil.integration.junit;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Turns an annotation into a parameterized environment declaration. A test annotated with it starts
 * a fresh scenario built by the factory from the annotation's members, as {@link AnvilTest} does for
 * a fixed scenario definition.
 *
 * <pre>{@code
 * @Target({ElementType.TYPE, ElementType.METHOD})
 * @Retention(RetentionPolicy.RUNTIME)
 * @AnvilEnvironment(LobbyScenarios.class)
 * public @interface Lobby {
 *     String version() default "1.21.11";
 *
 *     boolean whitelist() default false;
 * }
 *
 * public final class LobbyScenarios implements AnvilScenarioFactory<Lobby> {
 *     public AnvilScenario create(Lobby lobby) {
 *         return Scenarios.lobby(lobby.version(), lobby.whitelist());
 *     }
 * }
 *
 * @Test
 * @Lobby(whitelist = true)
 * void refusesUnlistedPlayers(ScenarioContext anvil) {
 * }
 * }</pre>
 *
 * <p>An annotation on the test method takes precedence over one on its class. A method or class
 * declares one environment: {@link AnvilTest} or a single environment annotation.</p>
 */
@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(AnvilExtension.class)
public @interface AnvilEnvironment {
	/**
	 * Factory that builds the scenario from the annotated environment annotation.
	 *
	 * @return factory type with an accessible no-argument constructor
	 */
	Class<? extends AnvilScenarioFactory<?>> value();
}
