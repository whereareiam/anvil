package external.fixture.capability;

import me.whereareiam.anvil.api.process.ProcessCapability;
import org.jetbrains.annotations.NotNull;

/**
 * Calls the owning process's native fixture operation without a simulated player.
 */
public interface ProcessEcho extends ProcessCapability {
	/**
	 * Returns the response from this process's native fixture handler.
	 *
	 * @param value request text
	 * @return handler response
	 */
	@NotNull String echo(@NotNull String value);

	/**
	 * Changes the native handler's prefix through an operation without a response payload.
	 *
	 * @param value prefix used by later echo requests in this process generation
	 */
	void prefix(@NotNull String value);

	/**
	 * Reads the native handler's prefix through an operation without a request payload.
	 *
	 * @return prefix used by this process generation
	 */
	@NotNull String prefix();
}
