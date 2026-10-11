package me.whereareiam.anvil.environment.execution.managed.process;

import me.whereareiam.anvil.api.exception.ProvisioningException;
import me.whereareiam.anvil.environment.execution.api.PortReservations;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Reserves listener ports below the operating systems' ephemeral ranges, across every engine that shares
 * the lock directory.
 * The process binds its port seconds after preparation selected it. A port from the ephemeral range can be
 * handed to any outgoing connection or other listener in the meantime, so candidates come from a range the
 * operating system does not assign by itself. A reserved port holds an operating-system lock on its file in
 * the lock directory until it is released, so neither this engine nor an engine in another JVM hands it out
 * again while its process starts or restarts. The operating system drops the lock when the JVM ends.
 */
public final class PortSelection implements PortReservations {
	/** Linux assigns ephemeral ports from 32768, Windows and macOS from 49152. */
	private static final int FIRST = 20000;
	private static final int LAST = 32767;
	/**
	 * Lock files this JVM holds. Closing any descriptor of a file drops every lock the process has on it, so
	 * engines of one JVM must not even open a file another of them holds.
	 */
	private static final Set<Path> HELD = ConcurrentHashMap.newKeySet();

	private final Path locks;
	private final int first;
	private final int last;
	private final Map<Integer, FileChannel> reserved = new HashMap<>();

	/**
	 * Selects ports coordinated through lock files in a directory that every cooperating engine uses.
	 *
	 * @param locks directory holding one lock file per port, created when the first port is reserved
	 */
	public PortSelection(@NotNull Path locks) {
		this(locks, FIRST, LAST);
	}

	PortSelection(Path locks, int first, int last) {
		this.locks = locks.toAbsolutePath().normalize();
		this.first = first;
		this.last = last;
	}

	/**
	 * Reserves a port that no cooperating engine holds and that can be bound on the address now. The probe
	 * socket is closed before returning; a listener outside Anvil may still take the port before the process
	 * binds it.
	 *
	 * @param bindAddress local bind address
	 * @return reserved port
	 * @throws ProvisioningException when the address or lock directory is unusable or no candidate is free
	 */
	@Override
	public synchronized int reserve(@NotNull String bindAddress) {
		InetAddress address;
		try {
			address = InetAddress.getByName(bindAddress);
			Files.createDirectories(locks);
		} catch (IOException failure) {
			throw new ProvisioningException("Could not select a port on " + bindAddress, failure);
		}

		// Starts at a random candidate, so engines rarely contend for the same lock.
		int candidates = last - first + 1;
		int start = ThreadLocalRandom.current().nextInt(candidates);
		for (int offset = 0; offset < candidates; offset++) {
			int port = first + (start + offset) % candidates;
			FileChannel lock = lock(port);
			if (lock == null) continue;
			if (!free(address, port)) {
				close(port, lock);
				continue;
			}

			reserved.put(port, lock);
			return port;
		}

		throw new ProvisioningException("Could not find an unassigned port on " + bindAddress);
	}

	@Override
	public synchronized void release(int port) {
		FileChannel lock = reserved.remove(port);
		if (lock != null) close(port, lock);
	}

	/**
	 * Locks the port's file, or returns {@code null} when another engine holds it.
	 * The files stay in place: removing one would let two engines lock different files for the same port.
	 */
	private @Nullable FileChannel lock(int port) {
		Path file = file(port);
		if (!HELD.add(file)) return null;

		FileChannel channel = null;
		try {
			channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
			if (channel.tryLock() != null) return channel;
		} catch (IOException failure) {
			if (channel != null) close(port, channel);
			else HELD.remove(file);
			throw new ProvisioningException("Could not lock port " + port + " in " + locks, failure);
		}

		close(port, channel);
		return null;
	}

	private void close(int port, FileChannel channel) {
		try {
			channel.close();
		} catch (IOException ignored) {
			// The descriptor and its lock are released even when closing reports a failure.
		} finally {
			HELD.remove(file(port));
		}
	}

	private Path file(int port) {
		return locks.resolve(port + ".lock");
	}

	private static boolean free(InetAddress address, int port) {
		try (ServerSocket socket = new ServerSocket(port, 1, address)) {
			return socket.isBound();
		} catch (IOException occupied) {
			return false;
		}
	}
}
