package me.whereareiam.anvil.edition.java.protocol.model.overlay;

/**
 * What a boss event packet does to its bar: add it, remove it, or change one part of it.
 *
 * <pre>{@code
 * switch (event.operation()) {
 *     case BossEventAdd add -> bars.put(event.id(), add);
 *     case BossEventRemove ignored -> bars.remove(event.id());
 *     case BossEventUpdateProgress update -> progress(event.id(), update.progress());
 *     // ...
 * }
 * }</pre>
 */
public sealed interface BossEventOperation permits BossEventAdd, BossEventRemove, BossEventUpdateProgress,
		BossEventUpdateName, BossEventUpdateStyle, BossEventUpdateProperties {
}
