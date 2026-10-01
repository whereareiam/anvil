package me.whereareiam.anvil.tooling.api;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.experimental.UtilityClass;
import me.whereareiam.anvil.tooling.api.model.action.invocation.ActionRequest;
import me.whereareiam.anvil.tooling.api.model.action.invocation.ActionResult;
import me.whereareiam.anvil.tooling.api.model.SessionSnapshot;
import me.whereareiam.anvil.tooling.api.model.ToolingOperation;
import org.jetbrains.annotations.NotNull;

/**
 * Shared environment operation contracts consumed by tooling clients and runner handlers.
 */
@UtilityClass
public class EnvironmentOperations {
	/**
	 * Reads the current environment state.
	 */
	public static final @NotNull ToolingOperation<Void, SessionSnapshot> SNAPSHOT =
			ToolingOperation.<Void, SessionSnapshot>builder()
					.name("snapshot")
					.requestType(Void.class)
					.responseType(new TypeReference<>() {})
					.build();

	/**
	 * Starts remaining processes and completes scenario setup.
	 */
	public static final @NotNull ToolingOperation<Void, SessionSnapshot> START_ALL =
			ToolingOperation.<Void, SessionSnapshot>builder()
					.name("startAll")
					.requestType(Void.class)
					.responseType(new TypeReference<>() {})
					.build();

	/**
	 * Stops the environment and cancels pending interruptible work.
	 */
	public static final @NotNull ToolingOperation<Void, SessionSnapshot> STOP =
			ToolingOperation.<Void, SessionSnapshot>builder()
					.name("stop")
					.requestType(Void.class)
					.responseType(new TypeReference<>() {})
					.build();

	/**
	 * Invokes a contributed action against its explicit environment and target.
	 */
	public static final @NotNull ToolingOperation<ActionRequest, ActionResult> INVOKE =
			ToolingOperation.<ActionRequest, ActionResult>builder()
					.name("action")
					.requestType(ActionRequest.class)
					.responseType(new TypeReference<>() {})
					.build();
}
