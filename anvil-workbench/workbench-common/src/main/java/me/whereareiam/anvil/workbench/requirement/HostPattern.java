package me.whereareiam.anvil.workbench.requirement;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Matches a host a scenario reaches against a host pattern a workbench allows. Host names compare without regard
 * to letter case.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class HostPattern {
	private static final String ANY = "*";
	private static final String SUBDOMAINS = "*.";

	/**
	 * Returns whether a pattern allows a host: {@code *} allows every host, {@code *.example.com} every subdomain
	 * of {@code example.com} but not {@code example.com} itself, and any other pattern exactly that host.
	 */
	static boolean matches(@NotNull String pattern, @NotNull String host) {
		if (pattern.equals(ANY)) return true;

		String candidate = host.toLowerCase(Locale.ROOT);
		String allowed = pattern.toLowerCase(Locale.ROOT);
		if (allowed.startsWith(SUBDOMAINS)) return candidate.endsWith(allowed.substring(1));

		return candidate.equals(allowed);
	}
}
