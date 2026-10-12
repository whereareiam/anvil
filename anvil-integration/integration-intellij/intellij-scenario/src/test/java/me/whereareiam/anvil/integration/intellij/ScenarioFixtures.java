package me.whereareiam.anvil.integration.intellij;

import java.nio.file.Path;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import me.whereareiam.anvil.tooling.api.model.process.ProcessDefinition;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import me.whereareiam.anvil.tooling.api.type.ProcessRole;

/**
 * Sample scenario sources and descriptors shared by the scenario feature's tests.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ScenarioFixtures {
	public static ScenarioSource source() {
		return ScenarioSource.builder()
				.id("fixture:main")
				.displayName("Example plugin")
				.integrationId("fixture-window")
				.directory(Path.of("/example"))
				.build();
	}

	public static ScenarioDescriptor scenario() {
		return ScenarioDescriptor.builder()
				.definition("example.AuthenticationScenarios")
				.name("registration")
				.displayName("Player registration")
				.description("New and returning players can register and sign in.")
				.category("Authentication")
				.tag("smoke")
				.entrypoint("proxy")
				.javaRequirement("Java 21")
				.startupTimeoutMillis(90000)
				.process(
						ProcessDefinition.builder()
								.name("paper")
								.displayName("Paper backend")
								.description("Stores player data.")
								.role(ProcessRole.SERVER)
								.platform("paper")
								.distribution("Pinned Paper distribution")
								.distributionVersion("1.21.11")
								.distributionBuild("117")
								.memoryMegabytes(1024)
								.onlineMode(false)
								.javaRequirement("Java 21 (scenario)")
								.build())
				.process(
						ProcessDefinition.builder()
								.name("proxy")
								.displayName("Velocity proxy")
								.description("Routes incoming players.")
								.role(ProcessRole.PROXY)
								.platform("velocity")
								.distribution("Pinned Velocity distribution")
								.distributionVersion("3.5.0")
								.memoryMegabytes(512)
								.onlineMode(false)
								.javaRequirement("Java 21 (scenario)")
								.backendName("paper")
								.defaultBackend("paper")
								.build())
				.build();
	}
}
