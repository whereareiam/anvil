package me.whereareiam.anvil.api.model.player;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;
import me.whereareiam.anvil.api.model.MinecraftEdition;
import me.whereareiam.anvil.api.type.AccountAvailability;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * What players of one Minecraft edition can do with a stored account: whether they can sign in with it, the profile
 * they sign in as, and the reason when they cannot. A protocol library of the edition checks the account and words
 * the reason; the result counts for every installed library of that edition. The reason names the account and never
 * quotes credentials.
 *
 * <pre>{@code
 * AccountAccess access = account.access(server.edition());
 * if (access.getAvailability() == AccountAvailability.UNAVAILABLE)
 *     System.out.println(access.getReason());
 * }</pre>
 */
@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AccountAccess {
	/**
	 * Edition whose players this access describes.
	 */
	@NotNull MinecraftEdition edition;
	/**
	 * Whether the edition's players can use the account.
	 */
	@NotNull AccountAvailability availability;
	/**
	 * Profile name the edition's players sign in as, when the account is available to them.
	 */
	@Nullable String username;
	/**
	 * Profile UUID the edition's players sign in as, when the account is available to them.
	 */
	@Nullable UUID uniqueId;
	/**
	 * The checking library's explanation when the account is unavailable or its availability is unknown, or null.
	 */
	@Nullable String reason;

	/**
	 * Describes an account the edition's players can sign in with.
	 *
	 * @param edition edition of the players
	 * @param username profile name the players sign in as
	 * @param uniqueId profile UUID the players sign in as
	 * @return available access
	 */
	public static @NotNull AccountAccess available(@NotNull MinecraftEdition edition, @NotNull String username, @NotNull UUID uniqueId) {
		return new AccountAccess(edition, AccountAvailability.AVAILABLE, username, uniqueId, null);
	}

	/**
	 * Describes an account the services of the edition refused.
	 *
	 * @param edition edition of the players
	 * @param reason the checking library's explanation, naming the account
	 * @return unavailable access
	 */
	public static @NotNull AccountAccess unavailable(@NotNull MinecraftEdition edition, @NotNull String reason) {
		return new AccountAccess(edition, AccountAvailability.UNAVAILABLE, null, null, reason);
	}

	/**
	 * Describes an account whose availability for the edition is not determined.
	 *
	 * @param edition edition of the players
	 * @param reason why it is unknown, such as the failure that prevented the check, or null when no library of the
	 * edition checked the account
	 * @return access of unknown availability
	 */
	public static @NotNull AccountAccess unknown(@NotNull MinecraftEdition edition, @Nullable String reason) {
		return new AccountAccess(edition, AccountAvailability.UNKNOWN, null, null, reason);
	}

	/**
	 * Returns whether the edition's players can sign in with the account.
	 *
	 * @return true when the availability is {@link AccountAvailability#AVAILABLE}
	 */
	public boolean isAvailable() {
		return availability == AccountAvailability.AVAILABLE;
	}

	/**
	 * Describes the access in one line for people, such as {@code example: available as Alice (<uuid>)}.
	 *
	 * @return the edition, its availability, and the profile or reason
	 */
	public @NotNull String describe() {
		return switch (availability) {
			case AVAILABLE -> edition + ": available as " + username + " (" + uniqueId + ")";
			case UNAVAILABLE -> edition + ": unavailable, " + reason;
			case UNKNOWN -> edition + ": unknown" + (reason == null ? "" : ", " + reason);
		};
	}
}
