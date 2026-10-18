package me.whereareiam.anvil.protocol.worker;

import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.exception.ProtocolUnavailableException;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The edition protocols one worker player speaks, which its {@code PlayerBindingContext.protocol(Class)} returns.
 * Each protocol also hands out scoped views: a view reports every subscription it creates, so the worker can release
 * the listeners of an extension whose binding failed.
 *
 * <pre>{@code
 * JavaPacketDispatcher java = new JavaPacketDispatcher(LIBRARY_ID, connection);
 * WorkerProtocols protocols = WorkerProtocols.builder(LIBRARY_ID)
 *         .speak(JavaProtocol.class, java, java::scoped)
 *         .build();
 * }</pre>
 */
public final class WorkerProtocols {
	private final String libraryId;
	private final Map<Class<?>, Spoken<?>> protocols;

	private WorkerProtocols(String libraryId, Map<Class<?>, Spoken<?>> protocols) {
		this.libraryId = libraryId;
		this.protocols = Map.copyOf(protocols);
	}

	/**
	 * Starts the protocols of a library's worker players.
	 *
	 * @param libraryId the library's identifier, named when an extension asks for a protocol it does not speak
	 * @return builder of the protocols
	 */
	public static @NotNull Builder builder(@NotNull String libraryId) {
		return new Builder(libraryId);
	}

	/**
	 * Returns a protocol the player speaks.
	 *
	 * @param type protocol interface
	 * @param <P> protocol type
	 * @return the player's protocol
	 * @throws ProtocolUnavailableException when the player does not speak it
	 */
	public <P> @NotNull P protocol(@NotNull Class<P> type) {
		return type.cast(require(type).protocol());
	}

	/**
	 * Returns a view of a protocol the player speaks that reports each subscription it creates.
	 *
	 * @param type protocol interface
	 * @param track receives every subscription the view creates
	 * @param <P> protocol type
	 * @return the scoped view
	 * @throws ProtocolUnavailableException when the player does not speak it
	 */
	public <P> @NotNull P protocol(@NotNull Class<P> type, @NotNull Consumer<Subscription> track) {
		return type.cast(require(type).scoped(track));
	}

	private Spoken<?> require(Class<?> type) {
		Spoken<?> spoken = protocols.get(type);
		if (spoken != null) return spoken;

		String names = protocols.keySet().stream().map(Class::getName).sorted().collect(Collectors.joining(", "));
		String offered = names.isEmpty() ? "no protocol" : names;
		throw new ProtocolUnavailableException("the " + libraryId + " worker speaks " + offered + ", not "
				+ type.getName());
	}

	/**
	 * Collects the protocols a library's worker players speak.
	 */
	public static final class Builder {
		private final String libraryId;
		private final Map<Class<?>, Spoken<?>> protocols = new LinkedHashMap<>();

		private Builder(String libraryId) {
			this.libraryId = libraryId;
		}

		/**
		 * Adds a protocol the player speaks.
		 *
		 * @param type protocol interface
		 * @param protocol the player's protocol
		 * @param scoped creates a view of the protocol that reports each subscription it creates
		 * @param <P> protocol type
		 * @return this builder
		 * @throws IllegalArgumentException when the protocol was already added
		 */
		public <P> @NotNull Builder speak(
				@NotNull Class<P> type,
				@NotNull P protocol,
				@NotNull Function<Consumer<Subscription>, ? extends P> scoped
		) {
			if (protocols.putIfAbsent(type, new Spoken<>(protocol, scoped)) != null)
				throw new IllegalArgumentException("Protocol already spoken: " + type.getName());

			return this;
		}

		/**
		 * Creates the protocols.
		 *
		 * @return immutable protocols
		 */
		public @NotNull WorkerProtocols build() {
			return new WorkerProtocols(libraryId, protocols);
		}
	}

	private record Spoken<P>(P protocol, Function<Consumer<Subscription>, ? extends P> views) {
		private P scoped(Consumer<Subscription> track) {
			return views.apply(track);
		}
	}
}
