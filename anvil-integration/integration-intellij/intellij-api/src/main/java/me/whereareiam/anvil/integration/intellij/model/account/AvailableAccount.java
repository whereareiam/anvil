package me.whereareiam.anvil.integration.intellij.model.account;

import java.nio.file.Path;

import lombok.Value;
import me.whereareiam.anvil.api.model.player.AuthenticationAccount;
import me.whereareiam.anvil.integration.intellij.type.AccountSource;
import org.jetbrains.annotations.NotNull;

/**
 * Account metadata and its source file without credential contents.
 */
@Value
public class AvailableAccount {
	/**
	 * Account identity and profile, including the protocol library that owns the account
	 * ({@link AuthenticationAccount#getLibraryId()}, the file's {@code provider} key).
	 */
	@NotNull AuthenticationAccount account;
	/**
	 * Account file in its directory.
	 */
	@NotNull Path file;
	/**
	 * Store the account was read from.
	 */
	@NotNull AccountSource source;
}
