package me.whereareiam.anvil.protocol.mcprotocol.worker.fixture;

/**
 * Probe port implementation that a test's probe segment declares as its service provider.
 */
public final class ProbePacketsAdapter implements ProbePackets {
	@Override
	public String name() {
		return "probe";
	}
}
