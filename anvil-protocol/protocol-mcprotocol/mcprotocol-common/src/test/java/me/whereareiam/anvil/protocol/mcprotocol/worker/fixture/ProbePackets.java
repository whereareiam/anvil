package me.whereareiam.anvil.protocol.mcprotocol.worker.fixture;

/**
 * Port of the probe capability, implemented only by a probe segment that tests place on the worker class path.
 */
public interface ProbePackets {
	/**
	 * Names the adapter.
	 *
	 * @return adapter name
	 */
	String name();
}
