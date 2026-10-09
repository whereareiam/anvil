package me.whereareiam.anvil.api.model.player;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Public metadata for one locally stored authenticated account.
 *
 * <p>Credential material is intentionally not part of this model. Providers keep refresh data in
 * private account files and expose only the identity needed for selection and presentation. An account belongs to
 * the protocol library that stores it.</p>
 */
@Getter
public final class AuthenticationAccount {
	private final @NotNull String accountId;
	private final @NotNull String libraryId;
	private final @Nullable String username;
	private final @Nullable UUID uniqueId;

	/**
	 * Creates account metadata without credential material.
	 *
	 * @param accountId local stable account identifier
	 * @param libraryId protocol library that stores the account file
	 * @param username  retrieved Minecraft username, when known
	 * @param uniqueId  retrieved Minecraft UUID, when known
	 */
	public AuthenticationAccount(
			@NotNull String accountId,
			@NotNull String libraryId,
			@Nullable String username,
			@Nullable UUID uniqueId
	) {
		if (accountId.isBlank()) throw new IllegalArgumentException("Account ID must not be blank");
		if (libraryId.isBlank()) throw new IllegalArgumentException("Library ID must not be blank");
		this.accountId = accountId;
		this.libraryId = libraryId;
		this.username = username;
		this.uniqueId = uniqueId;
	}
}
