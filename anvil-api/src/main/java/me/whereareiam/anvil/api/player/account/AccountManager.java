package me.whereareiam.anvil.api.player.account;

import me.whereareiam.anvil.api.model.player.AuthenticationAccount;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Discovers locally stored authenticated accounts and creates exclusive account pools.
 */
public interface AccountManager {
	/**
	 * Lists the accounts stored in the engine's account directory.
	 *
	 * @return available account metadata without credential material
	 */
	@NotNull Collection<AuthenticationAccount> list();

	/**
	 * Creates a pool over the supplied local account IDs.
	 *
	 * @param accountIds account IDs eligible for allocation
	 * @return account pool owned by the caller
	 */
	default @NotNull AccountPool pool(@NotNull Collection<String> accountIds) {
		throw new UnsupportedOperationException("Account pooling is unavailable");
	}

	/**
	 * Resolves a pool declared in the account directory's {@code pools.properties} file.
	 * <p>
	 * The file holds {@code schemaVersion=1} and one {@code pool.<name>} entry per pool, listing
	 * distinct account IDs separated by commas in lease order:
	 * <pre>{@code
	 * schemaVersion=1
	 * pool.testers=alice,bob
	 * }</pre>
	 *
	 * @param poolName declared pool name
	 * @return account pool owned by the caller
	 * @throws IllegalArgumentException when the pool is not declared
	 * @throws IllegalStateException when the pool file violates the format
	 */
	default @NotNull AccountPool pool(@NotNull String poolName) {
		throw new UnsupportedOperationException("Named account pools are unavailable");
	}
}
