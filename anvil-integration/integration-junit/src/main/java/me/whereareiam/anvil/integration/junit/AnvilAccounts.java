package me.whereareiam.anvil.integration.junit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that a test needs authenticated accounts stored on the machine that runs it. The test is
 * skipped, before any process starts, when the machine stores fewer accounts than required; a
 * continuous-integration machine without accounts therefore skips it instead of failing.
 *
 * <p>The requirement counts stored accounts rather than naming one, so the test runs with whichever accounts
 * a developer has signed in. It does not know the players' protocol libraries, so it counts every stored account,
 * also one a library cannot use. Declare an {@code AccountPool} parameter and create each player from it: the
 * player leases an account its library recorded as available, and fails with the reason of every skipped account
 * when the pool holds none:</p>
 *
 * <pre>{@code
 * @AnvilTest(LobbyScenario.class)
 * @AnvilAccounts(2)
 * void tradesBetweenTwoAccounts(ScenarioContext anvil, AccountPool accounts) {
 *     PlayerLogin login = PlayerLogin.leased(AuthenticationMode.ONLINE);
 *     SimulatedPlayer seller = anvil.players().create(PlayerOptions.builder().name("seller").login(login).build(), accounts);
 *     SimulatedPlayer buyer = anvil.players().create(PlayerOptions.builder().name("buyer").login(login).build(), accounts);
 * }
 * }</pre>
 *
 * <p>An annotation on the test method takes precedence over one on its class.</p>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface AnvilAccounts {
	/**
	 * Number of distinct accounts the test leases at the same time.
	 *
	 * @return required account count, at least one
	 */
	int value() default 1;

	/**
	 * Named pool, declared in the account directory's {@code pools.properties} file, that restricts the
	 * accounts the test may use. The test is skipped when the machine does not declare the pool.
	 *
	 * @return pool name, or an empty string to accept any stored account
	 */
	String pool() default "";
}
