package me.whereareiam.anvil.integration.intellij.model.account;

import java.nio.file.Path;
import java.util.List;

import lombok.Value;
import me.whereareiam.anvil.api.model.player.AuthenticationAccount;
import me.whereareiam.anvil.api.model.player.PlayerProfile;
import me.whereareiam.anvil.integration.intellij.type.AccountSource;
import org.jetbrains.annotations.NotNull;

/**
 * Account metadata and its source file without credential contents. The metadata holds what was recorded for each
 * Minecraft edition, {@link AuthenticationAccount#getAccess()}: availability, and the profile or the checking
 * library's reason. An account file the runtime has not rewritten since it recorded availability per protocol
 * library lists no edition; the profiles it recorded are {@link #getLibraryProfiles() library profiles} instead.
 */
@Value
public class AvailableAccount {
	@NotNull AuthenticationAccount account;
	/**
	 * Profiles an account file of an earlier schema recorded for its protocol libraries, which only the runtime
	 * attributes to editions: the one profile of an Anvil 1.0 file, or each available library's profile of a file
	 * that recorded availability per library. Empty for a file that records editions.
	 */
	@NotNull List<PlayerProfile> libraryProfiles;
	@NotNull Path file;
	@NotNull AccountSource source;
}
