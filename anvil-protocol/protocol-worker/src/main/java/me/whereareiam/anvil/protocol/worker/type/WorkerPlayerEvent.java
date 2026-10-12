package me.whereareiam.anvil.protocol.worker.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Core player lifecycle events. Capability-owned event names remain open extension points.
 */
@Getter
@RequiredArgsConstructor
public enum WorkerPlayerEvent {
	CONNECTION("player.connection"),
	CONNECTED("session.connected"),
	DISCONNECTED("session.disconnected"),
	DESTROYED("player.destroyed");

	private final String wireName;
}
