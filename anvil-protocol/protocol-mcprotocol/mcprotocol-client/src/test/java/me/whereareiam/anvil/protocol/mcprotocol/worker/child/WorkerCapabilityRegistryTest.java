package me.whereareiam.anvil.protocol.mcprotocol.worker.child;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import me.whereareiam.anvil.protocol.adapter.api.capability.ProtocolCapabilityAdapter;
import me.whereareiam.anvil.protocol.adapter.api.capability.ProtocolCapabilityAdapterRegistry;
import me.whereareiam.anvil.protocol.adapter.api.capability.ProtocolCapabilityAdapterProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorkerCapabilityRegistryTest {
	@Test
	void neverLoadsAnUnsupportedNativeAdapterAndKeepsDirectExtensions() {
		ProtocolCapabilityAdapterProvider unavailable = new ProtocolCapabilityAdapterProvider() {
			@Override
			public boolean supports(int protocolNumber) {
				return false;
			}

			@Override
			public ProtocolCapabilityAdapter create() {
				throw new AssertionError("Unsupported native classes must not be initialized");
			}
		};
		var registry = WorkerCapabilityRegistry.compose(774, List.of(unavailable), List.of(adapter("example.action")));
		assertTrue(registry.capabilities().contains("example.feature"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"create", "destroy", "shutdown", "unqualified", ""})
	void rejectsReservedAndUnqualifiedOperations(String operation) {
		assertThrows(IllegalArgumentException.class, () -> new WorkerCapabilityRegistry(774, List.of(adapter(operation))));
	}

	@Test
	void acceptsExternalNamespacesButRejectsDuplicatesAndLateRegistration() {
		var registry = new WorkerCapabilityRegistry(774, List.of(adapter("example.action")));
		assertTrue(registry.capabilities().contains("example.feature"));
		assertThrows(IllegalStateException.class,
				() -> registry.operation("example.late", (player, arguments) -> arguments));
		assertThrows(IllegalStateException.class,
				() -> new WorkerCapabilityRegistry(774, List.of(adapter("example.action"), adapter("example.action"))));
	}

	private ProtocolCapabilityAdapter adapter(String operation) {
		return new ProtocolCapabilityAdapter() {
			@Override
			public String id() {
				return "example.feature";
			}

			@Override
			public void install(ProtocolCapabilityAdapterRegistry registry) {
				registry.operation(operation, (player, arguments) -> JsonNodeFactory.instance.objectNode());
			}
		};
	}
}
