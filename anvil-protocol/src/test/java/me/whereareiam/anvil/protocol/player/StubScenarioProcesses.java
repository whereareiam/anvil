package me.whereareiam.anvil.protocol.player;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.process.ProcessConsole;
import me.whereareiam.anvil.api.process.RunningProcess;
import me.whereareiam.anvil.api.process.ScenarioProcesses;
import me.whereareiam.anvil.api.process.type.RunningProxy;
import me.whereareiam.anvil.api.process.type.RunningServer;
import me.whereareiam.anvil.api.type.ProcessState;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;

final class StubScenarioProcesses implements ScenarioProcesses {
	private RunningServer server;

	StubScenarioProcesses(String name, Path workspace) {
		server = new StubServer(name, workspace, new InetSocketAddress("127.0.0.1", 25565));
	}

	@Override
	public @NotNull Collection<RunningProcess> all() {
		return List.of(server);
	}

	@Override
	public @NotNull RunningProcess get(@NotNull String name) {
		return server(name);
	}

	@Override
	public @NotNull Collection<RunningServer> servers() {
		return List.of(server);
	}

	@Override
	public @NotNull RunningServer server(@NotNull String name) {
		if (!server.name().equals(name)) throw new NoSuchElementException(name);
		return server;
	}

	@Override
	public @NotNull Collection<RunningProxy> proxies() {
		return List.of();
	}

	@Override
	public @NotNull RunningProxy proxy(@NotNull String name) {
		throw new NoSuchElementException(name);
	}

	@Override
	public @NotNull RunningProcess restart(@NotNull String name) {
		RunningServer previous = server(name);
		server = new StubServer(name, previous.workDirectory(),
				new InetSocketAddress("127.0.0.1", previous.address().getPort() + 1));
		return server;
	}

	@RequiredArgsConstructor
	private static final class StubServer implements RunningServer {
		private final String name;
		private final Path workspace;
		private final InetSocketAddress address;

		@Override
		public @NotNull String name() {
			return name;
		}

		@Override
		public @NotNull InetSocketAddress address() {
			return address;
		}

		@Override
		public @NotNull Path workDirectory() {
			return workspace;
		}

		@Override
		public @NotNull ProcessState state() {
			return ProcessState.READY;
		}

		@Override
		public @NotNull ProcessConsole console() {
			throw new UnsupportedOperationException();
		}
	}
}
