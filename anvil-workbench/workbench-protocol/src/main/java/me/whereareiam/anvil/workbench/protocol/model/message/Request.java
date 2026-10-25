package me.whereareiam.anvil.workbench.protocol.model.message;

import lombok.Value;
import me.whereareiam.anvil.workbench.protocol.model.WorkbenchRequest;
import org.jetbrains.annotations.NotNull;

/**
 * One request on an engine's control connection.
 */
@Value
public class Request {
	/**
	 * Number the answer carries.
	 */
	long id;

	/**
	 * The request.
	 */
	@NotNull WorkbenchRequest<?> request;
}
