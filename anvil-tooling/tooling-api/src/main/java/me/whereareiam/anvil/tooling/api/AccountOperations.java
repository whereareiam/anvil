package me.whereareiam.anvil.tooling.api;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.experimental.UtilityClass;
import me.whereareiam.anvil.tooling.api.model.AuthenticationLibrary;
import me.whereareiam.anvil.tooling.api.model.ToolingOperation;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Shared account operation contracts consumed by tooling clients and runner handlers.
 */
@UtilityClass
public class AccountOperations {
	/**
	 * Lists the installed protocol libraries that offer interactive sign-in, ordered by identifier,
	 * without creating an engine or signing anything in.
	 */
	public static final @NotNull ToolingOperation<Void, List<AuthenticationLibrary>> AUTHENTICATION_LIBRARIES =
			ToolingOperation.<Void, List<AuthenticationLibrary>>builder()
					.name("authenticationLibraries")
					.requestType(Void.class)
					.responseType(new TypeReference<>() {})
					.build();
}
