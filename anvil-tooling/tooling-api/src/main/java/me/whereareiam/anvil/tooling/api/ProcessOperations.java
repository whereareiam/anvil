package me.whereareiam.anvil.tooling.api;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.experimental.UtilityClass;
import me.whereareiam.anvil.tooling.api.model.process.console.ConsoleCommandResult;
import me.whereareiam.anvil.tooling.api.model.process.console.ConsoleCommandRequest;
import me.whereareiam.anvil.tooling.api.model.process.ProcessControlRequest;
import me.whereareiam.anvil.tooling.api.model.SessionSnapshot;
import me.whereareiam.anvil.tooling.api.model.ToolingOperation;
import org.jetbrains.annotations.NotNull;

/**
 * Shared process operation contracts consumed by tooling clients and runner handlers.
 */
@UtilityClass
public class ProcessOperations {
	/**
	 * Starts a selected process and its dependencies.
	 */
	public static final @NotNull ToolingOperation<ProcessControlRequest, SessionSnapshot> START =
			ToolingOperation.<ProcessControlRequest, SessionSnapshot>builder()
					.name("startProcess")
					.requestType(ProcessControlRequest.class)
					.responseType(new TypeReference<>() {})
					.build();

	/**
	 * Stops a selected process while retaining its environment.
	 */
	public static final @NotNull ToolingOperation<ProcessControlRequest, SessionSnapshot> STOP =
			ToolingOperation.<ProcessControlRequest, SessionSnapshot>builder()
					.name("stopProcess")
					.requestType(ProcessControlRequest.class)
					.responseType(new TypeReference<>() {})
					.build();

	/**
	 * Restarts a selected process with its retained workspace.
	 */
	public static final @NotNull ToolingOperation<ProcessControlRequest, SessionSnapshot> RESTART =
			ToolingOperation.<ProcessControlRequest, SessionSnapshot>builder()
					.name("restartProcess")
					.requestType(ProcessControlRequest.class)
					.responseType(new TypeReference<>() {})
					.build();

	/**
	 * Submits console text and returns the runner acknowledgement.
	 */
	public static final @NotNull ToolingOperation<ConsoleCommandRequest, ConsoleCommandResult> CONSOLE =
			ToolingOperation.<ConsoleCommandRequest, ConsoleCommandResult>builder()
					.name("console")
					.requestType(ConsoleCommandRequest.class)
					.responseType(new TypeReference<>() {})
					.build();
}
