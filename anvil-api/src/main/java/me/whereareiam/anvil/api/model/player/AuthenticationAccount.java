package me.whereareiam.anvil.api.model.player;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Public metadata for one locally stored account: one Microsoft account, from which each installed protocol
 * library derives its own session when one of its players uses the account.
 *
 * <p>Credential material is intentionally not part of this model. The account file keeps the Microsoft token and
 * each library's derived session privately; this model exposes only what each library recorded about the account,
 * its {@link AccountAccess access}. A library that recorded nothing yet has {@link AccountAccess#unknown unknown}
 * access.</p>
 *
 * <pre>{@code
 * for (AuthenticationAccount account : accounts.list())
 *     System.out.println(account.getAccountId() + ": " + account.access("mcprotocol").getAvailability());
 * }</pre>
 */
@Getter
public final class AuthenticationAccount {
	private final @NotNull String accountId;
	/**
	 * What each library recorded about the account, in the order the account file lists the libraries.
	 */
	private final @NotNull List<AccountAccess> access;

	/**
	 * Creates account metadata without credential material.
	 *
	 * @param accountId local stable account identifier
	 * @param access what each library recorded about the account, at most one entry per library
	 * @throws IllegalArgumentException when the account identifier is blank or a library has several entries
	 */
	public AuthenticationAccount(@NotNull String accountId, @NotNull Collection<AccountAccess> access) {
		if (accountId.isBlank()) throw new IllegalArgumentException("Account ID must not be blank");

		Map<String, AccountAccess> indexed = new LinkedHashMap<>();
		for (AccountAccess entry : access)
			if (indexed.putIfAbsent(entry.getLibraryId(), entry) != null)
				throw new IllegalArgumentException("Account '" + accountId + "' records library '" + entry.getLibraryId() + "' twice");

		this.accountId = accountId;
		this.access = List.copyOf(indexed.values());
	}

	/**
	 * Returns what a library recorded about the account.
	 *
	 * @param libraryId protocol library identifier
	 * @return the recorded access, or {@link AccountAccess#unknown unknown} access when the library recorded none
	 */
	public @NotNull AccountAccess access(@NotNull String libraryId) {
		return access.stream()
				.filter(entry -> entry.getLibraryId().equals(libraryId))
				.findFirst()
				.orElseGet(() -> AccountAccess.unknown(libraryId, null));
	}
}
