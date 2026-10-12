package me.whereareiam.anvil.api.model.player;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;
import me.whereareiam.anvil.api.type.AccountAvailability;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * What one protocol library can do with a stored account: whether its players can sign in with it, the profile
 * they sign in as, and the library's reason when they cannot. The library words the reason; it names the account
 * and never quotes credentials.
 *
 * <pre>{@code
 * AccountAccess access = account.access("mcprotocol");
 * if (access.getAvailability() == AccountAvailability.UNAVAILABLE)
 *     System.out.println(access.getReason());
 * }</pre>
 */
@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AccountAccess {
	/**
	 * Identifier of the protocol library this access describes.
	 */
	@NotNull String libraryId;
	/**
	 * Whether the library's players can use the account.
	 */
	@NotNull AccountAvailability availability;
	/**
	 * Profile name the library's players sign in as, when the account is available to it.
	 */
	@Nullable String username;
	/**
	 * Profile UUID the library's players sign in as, when the account is available to it.
	 */
	@Nullable UUID uniqueId;
	/**
	 * The library's explanation when the account is unavailable or its availability is unknown, or null.
	 */
	@Nullable String reason;

	/**
	 * Describes an account the library's players can sign in with.
	 *
	 * @param libraryId protocol library identifier
	 * @param username profile name the library's players sign in as
	 * @param uniqueId profile UUID the library's players sign in as
	 * @return available access
	 * @throws IllegalArgumentException when the library identifier is blank
	 */
	public static @NotNull AccountAccess available(@NotNull String libraryId, @NotNull String username, @NotNull UUID uniqueId) {
		return new AccountAccess(requireLibrary(libraryId), AccountAvailability.AVAILABLE, username, uniqueId, null);
	}

	/**
	 * Describes an account the library's services refused.
	 *
	 * @param libraryId protocol library identifier
	 * @param reason the library's explanation, naming the account
	 * @return unavailable access
	 * @throws IllegalArgumentException when the library identifier is blank
	 */
	public static @NotNull AccountAccess unavailable(@NotNull String libraryId, @NotNull String reason) {
		return new AccountAccess(requireLibrary(libraryId), AccountAvailability.UNAVAILABLE, null, null, reason);
	}

	/**
	 * Describes an account whose availability the library has not determined.
	 *
	 * @param libraryId protocol library identifier
	 * @param reason why it is unknown, such as the failure that prevented the check, or null when the library never
	 * checked
	 * @return access of unknown availability
	 * @throws IllegalArgumentException when the library identifier is blank
	 */
	public static @NotNull AccountAccess unknown(@NotNull String libraryId, @Nullable String reason) {
		return new AccountAccess(requireLibrary(libraryId), AccountAvailability.UNKNOWN, null, null, reason);
	}

	/**
	 * Returns whether the library's players can sign in with the account.
	 *
	 * @return true when the availability is {@link AccountAvailability#AVAILABLE}
	 */
	public boolean isAvailable() {
		return availability == AccountAvailability.AVAILABLE;
	}

	/**
	 * Describes the access in one line for people, such as {@code mcprotocol: available as Alice}.
	 *
	 * @return the library, its availability, and the profile or reason
	 */
	public @NotNull String describe() {
		return switch (availability) {
			case AVAILABLE -> libraryId + ": available as " + username + " (" + uniqueId + ")";
			case UNAVAILABLE -> libraryId + ": unavailable, " + reason;
			case UNKNOWN -> libraryId + ": unknown" + (reason == null ? "" : ", " + reason);
		};
	}

	private static String requireLibrary(String libraryId) {
		if (libraryId.isBlank()) throw new IllegalArgumentException("Library ID must not be blank");

		return libraryId;
	}
}
