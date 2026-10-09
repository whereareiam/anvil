package me.whereareiam.anvil.integration.intellij.tooling.process;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToolingConnectionTest {
	@Test
	void cancellationBeforePreparationPreventsAcquisition() {
		AtomicInteger starts = new AtomicInteger();
		ToolingConnection process = new ToolingConnection(builder -> {
			starts.incrementAndGet();
			throw new AssertionError("Cancelled launch acquired a child");
		}, Runnable::run);
		process.stop();

		assertThrows(IOException.class, () -> process.start(new ProcessBuilder("unused")));
		assertEquals(0, starts.get());
		process.awaitTermination();
	}

	@Test
	void stopTerminatesARunnerWhoseCommandWriteIsBlocked() throws Exception {
		ControlledToolingProcess child = new ControlledToolingProcess(true);
		child.holdCommandFlush();
		var connection = new ToolingConnection(builder -> child, task -> new Thread(task).start());
		connection.startRunner(new ProcessBuilder("runner"));
		var sending = CompletableFuture.runAsync(() -> {
			try {
				connection.send("{\"operation\":\"console\",\"id\":\"1\"}");
			} catch (IOException failure) {
				throw new CompletionException(failure);
			}
		});
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while (!child.isCommandFlushWaiting() && System.nanoTime() < deadline) Thread.sleep(5);
		assertTrue(child.isCommandFlushWaiting());

		connection.stop();

		assertTrue(child.waitFor(5, TimeUnit.SECONDS), "A blocked write must not prevent termination");
		assertEquals(143, child.exitValue());
		assertDoesNotThrow(() -> sending.handle((ignored, failure) -> null).get(5, TimeUnit.SECONDS));
	}

	@Test
	void stoppingRejectsCommandsBeforeAsynchronousCleanupClosesTheRunner() throws Exception {
		ControlledToolingProcess child = new ControlledToolingProcess(true);
		List<Runnable> cleanup = new ArrayList<>();
		ToolingConnection process = new ToolingConnection(builder -> child, cleanup::add);
		try {
			process.startRunner(new ProcessBuilder("unused"));
			process.send("{\"operation\":\"console\",\"text\":\"help\"}");
			process.stop();
			process.stop();

			assertThrows(IOException.class, () -> process.send("{\"operation\":\"console\"}"));
			assertEquals(1, child.getRequests().size());
			assertEquals(1, cleanup.size());
			assertFalse(child.isCloseRequested());
			cleanup.getFirst().run();
			process.awaitTermination();
			assertTrue(child.isCloseRequested());
			assertFalse(child.isAlive());
		} finally {
			child.complete(0);
		}
	}

	@Test
	void cancellingPreparationTerminatesTheChildWithoutOpeningRunnerInput() throws Exception {
		ControlledToolingProcess child = new ControlledToolingProcess(false);
		ToolingConnection process = new ToolingConnection(builder -> child, Runnable::run);
		try {
			process.start(new ProcessBuilder("unused"));
			process.stop();
			process.awaitTermination();

			assertFalse(child.isAlive());
			assertFalse(child.isCloseRequested());
			assertThrows(IOException.class, () -> process.startRunner(new ProcessBuilder("unused")));
		} finally {
			child.complete(0);
		}
	}
	@Test
	void interruptedWaitStillObservesExitBeforeReturningAndRestoresInterrupt() throws Exception {
		var child = new DelayedExitProcess();
		var connection = new ToolingConnection(builder -> child, ignored -> {});
		connection.start(new ProcessBuilder("unused"));
		var interrupted = new AtomicBoolean();
		var completed = new CountDownLatch(1);
		Thread waiter = new Thread(() -> {
			Thread.currentThread().interrupt();
			connection.awaitTermination();
			interrupted.set(Thread.currentThread().isInterrupted());
			completed.countDown();
		});
		waiter.start();
		try {
			assertTrue(child.killed.await(5, TimeUnit.SECONDS));
			assertEquals(1, completed.getCount());
		} finally {
			child.exited.countDown();
			waiter.join(5000);
		}

		assertFalse(waiter.isAlive());
		assertEquals(0, completed.getCount());
		assertTrue(interrupted.get());
	}

	@Test
	void rejectedTerminationSchedulingStillKillsAndWaitsForTheChild() throws Exception {
		var child = new ControlledToolingProcess(false);
		var rejection = new RejectedExecutionException("Executor closed");
		var connection = new ToolingConnection(builder -> child, task -> { throw rejection; });
		connection.start(new ProcessBuilder("unused"));

		assertSame(rejection, assertThrows(RejectedExecutionException.class, connection::close));
		assertFalse(child.isAlive());
		assertTrue(connection.isStopping());
	}

	@Test
	void cancellationDuringAcquisitionCannotLeaveTheNewChildRunning() throws Exception {
		var child = new ControlledToolingProcess(false);
		var entered = new CountDownLatch(1);
		var acquired = new CountDownLatch(1);
		List<Runnable> cleanup = new ArrayList<>();
		var connection = new ToolingConnection(builder -> {
			entered.countDown();
			try {
				if (!acquired.await(5, TimeUnit.SECONDS)) throw new IOException("Acquisition was not released");
			} catch (InterruptedException failure) {
				Thread.currentThread().interrupt();
				throw new IOException(failure);
			}
			return child;
		}, cleanup::add);
		var started = CompletableFuture.supplyAsync(() -> {
			try {
				return connection.start(new ProcessBuilder("unused"));
			} catch (IOException failure) {
				throw new CompletionException(failure);
			}
		});
		try {
			assertTrue(entered.await(5, TimeUnit.SECONDS));
			connection.stop();
		} finally {
			acquired.countDown();
		}

		var failure = assertThrows(ExecutionException.class, () -> started.get(5, TimeUnit.SECONDS));
		assertInstanceOf(IOException.class, failure.getCause());
		cleanup.forEach(Runnable::run);
		assertFalse(child.isAlive());
	}

	@Test
	void readerIsClosedEvenWhenWaitingForTheProcessFails() throws Exception {
		var waitFailure = new IllegalStateException("Native wait failed");
		var closeFailure = new IOException("Diagnostic close failed");
		var closed = new AtomicBoolean();
		var child = new FailingWaitProcess(waitFailure, new ByteArrayInputStream(new byte[0]) {

			@Override
			public void close() throws IOException {
				closed.set(true);
				throw closeFailure;
			}
		});
		List<Runnable> tasks = new ArrayList<>();
		var connection = new ToolingConnection(builder -> child, tasks::add);
		connection.startRunner(new ProcessBuilder("unused"));
		try {
			assertSame(waitFailure, assertThrows(IllegalStateException.class,
					() -> connection.readRunner(child, line -> {}, line -> {}, failure -> {})));
			tasks.getFirst().run();
			assertSame(waitFailure, assertThrows(IllegalStateException.class, connection::close));
			assertTrue(closed.get());
			assertArrayEquals(new Throwable[] {closeFailure}, waitFailure.getSuppressed());
		} finally {
			child.destroy();
			tasks.getLast().run();
		}
	}

	@RequiredArgsConstructor
	private static final class FailingWaitProcess extends Process {
		private final RuntimeException failure;
		private final InputStream errors;
		private boolean alive = true;

		@Override

		public OutputStream getOutputStream() {

			return OutputStream.nullOutputStream();

		}

		@Override
		public InputStream getInputStream() {
			return InputStream.nullInputStream();
		}

		@Override
		public InputStream getErrorStream() {
			return errors;
		}

		@Override
		public int waitFor() {
			if (alive) throw failure;
			return 0;
		}

		@Override
		public int exitValue() {
			if (alive) throw new IllegalThreadStateException();
			return 0;
		}

		@Override
		public boolean isAlive() {
			return alive;
		}

		@Override
		public void destroy() {
			alive = false;
		}
	}

	private static final class DelayedExitProcess extends Process {
		private final CountDownLatch killed = new CountDownLatch(1);
		private final CountDownLatch exited = new CountDownLatch(1);

		@Override
		public OutputStream getOutputStream() {
			return OutputStream.nullOutputStream();
		}

		@Override
		public InputStream getInputStream() {
			return InputStream.nullInputStream();
		}

		@Override
		public InputStream getErrorStream() {
			return InputStream.nullInputStream();
		}

		@Override
		public int waitFor() throws InterruptedException {
			exited.await();
			return 0;
		}

		@Override
		public int exitValue() {
			if (isAlive()) throw new IllegalThreadStateException();
			return 0;
		}

		@Override
		public boolean isAlive() {
			return exited.getCount() != 0;
		}

		@Override
		public void destroy() {
			killed.countDown();
		}
	}

}
