package me.whereareiam.anvil.platform.planning.version;

import me.whereareiam.anvil.platform.api.PlatformProvider;
import me.whereareiam.anvil.platform.api.exception.PlatformException;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Version data of one engine's platform providers, each read and validated once, when a process first needs it.
 * Scenarios are planned concurrently, so concurrent lookups are safe.
 */
public final class ProviderVersions {
	private final PlatformVersionsReader reader = new PlatformVersionsReader();
	private final Map<String, PlatformVersions> versions = new ConcurrentHashMap<>();

	/**
	 * Returns the version data of a provider.
	 *
	 * @param provider installed platform provider
	 * @return validated version data from {@link PlatformProvider#versionData()}
	 * @throws PlatformException when the provider's version data is missing, unreadable, or invalid
	 */
	public @NotNull PlatformVersions of(@NotNull PlatformProvider provider) {
		return versions.computeIfAbsent(provider.id(), id -> reader.read(id, provider.versionData()));
	}
}
