package me.whereareiam.anvil.protocol.player;

import me.whereareiam.anvil.api.exception.scenario.ScenarioValidationException;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.api.model.player.PlayerOptions;
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.type.SupportLevel;
import me.whereareiam.anvil.api.type.SupportPolicy;
import me.whereareiam.anvil.protocol.api.model.ProtocolRelease;
import me.whereareiam.anvil.protocol.player.ProtocolLibrarySelector.Selection;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtocolLibrarySelectorTest {
	private static final MinecraftVersion VERSION = MinecraftVersion.parse("1.21.11");

	private final Map<String, List<ProtocolRelease>> releases = new LinkedHashMap<>();
	private final List<String> notices = new ArrayList<>();

	@Test
	void playerChoiceWinsOverScenarioAndEngineChoices() {
		library("player", verified());
		library("scenario", verified());
		library("engine", verified());
		ProtocolLibrarySelector selector = selector("engine", SupportPolicy.LENIENT);

		assertEquals("player", selector.select(player("player"), scenario("scenario", null), VERSION).library());
		assertEquals("scenario", selector.select(player(null), scenario("scenario", null), VERSION).library());
		assertEquals("engine", selector.select(player(null), scenario(null, null), VERSION).library());
	}

	@Test
	void explicitChoiceOverridesAStrongerAutomaticCandidate() {
		library("verified", verified());
		library("compatible", compatible());

		Selection selection = selector(null, SupportPolicy.LENIENT).select(player("compatible"), scenario(null, null), VERSION);

		assertEquals("compatible", selection.library());
		assertEquals(SupportLevel.COMPATIBLE, selection.level());
	}

	@Test
	void unknownExplicitLibrariesNameTheInstalledOnes() {
		library("first", verified());
		library("second", verified());
		ProtocolLibrarySelector selector = selector(null, SupportPolicy.LENIENT);

		var failure = assertThrows(ScenarioValidationException.class,
				() -> selector.select(player("missing"), scenario(null, null), VERSION));

		assertTrue(failure.getMessage().contains("unknown protocol library 'missing'"), failure.getMessage());
		assertTrue(failure.getMessage().contains("Installed: [first, second]"), failure.getMessage());
	}

	@Test
	void validationRejectsUnknownScenarioLibraries() {
		library("installed", verified());

		var scenario = assertThrows(ScenarioValidationException.class,
				() -> selector(null, SupportPolicy.LENIENT).validate(scenario("missing", null)));

		assertTrue(scenario.getMessage().startsWith("Scenario 'versions' selects unknown protocol library 'missing'"));
		assertTrue(scenario.getMessage().endsWith("Installed: [installed]"));
		selector(null, SupportPolicy.LENIENT).validate(scenario("installed", null));
	}

	@Test
	void theStrongestSupportLevelWinsAutomaticSelection() {
		library("untested", untested());
		library("compatible", compatible());
		library("verified", verified());
		library("other-version", release("other", "1.20.6", false, false));

		Selection selection = selector(null, SupportPolicy.LENIENT).select(player(null), scenario(null, null), VERSION);

		assertEquals("verified", selection.library());
		assertEquals(SupportLevel.VERIFIED, selection.level());
		assertEquals("verified-release", selection.release().getLibraryVersion());
		assertTrue(notices.isEmpty());
	}

	@Test
	void aTieAtTheStrongestLevelNamesEveryTiedLibrary() {
		library("first", compatible());
		library("second", compatible());
		library("weaker", untested());

		var failure = assertThrows(ScenarioValidationException.class,
				() -> selector(null, SupportPolicy.LENIENT).select(player(null), scenario(null, null), VERSION));

		assertTrue(failure.getMessage().contains("[first, second]"), failure.getMessage());
		assertTrue(failure.getMessage().contains("COMPATIBLE"), failure.getMessage());
		assertTrue(failure.getMessage().contains("set protocolLibrary"), failure.getMessage());
	}

	@Test
	void unlistedVersionsAreUnsupportedAndRefused() {
		library("old", release("old", "1.20.6", true, false));
		ProtocolLibrarySelector selector = selector(null, SupportPolicy.LENIENT);

		var automatic = assertThrows(ScenarioValidationException.class,
				() -> selector.select(player(null), scenario(null, null), VERSION));
		var explicit = assertThrows(ScenarioValidationException.class,
				() -> selector.select(player("old"), scenario(null, null), VERSION));

		assertTrue(automatic.getMessage().startsWith("No installed protocol library supports Minecraft 1.21.11"));
		assertTrue(explicit.getMessage().contains("does not support Minecraft 1.21.11. Supported: [1.20.6]"));
	}

	@Test
	void strictPolicyRefusesUntestedReleasesAndScenarioPolicyOverridesTheEngine() {
		library("untested", untested());

		var engine = assertThrows(ScenarioValidationException.class,
				() -> selector(null, SupportPolicy.STRICT).select(player(null), scenario(null, null), VERSION));
		var scenario = assertThrows(ScenarioValidationException.class,
				() -> selector(null, SupportPolicy.LENIENT).select(player(null), scenario(null, SupportPolicy.STRICT), VERSION));

		for (var failure : List.of(engine, scenario))
			assertTrue(failure.getMessage().contains("STRICT support policy refuses protocol library 'untested'"), failure.getMessage());
		assertEquals("untested",
				selector(null, SupportPolicy.STRICT).select(player(null), scenario(null, SupportPolicy.LENIENT), VERSION).library());
	}

	@Test
	void lenientPolicyWarnsOnceForUntestedReleasesAndCompatibleReleasesReportInformation() {
		library("untested", untested());
		library("compatible", compatible());
		ProtocolLibrarySelector selector = selector(null, SupportPolicy.LENIENT);

		selector.select(player("untested"), scenario(null, null), VERSION);
		selector.select(player("untested"), scenario(null, null), VERSION);
		selector.select(player("compatible"), scenario(null, null), VERSION);

		assertEquals(2, notices.size(), notices.toString());
		assertTrue(notices.getFirst().startsWith("[Anvil] Warning: protocol library 'untested' release 'untested-release'"));
		assertTrue(notices.get(1).startsWith("[Anvil] Info: protocol library 'compatible' release 'compatible-release'"));
	}

	@Test
	void scenarioLibrariesAreTheScenarioChoiceElseTheEngineChoiceElseTheSoleStrongestPermittedPerServerVersion() {
		library("verified", verified());
		library("compatible", compatible());
		library("first-old", release("first-old", "1.20.6", false, false));
		library("second-old", release("second-old", "1.20.6", false, false));
		library("additional", release("additional", "1.18.2", true, true));
		library("unreached", release("unreached", "1.19.4", true, false));
		AnvilScenario scenario = scenario(null, null).toBuilder()
				.server(server("current", Distribution.remote("1.21.11", "1")))
				.server(server("tied", Distribution.remote("1.20.6", "1")))
				.server(server("untested", Distribution.remote("1.18.2", "1")))
				.server(server("snapshot", Distribution.remote("1.21-pre1", "1")))
				.server(server("undeclared", Distribution.local(Path.of("server.jar"))))
				.build();

		// Tied libraries and releases the policy refuses are selectable only by declaration.
		assertEquals(List.of("verified", "additional"), selector(null, SupportPolicy.LENIENT).scenarioLibraries(scenario));
		assertEquals(List.of("verified"), selector(null, SupportPolicy.STRICT).scenarioLibraries(scenario));
		assertEquals(List.of("verified"), selector(null, SupportPolicy.LENIENT)
				.scenarioLibraries(scenario.toBuilder().supportPolicy(SupportPolicy.STRICT).build()));
		assertEquals(List.of("compatible"), selector("compatible", SupportPolicy.LENIENT).scenarioLibraries(scenario));
		assertEquals(List.of("unreached"), selector("compatible", SupportPolicy.LENIENT)
				.scenarioLibraries(scenario.toBuilder().protocolLibrary("unreached").build()));
		assertTrue(selector(null, SupportPolicy.LENIENT).scenarioLibraries(scenario(null, null)).isEmpty());
	}

	@Test
	void unlaunchableDefaultReleasesWarnWhileTheScenarioIsPreparedAndRefuseThePlayersThatSelectThem() {
		String refusal = unpinned();
		AnvilScenario scenario = scenario(null, null).toBuilder()
				.server(server("current", Distribution.remote("1.21.11", "1")))
				.server(server("old", Distribution.remote("1.20.6", "1")))
				.build();
		ProtocolLibrarySelector selector = selector(null, SupportPolicy.LENIENT);

		selector.validate(scenario);
		var selected = assertThrows(ScenarioValidationException.class, () -> selector.select(player(null), scenario, VERSION));

		assertEquals(List.of("[Anvil] Warning: scenario 'versions' cannot create players for server 'current' unless they "
				+ "declare another protocol library: " + refusal), notices);
		assertEquals("Cannot create player 'Alice': " + refusal, selected.getMessage());
		notices.clear();
		selector.validate(scenario.toBuilder().protocolLibrary("pinned").build());
		assertTrue(notices.isEmpty(), notices.toString());
	}

	@Test
	void aScenarioWithoutPlayersPreparesAlthoughItsDefaultReleaseCannotBeLaunched() {
		unpinned();
		AnvilScenario serversOnly = scenario(null, null).toBuilder()
				.server(server("lobby", Distribution.remote("1.21.11", "1")))
				.server(server("game", Distribution.remote("1.21.11", "1")))
				.build();

		selector(null, SupportPolicy.LENIENT).validate(serversOnly);

		assertEquals(2, notices.size(), notices.toString());
		assertTrue(notices.stream().allMatch(notice -> notice.startsWith("[Anvil] Warning: ")), notices.toString());
	}

	@Test
	void playersDeclaringALaunchableLibraryAreCreatedAlthoughTheDefaultReleaseCannotBeLaunched() {
		unpinned();
		AnvilScenario scenario = scenario(null, null).toBuilder()
				.server(server("current", Distribution.remote("1.21.11", "1")))
				.build();
		ProtocolLibrarySelector selector = selector(null, SupportPolicy.LENIENT);

		selector.validate(scenario);
		Selection first = selector.select(player("pinned"), scenario, VERSION);
		Selection second = selector.select(PlayerOptions.builder().name("Bob").protocolLibrary("pinned").build(), scenario, VERSION);

		assertEquals("pinned", first.library());
		assertEquals("pinned", second.library());
		assertTrue(first.release().isLaunchable());
	}

	/**
	 * Installs a verified library whose release cannot be launched, which automatic selection prefers, and a
	 * compatible library whose release can.
	 *
	 * @return why the unpinned release cannot be launched
	 */
	private String unpinned() {
		String refusal = "MCProtocolLib release unpinned-release has no pinned checksum for example:protocol:1";
		library("unpinned", verified().toBuilder().libraryVersion("unpinned-release").launchRefusal(refusal).build());
		library("pinned", compatible().toBuilder().libraryVersion("pinned-release").build());
		return refusal;
	}

	private ProtocolLibrarySelector selector(@Nullable String engineLibrary, SupportPolicy policy) {
		return new ProtocolLibrarySelector(releases.keySet(), releases::get, engineLibrary, policy, notices::add);
	}

	private void library(String id, ProtocolRelease release) {
		releases.put(id, List.of(release));
	}

	private ProtocolRelease verified() {
		return release("verified", VERSION.toString(), true, false);
	}

	private ProtocolRelease compatible() {
		return release("compatible", VERSION.toString(), false, false);
	}

	private ProtocolRelease untested() {
		return release("untested", VERSION.toString(), true, true);
	}

	private ProtocolRelease release(String name, String version, boolean verified, boolean additional) {
		MinecraftVersion parsed = MinecraftVersion.parse(version);
		var builder = ProtocolRelease.builder()
				.libraryVersion(name + "-release")
				.minecraftVersion(parsed)
				.protocolNumber(774)
				.javaVersion(21)
				.additional(additional);
		if (verified) builder.verifiedVersion(parsed);

		return builder.build();
	}

	private PlayerOptions player(@Nullable String library) {
		return PlayerOptions.builder().name("Alice").protocolLibrary(library).build();
	}

	private MinecraftServer server(String name, Distribution distribution) {
		return MinecraftServer.builder().name(name).platform("test").distribution(distribution).build();
	}

	private AnvilScenario scenario(@Nullable String library, @Nullable SupportPolicy policy) {
		return AnvilScenario.builder().name("versions").entrypoint("server").protocolLibrary(library).supportPolicy(policy).build();
	}
}
