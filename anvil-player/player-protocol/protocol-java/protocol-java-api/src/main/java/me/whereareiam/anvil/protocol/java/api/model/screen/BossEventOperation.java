package me.whereareiam.anvil.protocol.java.api.model.screen;

/**
 * What a boss event packet does to its bar: add it, remove it, or change one part of it.
 *
 * <pre>{@code
 * switch (event.getOperation()) {
 *     case BossEventAdd add -> bars.put(event.getId(), add);
 *     case BossEventRemove ignored -> bars.remove(event.getId());
 *     case BossEventUpdateProgress update -> progress(event.getId(), update.getProgress());
 *     // ...
 * }
 * }</pre>
 */
public sealed interface BossEventOperation permits BossEventAdd, BossEventRemove, BossEventUpdateProgress,
		BossEventUpdateName, BossEventUpdateStyle, BossEventUpdateProperties {
}
