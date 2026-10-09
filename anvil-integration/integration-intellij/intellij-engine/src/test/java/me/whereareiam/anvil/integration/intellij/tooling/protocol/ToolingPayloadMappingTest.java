package me.whereareiam.anvil.integration.intellij.tooling.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

import me.whereareiam.anvil.tooling.api.model.SessionSnapshot;
import me.whereareiam.anvil.tooling.api.model.process.ProcessDefinition;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import me.whereareiam.anvil.tooling.api.type.ProcessRole;
import me.whereareiam.anvil.tooling.api.type.SessionState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ToolingPayloadMappingTest {
	private final ObjectMapper json = new ObjectMapper();

	@Test
	void preservesResolvedNamesAndOptionalPresentation() throws Exception {
		var scenarios =
				scenarios(
						json.readTree(
								"""
								[{"definition":"example.Registration","name":"registration","displayName":"Registration",
									"entrypoint":"server","processes":[],"executionProviderId":null,
									"javaRequirement":"Project default","startupTimeoutMillis":120000}]
								"""));

		assertEquals("Registration", scenarios.getFirst().getDisplayName());
		assertEquals("registration", scenarios.getFirst().getName());
		assertNull(scenarios.getFirst().getCategory());
		assertEquals(0, scenarios.getFirst().getTags().size());
	}

	@Test
	void keepsDirectDefinitionsVisible() throws Exception {
		ScenarioDescriptor hidden =
				ScenarioDescriptor.builder()
						.definition("example.Definition")
						.name("paper")
						.displayName("Paper")
						.build();
		ScenarioDescriptor visible = hidden.toBuilder().name("network").build();
		var decoded =
				scenarios(json.readTree(json.writeValueAsString(List.of(hidden, visible))));
		assertEquals(List.of(hidden, visible), decoded);
	}

	@Test
	void preservesAnIdentityWhileItsDeclarationIsNotLoadedYet() throws Exception {
		ScenarioDescriptor selection =
				ScenarioDescriptor.builder()
						.definition("example.Registration")
						.name("registration")
						.displayName("Registration")
						.build();
		var decoded =
				scenarios(json.readTree(json.writeValueAsString(List.of(selection))))
						.getFirst();

		assertEquals(selection, decoded);
		assertNull(decoded.getEntrypoint());
		assertNull(decoded.getExecutionProviderId());
		assertNull(decoded.getJavaRequirement());
		assertEquals(0, decoded.getStartupTimeoutMillis());
		assertEquals(List.of(), decoded.getProcesses());
	}

	@Test
	void roundTripsDeclaredTopologyWithoutRuntimeFields() throws Exception {
		ProcessDefinition backend =
				ProcessDefinition.builder()
						.name("lobby")
						.displayName("Lobby backend")
						.description("Player entry server")
						.role(ProcessRole.SERVER)
						.platform("paper")
						.distribution("Artifact: server-under-test")
						.minecraftVersion("1.21.11")
						.memoryMegabytes(768)
						.javaRequirement("Java 21 (scenario)")
						.build();
		ProcessDefinition proxy =
				ProcessDefinition.builder()
						.name("proxy")
						.displayName("Velocity proxy")
						.role(ProcessRole.PROXY)
						.platform("velocity")
						.distribution("3.5.1 · build 615")
						.distributionVersion("3.5.1")
						.distributionBuild("615")
						.memoryMegabytes(512)
						.onlineMode(true)
						.javaRequirement("Java 21 (scenario)")
						.backendName("lobby")
						.defaultBackend("lobby")
						.build();
		ScenarioDescriptor scenario =
				ScenarioDescriptor.builder()
						.definition("example.Registration")
						.name("registration")
						.displayName("Player registration")
						.description("Registers a player behind the proxy")
						.category("Authentication")
						.tag("smoke")
						.entrypoint("proxy")
						.process(backend)
						.process(proxy)
						.manual(true)
						.executionProviderId("local")
						.javaRequirement("Java 21")
						.startupTimeoutMillis(90000)
						.shutdownTimeoutMillis(7000)
						.build();
		var encoded = json.readTree(json.writeValueAsString(List.of(scenario)));

		assertEquals(List.of(scenario), scenarios(encoded));
		assertFalse(encoded.get(0).has("players"));
		for (var process : encoded.get(0).path("processes")) {
			assertFalse(process.has("state"));
			assertFalse(process.has("host"));
			assertFalse(process.has("port"));
			assertFalse(process.has("workDirectory"));
		}
	}

	@Test
	void rejectsUnknownDeclaredProcessRole() throws Exception {
		var payload =
				json.readTree(
						"""
						[{"definition":"example.Registration","name":"registration","displayName":"Registration",
							"processes":[{"role":"UNRECOGNIZED"}]}]
						""");
		assertThrows(IllegalArgumentException.class, () -> scenarios(payload));
	}

	@Test
	void retainsFailureAndContributedActionAvailability() throws Exception {
		var snapshot =
				snapshot(
						json.readTree(
								"""
								{"sessionId":"session-1","state":"FAILED","failure":"Paper stopped",
								"processes":[{"name":"paper","executionId":"00000000-0000-0000-0000-000000000041","displayName":"Paper Backend","state":"STOPPED", "host":"localhost","port":25565,"workDirectory":"/work/paper"}],
								"players":[{"name":"alice","displayName":"Returning Player"}],
								"actions":[{"definition":{"id":"fixture.inspect","inputs":[]},"target":{"type":"PLAYER","name":"alice"},"availability":{"enabled":false,"reason":"Disconnected"}}],
								"observations":[{"definition":{"id":"fixture.connection","displayName":"Connection"},"target":{"type":"PLAYER","name":"alice"},"value":{"text":"Disconnected","tone":"WARNING"}}]}
								"""));

		assertEquals(SessionState.FAILED, snapshot.getState());
		assertEquals("Paper stopped", snapshot.getFailure());
		assertEquals("Paper Backend", snapshot.getProcesses().getFirst().getDisplayName());
		assertEquals(
				"00000000-0000-0000-0000-000000000041",
				snapshot.getProcesses().getFirst().getExecutionId().toString());
		assertEquals("Disconnected", snapshot.getObservations().getFirst().getValue().getText());
		assertFalse(snapshot.getActions().getFirst().getAvailability().isEnabled());
	}

	@Test
	void processReplacementChangesSnapshotEvenWhenReadinessAndAddressAreUnchanged() throws Exception {
		var payload =
				json.readTree(
						"""
						{"state":"RUNNING","processes":[{"name":"paper","executionId":"00000000-0000-0000-0000-000000000001","displayName":"Paper",
						"state":"READY","host":"127.0.0.1","port":25565,"workDirectory":"/work/paper"}]}
						""");
		var first = snapshot(payload);
		((ObjectNode) payload.path("processes").get(0))
				.put("executionId", "00000000-0000-0000-0000-000000000041");
		var replacement = snapshot(payload);
		assertFalse(first.equals(replacement));
		assertEquals(
				"00000000-0000-0000-0000-000000000041",
				replacement.getProcesses().getFirst().getExecutionId().toString());
	}

	@Test
	void rejectsMissingOrInvalidExecutionIdentity() throws Exception {
		for (String value : List.of("null", "0", "-1", "1.5", "\"one\"")) {
			var payload =
					json.readTree("{\"state\":\"RUNNING\",\"processes\":[{\"executionId\":" + value + "}]}");
			assertThrows(IllegalArgumentException.class, () -> snapshot(payload));
		}
		var missing = json.readTree("{\"state\":\"RUNNING\",\"processes\":[{}]}");
		assertThrows(IllegalArgumentException.class, () -> snapshot(missing));
	}

	@Test
	void rejectsUnknownLifecycleState() throws Exception {
		var payload = json.readTree("{\"state\":\"UNKNOWN\"}");
		assertThrows(IllegalArgumentException.class, () -> snapshot(payload));
	}
	private List<ScenarioDescriptor> scenarios(JsonNode payload) {
		return json.convertValue(payload, json.getTypeFactory().constructCollectionType(List.class, ScenarioDescriptor.class));
	}

	private SessionSnapshot snapshot(JsonNode payload) {
		return json.convertValue(payload, SessionSnapshot.class);
	}

}
