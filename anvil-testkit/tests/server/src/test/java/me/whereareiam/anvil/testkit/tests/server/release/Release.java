package me.whereareiam.anvil.testkit.tests.server.release;

import me.whereareiam.anvil.integration.junit.AnvilEnvironment;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Runs a test against the direct Paper server of the release under test, which {@link TestedRelease} names. The
 * server is kept for the test JVM, so every test of the release suite in one run shares one server, while each test
 * gets a scenario, and so players, of its own.
 *
 * <pre>{@code
 * @Release
 * class MessagesReleaseTest {
 *     @Test
 *     void echoesChat(ScenarioContext anvil) {
 *         SimulatedPlayer player = new FixtureServer(anvil).join();
 *     }
 * }
 * }</pre>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@AnvilEnvironment(ReleaseScenarios.class)
public @interface Release {
}
