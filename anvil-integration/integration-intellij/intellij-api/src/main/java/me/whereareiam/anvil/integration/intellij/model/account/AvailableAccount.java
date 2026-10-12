package me.whereareiam.anvil.integration.intellij.model.account;

import java.nio.file.Path;

import lombok.Value;
import me.whereareiam.anvil.api.model.player.AuthenticationAccount;
import me.whereareiam.anvil.integration.intellij.type.AccountSource;
import org.jetbrains.annotations.NotNull;

/**
 * Account metadata and its source file without credential contents. The metadata holds what was recorded for each
 * Minecraft edition, {@link AuthenticationAccount#getAccess()}: availability, and the profile or the checking
 * library's reason. An account file the runtime has not rewritten since it recorded availability per protocol
 * library lists no edition.
 */
@Value
public class AvailableAccount {
	@NotNull AuthenticationAccount account;
	@NotNull Path file;
	@NotNull AccountSource source;
}
