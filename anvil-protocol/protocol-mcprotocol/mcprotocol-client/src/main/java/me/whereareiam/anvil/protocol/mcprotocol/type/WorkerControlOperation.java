package me.whereareiam.anvil.protocol.mcprotocol.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Optional;

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

	public static @NotNull Optional<WorkerControlOperation> find(@NotNull String wireName) {
		return Arrays.stream(values()).filter(operation -> operation.wireName.equals(wireName)).findFirst();
	}
}
