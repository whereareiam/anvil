package me.whereareiam.anvil.api.model.player;

import lombok.Getter;
import me.whereareiam.anvil.api.model.MinecraftEdition;
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
 * each library's derived session privately; this model exposes only what was recorded for each Minecraft edition,
 * its {@link AccountAccess access}. An edition no library checked yet has {@link AccountAccess#unknown unknown}
 * access.</p>
 *
 * <pre>{@code
 * for (AuthenticationAccount account : accounts.list())
 *     System.out.println(account.getAccountId() + ": " + account.access(edition).getAvailability());
 * }</pre>
 */
@Getter
public final class AuthenticationAccount {
	private final @NotNull String accountId;
	/**
	 * What was recorded for each edition, in the order the account file lists the editions.
	 */
	private final @NotNull List<AccountAccess> access;

	/**
	 * Creates account metadata without credential material.
	 *
	 * @param accountId local stable account identifier
	 * @param access what was recorded for each edition, at most one entry per edition
	 * @throws IllegalArgumentException when the account identifier is blank or an edition has several entries
	 */
	public AuthenticationAccount(@NotNull String accountId, @NotNull Collection<AccountAccess> access) {
		if (accountId.isBlank()) throw new IllegalArgumentException("Account ID must not be blank");

		Map<MinecraftEdition, AccountAccess> indexed = new LinkedHashMap<>();
		for (AccountAccess entry : access)
			if (indexed.putIfAbsent(entry.getEdition(), entry) != null)
				throw new IllegalArgumentException("Account '" + accountId + "' records edition '" + entry.getEdition() + "' twice");

		this.accountId = accountId;
		this.access = List.copyOf(indexed.values());
	}

	/**
	 * Returns what was recorded about the account for an edition.
	 *
	 * @param edition edition of the players that would use the account
	 * @return the recorded access, or {@link AccountAccess#unknown unknown} access when nothing was recorded for the
	 * edition
	 */
	public @NotNull AccountAccess access(@NotNull MinecraftEdition edition) {
		return access.stream()
				.filter(entry -> entry.getEdition().equals(edition))
				.findFirst()
				.orElseGet(() -> AccountAccess.unknown(edition, null));
	}
}
