package me.whereareiam.anvil.workbench.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.whereareiam.anvil.api.model.NetworkPolicy;
import me.whereareiam.anvil.environment.execution.api.model.ExecutionContext;
import me.whereareiam.anvil.environment.execution.api.model.JavaCommand;
import me.whereareiam.anvil.workbench.protocol.model.Done;
import me.whereareiam.anvil.workbench.protocol.model.WorkbenchRequest;
import me.whereareiam.anvil.workbench.protocol.model.connection.ConnectionHeader;
import me.whereareiam.anvil.workbench.protocol.model.connection.Upload;
import me.whereareiam.anvil.workbench.protocol.model.execution.OpenSession;
import me.whereareiam.anvil.workbench.protocol.model.execution.StartProcess;
import me.whereareiam.anvil.workbench.protocol.model.file.ReadFile;
import me.whereareiam.anvil.workbench.protocol.model.message.Request;
import me.whereareiam.anvil.workbench.protocol.model.message.Response;
import me.whereareiam.anvil.workbench.protocol.model.message.ReverseConnection;
import me.whereareiam.anvil.workbench.protocol.model.message.ServerMessage;
import me.whereareiam.anvil.workbench.protocol.model.workbench.ReadFacts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ProtocolMessagesTest {
	private final ObjectMapper mapper = WireMapper.create();

	@Test
	void carriesRequestsAsTheirOwnTypes() throws Exception {
		WorkbenchRequest<?> read = new ReadFile(Path.of("/srv/anvil/work/run/lobby"), "server.properties");
		WorkbenchRequest<?> open = new OpenSession("docker", ExecutionContext.builder().refresh(true)
				.networkPolicy(NetworkPolicy.builder().bindAddress("127.0.0.1").build()).build());
		WorkbenchRequest<?> start = new StartProcess("target", JavaCommand.builder().jar(Path.of("/srv/anvil/cache/paper.jar"))
				.memoryMegabytes(1024).build());

		for (WorkbenchRequest<?> request : new WorkbenchRequest<?>[]{read, open, start, new ReadFacts()})
			assertEquals(new Request(7, request), roundTrip(new Request(7, request), Request.class));
		assertEquals("ReadFile", mapper.readTree(mapper.writeValueAsString(read)).path("request").asText());
	}

	@Test
	void carriesAnswersEventsAndHeaders() throws Exception {
		ServerMessage done = new Response(7, mapper.valueToTree(new Done()), null);
		ServerMessage failed = new Response(8, null, WireErrors.describe(new IllegalStateException("closed")));
		ServerMessage reverse = new ReverseConnection("waiting", 25600);
		ConnectionHeader upload = new Upload("token", "a".repeat(64), "plugin.jar", false, 1024);

		assertEquals(done, roundTrip(done, ServerMessage.class));
		Response carried = (Response) roundTrip(failed, ServerMessage.class);
		assertEquals(8, carried.getId());
		assertEquals(((Response) failed).getFailure(), carried.getFailure());
		assertEquals(reverse, roundTrip(reverse, ServerMessage.class));
		assertEquals(upload, roundTrip(upload, ConnectionHeader.class));
	}

	private <T> T roundTrip(Object message, Class<T> type) throws Exception {
		ByteArrayOutputStream line = new ByteArrayOutputStream();
		WireLines.write(mapper, line, message);
		return WireLines.read(mapper, new ByteArrayInputStream(line.toByteArray()), type);
	}
}
