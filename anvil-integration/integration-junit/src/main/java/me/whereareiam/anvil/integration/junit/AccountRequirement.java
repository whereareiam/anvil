package me.whereareiam.anvil.integration.junit;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.model.player.AuthenticationAccount;
import me.whereareiam.anvil.api.player.account.AccountManager;
import me.whereareiam.anvil.api.player.account.AccountPool;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.opentest4j.TestAbortedException;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The accounts a test declares through {@link AnvilAccounts}, checked against the machine's stored
 * accounts before the scenario's processes start.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
final class AccountRequirement {
	private final int count;
	private final @NotNull String poolName;

	static @Nullable AccountRequirement of(@NotNull Method method, @NotNull Class<?> type) {
		AnvilAccounts declared = method.getAnnotation(AnvilAccounts.class);
		if (declared == null) declared = type.getAnnotation(AnvilAccounts.class);
		if (declared == null) return null;
		if (declared.value() < 1)
			throw new ExtensionConfigurationException("@AnvilAccounts requires at least one account, not " + declared.value());

		return new AccountRequirement(declared.value(), declared.pool());
	}

	/**
	 * Returns a pool holding at least the required number of accounts.
	 *
	 * @throws TestAbortedException when the machine stores too few accounts
	 */
	@NotNull AccountPool satisfy(@NotNull AccountManager accounts) {
		AccountPool pool = poolName.isEmpty() ? anyStored(accounts) : named(accounts);
		int available = pool.accounts().size();
		if (available >= count) return pool;

		pool.close();
		throw new TestAbortedException(describe() + ", but " + available + " available");
	}

	private AccountPool named(AccountManager accounts) {
		try {
			return accounts.pool(poolName);
		} catch (IllegalArgumentException unavailable) {
			throw new TestAbortedException(describe() + ": " + unavailable.getMessage(), unavailable);
		}
	}

	/**
	 * Pools every stored account whose ID one library stores; a pool cannot lease an ID that several
	 * libraries store.
	 */
	private AccountPool anyStored(AccountManager accounts) {
		Map<String, Integer> owners = new LinkedHashMap<>();
		for (AuthenticationAccount account : accounts.list())
			owners.merge(account.getAccountId(), 1, Integer::sum);

		List<String> accountIds = new ArrayList<>();
		owners.forEach((accountId, libraries) -> {
			if (libraries == 1) accountIds.add(accountId);
		});
		if (accountIds.isEmpty())
			throw new TestAbortedException(describe() + ", but none is stored on this machine");

		return accounts.pool(accountIds);
	}

	private String describe() {
		String source = poolName.isEmpty() ? "stored" : "in pool '" + poolName + "'";
		return "Test requires " + count + (count == 1 ? " account " : " accounts ") + source;
	}
}
