package me.whereareiam.anvil.tooling.api.model;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * Shared name and payload schemas for one tooling operation. Declaring an operation does not install
 * a handler; the runner binds its behavior and cancellation policy separately.
 *
 * @param <Q> request model; Void declares an operation without a payload
 * @param <R> non-null response model, including parameterized collection types
 */
@Value
@Builder
public class ToolingOperation<Q, R> {
	/**
	 * Stable protocol name used to locate the runner's registered handler.
	 */
	@NotNull String name;

	/**
	 * Payload decoded before execution. Void operations use null as their request value.
	 */
	@NotNull Class<Q> requestType;

	/**
	 * Complete response type retained by the client for correlated decoding.
	 * A type reference preserves collection element types that a raw Class would erase.
	 */
	@NotNull TypeReference<R> responseType;
}
