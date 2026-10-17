package me.whereareiam.anvil.capability.entities;

import me.whereareiam.anvil.api.model.player.Expectation;
import me.whereareiam.anvil.capability.entities.channel.EntitiesChannel;
import me.whereareiam.anvil.capability.entities.model.EntityChange;
import me.whereareiam.anvil.capability.entities.model.SeenEntity;
import me.whereareiam.anvil.capability.entities.type.EntityChangeKind;
import me.whereareiam.anvil.protocol.api.channel.ProtocolChannel;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/**
 * Entities capability driven over a player's typed channel: every change the worker's entities extension emits is kept
 * in the player's history, across reconnects, and applied to the entities of the latest connection, which tell which
 * entities are still known. A change of a newer connection first makes every entity of the older one disappear, and
 * a late change of an older connection is dropped. Reads of the entities, whose positions change without events, ask
 * the worker. The channel releases the event subscription together with the player.
 *
 * <p>Changes arrive on the channel's thread while waits read them on the test's, so the history and the entities
 * are guarded by this object's monitor; reads copy what they return.</p>
 */
final class ChannelEntities implements Entities {
	private final ProtocolChannel channel;
	private final List<EntityChange> history = new ArrayList<>();
	private final Map<Integer, SeenEntity> entities = new LinkedHashMap<>();
	private int connection;

	ChannelEntities(@NotNull ProtocolChannel channel) {
		this.channel = channel;
		channel.subscribe(EntitiesChannel.CHANGED, this::record);
	}

	@Override
	public @NotNull List<SeenEntity> all() {
		return Objects.requireNonNull(channel.request(EntitiesChannel.KNOWN, null)).getEntities();
	}

	@Override
	public @NotNull SeenEntity present(@NotNull Predicate<SeenEntity> matcher, @NotNull Duration timeout) {
		AtomicReference<SeenEntity> found = new AtomicReference<>();
		channel.await(() -> {
			found.set(all().stream().filter(matcher).findFirst().orElse(null));
			return found.get() != null;
		}, matcher instanceof EntityQuery query ? "know an entity " + query : "know a matching entity", timeout);
		return found.get();
	}

	@Override
	public synchronized @NotNull EntityWatch watch() {
		return new Watch(history.size());
	}

	private synchronized void record(EntityChange change) {
		int changed = change.getEntity().getConnection();
		if (changed < connection) return;
		if (changed > connection) {
			for (SeenEntity entity : entities.values())
				history.add(new EntityChange(EntityChangeKind.DISAPPEARED, entity, null));
			entities.clear();
			connection = changed;
		}

		history.add(change);
		SeenEntity entity = change.getEntity();
		if (change.getKind() == EntityChangeKind.DISAPPEARED) entities.remove(entity.getId());
		else entities.put(entity.getId(), entity);
	}

	private synchronized boolean known(SeenEntity entity) {
		SeenEntity current = entities.get(entity.getId());
		return current != null && current.sameEntity(entity);
	}

	/**
	 * Finds the first change from a position of the history at which an entity starts to satisfy a condition.
	 *
	 * @return the position of the change, or -1 when there is none yet
	 */
	private synchronized int find(Predicate<SeenEntity> matcher, int from) {
		for (int index = from; index < history.size(); index++)
			if (appears(history.get(index), matcher)) return index;

		return -1;
	}

	private synchronized EntityChange at(int index) {
		return history.get(index);
	}

	private synchronized List<EntityChange> from(int position) {
		return List.copyOf(history.subList(Math.min(position, history.size()), history.size()));
	}

	private static boolean appears(EntityChange change, Predicate<SeenEntity> matcher) {
		if (change.getKind() == EntityChangeKind.DISAPPEARED || !matcher.test(change.getEntity())) return false;

		SeenEntity previous = change.getPrevious();
		return previous == null || !matcher.test(previous);
	}

	private static String name(SeenEntity entity) {
		String label = entity.label();
		return entity.getType() + " " + entity.getId() + (label == null ? "" : " labelled '" + label + "'");
	}

	private static String describe(Predicate<SeenEntity> matcher) {
		return matcher instanceof EntityQuery query
				? "see an entity appear " + query
				: "see a matching entity appear";
	}

	/**
	 * A watch over this player's history, which waits through the player's channel.
	 */
	private final class Watch implements EntityWatch {
		private int position;

		private Watch(int position) {
			this.position = position;
		}

		@Override
		public @NotNull SeenEntity appeared(@NotNull Predicate<SeenEntity> matcher, @NotNull Duration timeout) {
			channel.await(() -> find(matcher, position()) >= 0, describe(matcher), timeout);
			return take(matcher);
		}

		@Override
		public void disappeared(@NotNull SeenEntity entity, @NotNull Duration timeout) {
			channel.await(() -> !known(entity), "see " + name(entity) + " disappear", timeout);
		}

		@Override
		public void neverAppeared(@NotNull Predicate<SeenEntity> matcher, @NotNull Duration duration) {
			try {
				channel.await(() -> find(matcher, position()) >= 0, describe(matcher), duration);
			} catch (IllegalStateException quiet) {
				if (find(matcher, position()) < 0) return;
			}

			throw new IllegalStateException("Player saw an entity appear that must not appear: "
					+ name(at(find(matcher, position())).getEntity()));
		}

		@Override
		public @NotNull Expectation expectAppeared(@NotNull Predicate<SeenEntity> matcher) {
			return Expectation.of(describe(matcher), () -> find(matcher, position()) >= 0);
		}

		@Override
		public @NotNull Expectation expectDisappeared(@NotNull SeenEntity entity) {
			return Expectation.of("see " + name(entity) + " disappear", () -> !known(entity));
		}

		@Override
		public @NotNull List<EntityChange> unread() {
			return from(position());
		}

		private synchronized int position() {
			return position;
		}

		/**
		 * Moves the watch directly after the change found from its position, which a successful wait guarantees.
		 */
		private synchronized @NotNull SeenEntity take(Predicate<SeenEntity> matcher) {
			int index = find(matcher, position);
			if (index < 0) throw new IllegalStateException("Player did not " + describe(matcher));

			position = index + 1;
			return at(index).getEntity();
		}
	}
}
