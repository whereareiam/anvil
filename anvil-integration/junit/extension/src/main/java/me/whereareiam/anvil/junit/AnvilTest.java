package me.whereareiam.anvil.junit;

import me.whereareiam.anvil.api.scenario.AnvilScenarioDefinition;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Starts a fresh typed Anvil scenario around a JUnit Jupiter test.
 *
 * <pre>{@code
 * @AnvilTest(RegistrationScenario.class)
 * void registration(AnvilContext anvil) {
 *     SimulatedPlayer alice = anvil.players().create("Alice");
 *     Session session = alice.capability(Session.class);
 *     session.connect();
 *     alice.capability(Messages.class).command("register secret");
 * }
 * }</pre>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(AnvilExtension.class)
public @interface AnvilTest {
	/**
	 * Scenario definition selected without classpath scanning or string lookup.
	 *
	 * @return definition type with an accessible no-argument constructor
	 */
	Class<? extends AnvilScenarioDefinition> value();
}
