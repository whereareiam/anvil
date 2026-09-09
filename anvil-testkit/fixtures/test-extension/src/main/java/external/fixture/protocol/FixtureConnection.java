package external.fixture.protocol;

/**
 * Service contract supplied by the fixture backend and consumed by its capability adapters.
 * It intentionally exposes no Anvil implementation or MCProtocolLib types.
 */
public interface FixtureConnection {
	void connect();
	void disconnect();
	boolean connected();
	void send(String payload);
	String lastSent();
}
