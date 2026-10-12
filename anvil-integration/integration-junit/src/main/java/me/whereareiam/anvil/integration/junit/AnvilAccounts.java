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
 * a developer has signed in. Without an {@link #edition()} it counts every stored account, also one the players'
 * edition cannot use. Declare an {@code AccountPool} parameter and create each player from it: the player leases an
 * account available for the edition of the servers it reaches, and fails with the reason of every skipped account
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
 * <p>Name the edition of the scenario's servers to count only accounts recorded as available for it, so the test is
 * skipped instead of failing when the machine's accounts do not own that edition of the game. The edition is the
 * identifier the platform's version data declares, such as {@code bedrock} for Lumi:</p>
 *
 * <pre>{@code
 * @AnvilAccounts(value = 2, edition = "bedrock")
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

	/**
	 * Minecraft edition whose players use the accounts, as the platforms' version data name it. Only accounts recorded
	 * as available for the edition count, and without a {@link #pool()} the test's pool holds only them.
	 *
	 * @return edition identifier, or an empty string to count every stored account
	 */
	String edition() default "";
}
