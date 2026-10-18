package me.whereareiam.anvil.protocol.mcprotocol.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reserved process/player lifecycle operations. Capability operations remain open and namespaced.
 */
@Getter
@RequiredArgsConstructor
public enum WorkerControlOperation {
	CREATE_PLAYER("create"),
	DESTROY_PLAYER("destroy"),
	SHUTDOWN("shutdown");

	private final String wireName;

	/**
	 * Returns the wire names of every lifecycle operation, which no worker extension may register.
	 *
	 * @return immutable wire names
	 */
	public static @NotNull Set<String> wireNames() {
		return Arrays.stream(values()).map(WorkerControlOperation::getWireName).collect(Collectors.toUnmodifiableSet());
	}

	public static @NotNull Optional<WorkerControlOperation> find(@NotNull String wireName) {
		return Arrays.stream(values()).filter(operation -> operation.wireName.equals(wireName)).findFirst();
	}
}
