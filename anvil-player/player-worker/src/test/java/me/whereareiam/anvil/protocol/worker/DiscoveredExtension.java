package me.whereareiam.anvil.protocol.worker;

import me.whereareiam.anvil.protocol.api.worker.OperationRegistry;
import me.whereareiam.anvil.protocol.api.worker.PlayerBindingContext;
import me.whereareiam.anvil.protocol.api.worker.WorkerBinding;
import me.whereareiam.anvil.protocol.api.worker.WorkerExtension;
import org.jetbrains.annotations.NotNull;

/**
 * An extension the test class path declares as a service, which discovery finds.
 */
public final class DiscoveredExtension implements WorkerExtension<Object> {
	static final String ID = "test.discovered";

	@Override
	public @NotNull String id() {
		return ID;
	}

	@Override
	public @NotNull Class<Object> nativeSessionType() {
		return Object.class;
	}

	@Override
	public @NotNull WorkerBinding bind(
			@NotNull PlayerBindingContext<Object> player,
			@NotNull OperationRegistry operations
	) {
		return () -> { };
	}
}
