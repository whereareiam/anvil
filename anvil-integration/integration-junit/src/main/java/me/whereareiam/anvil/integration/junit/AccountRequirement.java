package me.whereareiam.anvil.integration.junit;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.model.Edition;
import me.whereareiam.anvil.api.model.player.AuthenticationAccount;
import me.whereareiam.anvil.api.player.account.AccountManager;
import me.whereareiam.anvil.api.player.account.AccountPool;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.opentest4j.TestAbortedException;

import java.lang.reflect.Method;
import java.util.List;

/**
 * The accounts a test declares through {@link AnvilAccounts}, checked against the machine's stored
 * accounts before the scenario's processes start.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
final class AccountRequirement {
	private final int count;
	private final @NotNull String poolName;
	private final @Nullable Edition edition;

	static @Nullable AccountRequirement of(@NotNull Method method, @NotNull Class<?> type) {
		AnvilAccounts declared = method.getAnnotation(AnvilAccounts.class);
		if (declared == null) declared = type.getAnnotation(AnvilAccounts.class);
		if (declared == null) return null;
		if (declared.value() < 1)
			throw new ExtensionConfigurationException("@AnvilAccounts requires at least one account, not " + declared.value());

		return new AccountRequirement(declared.value(), declared.pool(), edition(declared.edition()));
	}

	/**
	 * Returns a pool holding at least the required number of accounts, counting only accounts available for the
	 * declared edition when there is one.
	 *
	 * @throws TestAbortedException when the machine stores too few accounts
	 */
	@NotNull AccountPool satisfy(@NotNull AccountManager accounts) {
		AccountPool pool = poolName.isEmpty() ? anyStored(accounts) : named(accounts);
		long available = pool.accounts().stream().filter(this::counts).count();
		if (available >= count) return pool;

		pool.close();
		throw new TestAbortedException(describe() + ", but " + available + " available");
	}

	private static @Nullable Edition edition(String declared) {
		if (declared.isEmpty()) return null;

		try {
			return Edition.of(declared);
		} catch (IllegalArgumentException invalid) {
			throw new ExtensionConfigurationException("@AnvilAccounts names an invalid edition: " + invalid.getMessage(), invalid);
		}
	}

	private AccountPool named(AccountManager accounts) {
		try {
			return accounts.pool(poolName);
		} catch (IllegalArgumentException unavailable) {
			throw new TestAbortedException(describe() + ": " + unavailable.getMessage(), unavailable);
		}
	}

	/**
	 * Pools every stored account that counts; a lease for a player lends only an account its edition can use.
	 */
	private AccountPool anyStored(AccountManager accounts) {
		List<String> accountIds = accounts.list().stream().filter(this::counts).map(AuthenticationAccount::getAccountId).toList();
		if (accountIds.isEmpty())
			throw new TestAbortedException(describe() + ", but none is " + (edition == null ? "stored" : "available") + " on this machine");

		return accounts.pool(accountIds);
	}

	private boolean counts(AuthenticationAccount account) {
		return edition == null || account.access(edition).isAvailable();
	}

	private String describe() {
		String accounts = "Test requires " + count + (count == 1 ? " account" : " accounts");
		if (!poolName.isEmpty()) accounts += " in pool '" + poolName + "'";
		if (edition != null) return accounts + " available for edition '" + edition + "'";

		return poolName.isEmpty() ? accounts + " stored" : accounts;
	}
}
