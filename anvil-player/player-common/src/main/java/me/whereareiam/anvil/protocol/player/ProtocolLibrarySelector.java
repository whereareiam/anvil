package me.whereareiam.anvil.protocol.player;

import me.whereareiam.anvil.api.exception.scenario.ScenarioValidationException;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.api.model.player.PlayerOptions;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.type.SupportLevel;
import me.whereareiam.anvil.api.type.SupportPolicy;
import me.whereareiam.anvil.protocol.api.model.ProtocolRelease;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Selects the protocol library and exact release for each simulated player.
 *
 * <p>A player uses its own {@code protocolLibrary}, else the scenario's, else the engine's. Without a
 * declaration, every installed library whose release lists the player's Minecraft version is ranked by
 * {@link SupportLevel}; the strongest wins and a tie is refused. The selected release is then checked
 * against the scenario's support policy, falling back to the engine's, and must be
 * {@link ProtocolRelease#isLaunchable() launchable}; a release that is not only produces a warning while the
 * scenario is prepared and refuses the player that selects it.</p>
 */
final class ProtocolLibrarySelector {
	private final Collection<String> libraries;
	private final Function<String, List<ProtocolRelease>> releases;
	private final @Nullable String engineLibrary;
	private final SupportPolicy enginePolicy;
	private final Consumer<String> notices;
	private final Set<String> reported = ConcurrentHashMap.newKeySet();

	/**
	 * Creates a selector over installed libraries.
	 *
	 * @param libraries installed library identifiers in discovery order
	 * @param releases releases of one installed library
	 * @param engineLibrary library the engine declares, or null to rank installed libraries
	 * @param enginePolicy support policy the engine declares, used when a scenario declares none
	 * @param notices receives information and warning lines
	 */
	ProtocolLibrarySelector(
			@NotNull Collection<String> libraries,
			@NotNull Function<String, List<ProtocolRelease>> releases,
			@Nullable String engineLibrary,
			@NotNull SupportPolicy enginePolicy,
			@NotNull Consumer<String> notices
	) {
		this.libraries = List.copyOf(libraries);
		this.releases = releases;
		this.engineLibrary = engineLibrary;
		this.enginePolicy = enginePolicy;
		this.notices = notices;
	}

	/**
	 * Checks a scenario before any process starts. A library the scenario names must be installed. A release that
	 * cannot be launched, but that players would use by default for one of the scenario's servers, only produces a
	 * warning: the release of the scenario's or engine's library for the server's native version, else the release
	 * of the one strongest library. The scenario may never create such a player, and each player may declare
	 * another library, so creating a player that selects the release is what fails.
	 *
	 * @param scenario scenario about to be prepared
	 * @throws ScenarioValidationException when the scenario names a library that is not installed
	 */
	void validate(@NotNull AnvilScenario scenario) {
		if (scenario.getProtocolLibrary() != null)
			requireInstalled(scenario.getProtocolLibrary(), "Scenario '" + scenario.getName() + "'");

		for (MinecraftServer server : scenario.getServers()) {
			Optional<MinecraftVersion> version = nativeVersion(server);
			if (version.isEmpty()) continue;

			Optional<Selection> selection = defaultSelection(scenario, version.get());
			if (selection.isEmpty() || selection.get().release().isLaunchable()) continue;

			notices.accept("[Anvil] Warning: scenario '" + scenario.getName() + "' cannot create players for server '"
					+ server.getName() + "' unless they declare another protocol library: "
					+ selection.get().release().getLaunchRefusal());
		}
	}

	/**
	 * Returns the libraries the scenario's players select when they declare none: the scenario's library,
	 * else the engine's, else, for the native version of each of the scenario's servers, the one library
	 * ranked strongest when the effective support policy permits its release. A tie, a release the policy
	 * refuses and a server without a valid native version add nothing, because no player selects a library
	 * that way without declaring one; such a player reports the reason when it is created.
	 *
	 * <p>Ranking reads the release data of every installed library, so a library whose release data cannot
	 * be read fails the scenario here, even before any player exists.</p>
	 *
	 * @param scenario validated scenario
	 * @return distinct library identifiers, ranked libraries in the order of the servers
	 */
	@NotNull List<String> scenarioLibraries(@NotNull AnvilScenario scenario) {
		if (scenario.getProtocolLibrary() != null) return List.of(scenario.getProtocolLibrary());
		if (engineLibrary != null) return List.of(engineLibrary);

		SupportPolicy policy = policy(scenario);
		Set<String> ranked = new LinkedHashSet<>();
		for (MinecraftServer server : scenario.getServers()) {
			Optional<MinecraftVersion> version = nativeVersion(server);
			if (version.isEmpty()) continue;

			List<Selection> strongest = strongest(version.get());
			if (strongest.size() != 1 || !policy.permits(strongest.getFirst().level())) continue;

			ranked.add(strongest.getFirst().library());
		}

		return List.copyOf(ranked);
	}

	/**
	 * Selects one player's library and release.
	 *
	 * @param options player declaration, possibly naming a library
	 * @param scenario owning scenario, possibly naming a library and a support policy
	 * @param version the player's exact Minecraft version
	 * @return the selected library and release, permitted by the effective support policy
	 * @throws ScenarioValidationException when no permitted, launchable release can be selected
	 */
	@NotNull Selection select(@NotNull PlayerOptions options, @NotNull AnvilScenario scenario, @NotNull MinecraftVersion version) {
		String player = "player '" + options.getName() + "'";
		String declared = declared(options, scenario);
		Selection selection = declared == null ? rank(player, version) : declared(player, declared, version);

		apply(policy(scenario), selection, version);
		if (!selection.release().isLaunchable())
			throw new ScenarioValidationException("Cannot create " + player + ": " + selection.release().getLaunchRefusal());

		return selection;
	}

	private Optional<Selection> defaultSelection(AnvilScenario scenario, MinecraftVersion version) {
		String declared = scenario.getProtocolLibrary() == null ? engineLibrary : scenario.getProtocolLibrary();
		if (declared != null)
			return release(declared, version).map(release -> new Selection(declared, release, release.support(version)));

		List<Selection> strongest = strongest(version);
		return strongest.size() == 1 ? Optional.of(strongest.getFirst()) : Optional.empty();
	}

	private SupportPolicy policy(AnvilScenario scenario) {
		return scenario.getSupportPolicy() == null ? enginePolicy : scenario.getSupportPolicy();
	}

	private @Nullable String declared(PlayerOptions options, AnvilScenario scenario) {
		if (options.getProtocolLibrary() != null) return options.getProtocolLibrary();
		if (scenario.getProtocolLibrary() != null) return scenario.getProtocolLibrary();

		return engineLibrary;
	}

	private Selection declared(String player, String library, MinecraftVersion version) {
		requireInstalled(library, "Simulated " + player);

		ProtocolRelease release = release(library, version).orElseThrow(() -> new ScenarioValidationException(
				"Protocol library '" + library + "' selected for " + player + " does not support Minecraft " + version
						+ ". Supported: " + versions(library)));

		return new Selection(library, release, release.support(version));
	}

	private Selection rank(String player, MinecraftVersion version) {
		List<Selection> strongest = strongest(version);
		if (strongest.isEmpty())
			throw new ScenarioValidationException("No installed protocol library supports Minecraft " + version
					+ " for " + player + ". Installed: " + libraries);
		if (strongest.size() > 1)
			throw new ScenarioValidationException("Protocol libraries " + strongest.stream().map(Selection::library).toList()
					+ " support Minecraft " + version + " equally (" + strongest.getFirst().level() + ") for " + player
					+ "; set protocolLibrary on the player, scenario, or engine to choose one");

		return strongest.getFirst();
	}

	private List<Selection> strongest(MinecraftVersion version) {
		List<Selection> strongest = new ArrayList<>();
		for (String library : libraries) {
			Optional<ProtocolRelease> release = release(library, version);
			if (release.isEmpty()) continue;

			Selection candidate = new Selection(library, release.get(), release.get().support(version));
			if (!strongest.isEmpty() && candidate.level().compareTo(strongest.getFirst().level()) > 0) continue;
			if (!strongest.isEmpty() && candidate.level().compareTo(strongest.getFirst().level()) < 0) strongest.clear();
			strongest.add(candidate);
		}

		return strongest;
	}

	private static Optional<MinecraftVersion> nativeVersion(MinecraftServer server) {
		try {
			return Optional.ofNullable(server.nativeVersion());
		} catch (IllegalArgumentException invalid) {
			return Optional.empty();
		}
	}

	private void apply(SupportPolicy policy, Selection selection, MinecraftVersion version) {
		String subject = "protocol library '" + selection.library() + "' release '"
				+ selection.release().getLibraryVersion() + "' with Minecraft " + version;
		if (!policy.permits(selection.level()))
			throw new ScenarioValidationException("The " + policy + " support policy refuses " + subject
					+ " because it is " + selection.level());

		String key = selection.library() + '|' + selection.release().getLibraryVersion() + '|' + version;
		if (selection.level() == SupportLevel.COMPATIBLE && reported.add(key))
			notices.accept("[Anvil] Info: " + subject + " is supported by Anvil's release data but not live-tested.");
		if (selection.level() == SupportLevel.UNTESTED && reported.add(key))
			notices.accept("[Anvil] Warning: " + subject + " is untested; the " + policy
					+ " support policy runs it anyway.");
	}

	private Optional<ProtocolRelease> release(String library, MinecraftVersion version) {
		for (ProtocolRelease release : releases.apply(library))
			if (release.getMinecraftVersions().contains(version)) return Optional.of(release);

		return Optional.empty();
	}

	private List<String> versions(String library) {
		List<String> versions = new ArrayList<>();
		for (ProtocolRelease release : releases.apply(library))
			release.getMinecraftVersions().forEach(version -> versions.add(version.toString()));

		return versions;
	}

	private void requireInstalled(String library, String owner) {
		if (!libraries.contains(library))
			throw new ScenarioValidationException(owner + " selects unknown protocol library '" + library
					+ "'. Installed: " + libraries);
	}

	/**
	 * Library and release selected for one player.
	 *
	 * @param library protocol-library identifier
	 * @param release release listing the player's version
	 * @param level support level of the release for that version
	 */
	record Selection(@NotNull String library, @NotNull ProtocolRelease release, @NotNull SupportLevel level) { }
}
