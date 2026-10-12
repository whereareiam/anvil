package me.whereareiam.anvil.platform.planning.validation;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.exception.scenario.ScenarioValidationException;
import me.whereareiam.anvil.api.model.MinecraftEdition;
import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.process.MinecraftProxy;
import me.whereareiam.anvil.platform.api.PlatformProvider;
import me.whereareiam.anvil.platform.planning.version.ProviderVersions;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Refuses a proxy whose backend servers speak different Minecraft editions. A player connects to the proxy with a
 * client of one edition and the proxy may move it to any of its servers, so every server behind one proxy must speak
 * the edition of the others. The edition of a server is the one its platform's version data declares.
 */
@RequiredArgsConstructor
public final class BackendEditionValidator {
	private final @NotNull ProviderVersions versions;

	/**
	 * Validates the backend servers of every proxy of the declarations against each other.
	 *
	 * @param processes declarations validated by {@link ProcessDeclarationValidator}, by process name
	 * @param providers platform providers by platform identifier
	 * @throws ScenarioValidationException when two servers of one proxy speak different editions
	 * @throws me.whereareiam.anvil.platform.api.exception.PlatformException when a server's platform has invalid
	 * version data
	 */
	public void validate(
			@NotNull Map<String, MinecraftProcess> processes,
			@NotNull Map<String, PlatformProvider> providers
	) {
		for (MinecraftProcess process : processes.values()) {
			if (!(process instanceof MinecraftProxy proxy)) continue;

			MinecraftProcess first = null;
			MinecraftEdition expected = null;
			for (String name : proxy.getServers()) {
				MinecraftProcess server = processes.get(name);
				MinecraftEdition edition = versions.of(providers.get(server.getPlatform())).getEdition();
				if (expected == null) {
					first = server;
					expected = edition;
					continue;
				}
				if (edition.equals(expected)) continue;

				throw new ScenarioValidationException("Proxy '" + proxy.getName() + "' has servers of different editions: '"
						+ first.getName() + "' (platform '" + first.getPlatform() + "') speaks " + expected.getId() + " and '"
						+ name + "' (platform '" + server.getPlatform() + "') speaks " + edition.getId()
						+ "; a player reaches every server of a proxy with one client, so they must speak one edition");
			}
		}
	}
}
