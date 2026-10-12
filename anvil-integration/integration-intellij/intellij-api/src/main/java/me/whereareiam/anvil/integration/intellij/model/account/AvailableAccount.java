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
	@NotNull AuthenticationAccount account;
	@NotNull Path file;
	@NotNull AccountSource source;
}
