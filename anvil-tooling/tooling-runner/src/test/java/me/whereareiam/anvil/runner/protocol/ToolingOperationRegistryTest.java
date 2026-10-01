package me.whereareiam.anvil.runner.protocol;

import com.fasterxml.jackson.core.type.TypeReference;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Locale;
import me.whereareiam.anvil.tooling.api.ToolingSession;
import me.whereareiam.anvil.tooling.api.model.ToolingOperation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToolingOperationRegistryTest {
	private final ToolingRequestReader reader = new ToolingRequestReader();
	private final ToolingSession session = (ToolingSession) Proxy.newProxyInstance(
			ToolingSession.class.getClassLoader(), new Class<?>[] {ToolingSession.class},
			(proxy, method, arguments) -> { throw new AssertionError("The custom handler must own execution"); });
	private final ToolingOperation<Echo, List<String>> operation = ToolingOperation.<Echo, List<String>>builder()
			.name("fixture.echo")
			.requestType(Echo.class)
			.responseType(new TypeReference<List<String>>() {})
			.build();

	@Test
	void aRegisteredSchemaDecodesAndExecutesWithoutOperationSpecificReaderCode() throws Exception {
		var registry = new ToolingOperationRegistry(session);
		registry.register(operation, request -> List.of(request.text(), request.text().toUpperCase(Locale.ROOT)));
		var request = reader.read("{\"id\":\"request-1\",\"operation\":\"fixture.echo\",\"text\":\"hello\"}");

		var binding = registry.find(request.operation());
		assertNotNull(binding);
		assertEquals(List.of("hello", "HELLO"), binding.execute(request, reader));
	}

	@Test
	void duplicateWireNamesCannotReplaceTheOriginalBinding() throws Exception {
		var registry = new ToolingOperationRegistry(session);
		registry.register(operation, request -> List.of("original"));
		assertThrows(IllegalArgumentException.class, () -> registry.register(operation, request -> List.of("replacement")));
		var request = reader.read("{\"id\":\"request-1\",\"operation\":\"fixture.echo\",\"text\":\"hello\"}");
		assertEquals(List.of("original"), registry.find(request.operation()).execute(request, reader));
	}

	private record Echo(String text) {}
}
