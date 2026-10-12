package me.whereareiam.anvil.tooling.api.model;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.NotNull;

/**
 * Installed protocol library that can sign accounts in, as offered to a user choosing which library an account
 * belongs to. Stored accounts name their library by this identifier.
 */
@Value
@Builder
@Jacksonized
public class AuthenticationLibrary {
	/**
	 * Stable library identifier, passed back to select the library for a sign-in.
	 */
	@NotNull String id;
	/**
	 * Name of the library for people, such as {@code MCProtocolLib}; never used for selection.
	 */
	@NotNull String displayName;
}
