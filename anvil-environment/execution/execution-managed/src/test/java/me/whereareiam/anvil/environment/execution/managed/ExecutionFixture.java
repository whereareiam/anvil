package me.whereareiam.anvil.environment.execution.managed;

import me.whereareiam.anvil.api.model.java.JavaRequirement;
import me.whereareiam.anvil.environment.execution.api.ExecutionProvider;
import me.whereareiam.anvil.environment.execution.api.ExecutionSession;
import me.whereareiam.anvil.environment.execution.api.model.ExecutionContext;
import me.whereareiam.anvil.environment.execution.api.model.ExecutionPlan;
import me.whereareiam.anvil.environment.execution.api.model.JavaCommand;
import me.whereareiam.anvil.environment.execution.api.model.process.ProcessRequest;
import me.whereareiam.anvil.environment.execution.api.model.process.ProcessSpec;
import me.whereareiam.anvil.environment.execution.api.preparation.ExecutionPreparation;
import me.whereareiam.anvil.environment.execution.api.preparation.PreparedLaunch;
import me.whereareiam.anvil.environment.execution.api.preparation.PreparedProcess;
import me.whereareiam.anvil.environment.execution.api.process.ProcessExecution;
import me.whereareiam.anvil.environment.execution.api.process.ProcessTarget;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * Execution-only owners used to observe preparation, generations, and independent cleanup failures.
 */
final class ExecutionFixture {
	final Path directory;
	final List<String> calls = Collections.synchronizedList(new ArrayList<>());
	final Map<String, Target> targets = new LinkedHashMap<>();
	final Preparation preparation = new Preparation();
	final Provider provider = new Provider();
	String failingTarget;
	String failingPreparation;
	String failingConfiguration;
	String failingAttachment;
	RuntimeException failure = new IllegalStateException("requested failure");
	RuntimeException targetCleanupFailure;
	RuntimeException sessionCleanupFailure;
	RuntimeException attachmentCleanupFailure;

	ExecutionFixture(Path directory) {
		this.directory = directory;
	}

	ManagedProcessService service() {
		return new ManagedProcessService(Map.of("fixture", provider));
	}

	ExecutionPlan plan(ProcessSpec... processes) {
		ExecutionContext context = ExecutionContext.builder().cacheDirectory(directory).bindAddress("127.0.0.1")
				.localRuntime((request, source) -> { throw new AssertionError("Not a local runtime"); })
				.runtimeValidator((properties, request) -> { throw new AssertionError("No image probe"); })
				.imageLocks(path -> { throw new AssertionError("No image coordination"); }).build();
		return ExecutionPlan.builder().executionId("fixture").context(context).processes(List.of(processes))
				.parallelism(1).startupMemoryMegabytes(1024).startupTimeout(Duration.ofSeconds(3))
				.stopTimeout(Duration.ofSeconds(1)).build();
	}

	ProcessSpec spec(String name, boolean proxy, String... dependencies) {
		return ProcessSpec.builder().request(ProcessRequest.builder().name(name).workspace(directory.resolve(name))
				.javaRequirement(JavaRequirement.builder().build()).minimumJavaVersion(21).build())
				.proxy(proxy).dependencies(Set.of(dependencies)).readinessPattern(Pattern.compile("READY"))
				.stopCommand("stop").memoryMegabytes(256).build();
	}

	final class Provider implements ExecutionProvider {
		@Override
		public @NotNull String id() { return "fixture"; }

		@Override
		public @NotNull ExecutionSession open(@NotNull ExecutionContext context) {
			calls.add("session-open");
			return new ExecutionSession() {
				@Override
				public @NotNull ProcessTarget prepare(@NotNull ProcessRequest request) {
					calls.add("target:" + request.getName());
					if (request.getName().equals(failingTarget)) throw failure;
					Target target = new Target(request.getName(), targets.size());
					targets.put(request.getName(), target);
					return target;
				}

				@Override
				public void close() {
					calls.add("session-close");
					if (sessionCleanupFailure != null) throw sessionCleanupFailure;
				}
			};
		}
	}

	final class Preparation implements ExecutionPreparation {
		final Map<String, Map<String, InetSocketAddress>> observedPeers = new LinkedHashMap<>();

		@Override
		public void open() { calls.add("open"); }

		@Override
		public @NotNull PreparedProcess prepare(@NotNull ProcessSpec process, @NotNull ProcessTarget target,
				@NotNull Map<String, InetSocketAddress> peers) {
			String name = process.getRequest().getName();
			calls.add("prepare:" + name);
			observedPeers.put(name, peers);
			if (name.equals(failingPreparation)) throw failure;
			try {
				Files.createDirectories(process.getRequest().getWorkspace());
			} catch (IOException failure) {
				throw new UncheckedIOException(failure);
			}
			return new PreparedProcess() {
				private int generation;

				@Override
				public @NotNull PreparedLaunch launch() {
					int current = ++generation;
					calls.add("configure:" + name + ":" + current);
					if (name.equals(failingConfiguration)) throw failure;
					return new PreparedLaunch() {
						@Override
						public @NotNull JavaCommand command() {
							return JavaCommand.builder().jar(directory.resolve("input.jar")).memoryMegabytes(256)
									.environmentVariable("GENERATION", Integer.toString(current)).build();
						}

						@Override
						public void started() {
							calls.add("attach:" + name + ":" + current);
							if (name.equals(failingAttachment)) throw failure;
						}

						@Override
						public void close() {
							calls.add("detach:" + name + ":" + current);
							if (attachmentCleanupFailure != null) throw attachmentCleanupFailure;
						}
					};
				}

				@Override
				public void finish(boolean successful) { calls.add("prepared-finish:" + name + ":" + successful); }
			};
		}

		@Override
		public void finish(boolean successful) { calls.add("finish:" + successful); }
	}

	final class Target implements ProcessTarget {
		final String name;
		final int index;
		final List<JavaCommand> commands = new ArrayList<>();

		Target(String name, int index) {
			this.name = name;
			this.index = index;
		}

		@Override
		public @NotNull InetSocketAddress address() { return new InetSocketAddress("127.0.0.1", 20000 + index); }
		@Override
		public @NotNull InetSocketAddress peerAddress() { return new InetSocketAddress("127.0.0.1", 30000 + index); }
		@Override
		public @NotNull String bindAddress() { return "127.0.0.1"; }
		@Override
		public @NotNull InetSocketAddress agentAddress() { return new InetSocketAddress("127.0.0.1", 40000 + index); }
		@Override
		public int agentPort() { return 40000 + index; }
		@Override
		public @NotNull String agentBindAddress() { return "127.0.0.1"; }
		@Override
		public @NotNull ProcessExecution start(@NotNull JavaCommand command) {
			calls.add("start:" + name);
			commands.add(command);
			return new Running(name, calls);
		}
		@Override
		public void close() {
			calls.add("target-close:" + name);
			if (targetCleanupFailure != null) throw targetCleanupFailure;
		}
	}

	private static final class Running implements ProcessExecution {
		private final String name;
		private final List<String> calls;
		private final AtomicBoolean alive = new AtomicBoolean(true);
		private final CompletableFuture<Void> exited = new CompletableFuture<>();
		private final PipedInputStream output = new PipedInputStream();
		private final PipedOutputStream writer;
		private final ByteArrayOutputStream input = new ByteArrayOutputStream() {
			@Override
			public void flush() {
				String command = toString(StandardCharsets.UTF_8).trim();
				reset();
				if (command.equals("stop")) end();
			}
		};

		Running(String name, List<String> calls) {
			this.name = name;
			this.calls = calls;
			try {
				writer = new PipedOutputStream(output);
				writer.write("READY\n".getBytes(StandardCharsets.UTF_8));
				writer.flush();
			} catch (IOException failure) {
				throw new UncheckedIOException(failure);
			}
		}

		@Override
		public @NotNull InputStream output() { return output; }
		@Override
		public @NotNull OutputStream input() { return input; }
		@Override
		public boolean isAlive() { return alive.get(); }
		@Override
		public @NotNull CompletableFuture<Void> onExit() { return exited; }
		@Override
		public boolean await(@NotNull Duration timeout) throws InterruptedException {
			try {
				exited.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
				return true;
			} catch (TimeoutException failure) {
				return false;
			} catch (ExecutionException failure) {
				throw new IllegalStateException(failure);
			}
		}
		@Override
		public void terminate(boolean force) { end(); }

		private void end() {
			if (!alive.compareAndSet(true, false)) return;
			calls.add("stop:" + name);
			try {
				writer.close();
			} catch (IOException failure) {
				throw new UncheckedIOException(failure);
			} finally {
				exited.complete(null);
			}
		}
	}
}
