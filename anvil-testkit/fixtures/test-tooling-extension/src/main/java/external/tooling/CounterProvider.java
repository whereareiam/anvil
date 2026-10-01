package external.tooling;

import java.util.concurrent.atomic.AtomicLong;
import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import me.whereareiam.anvil.capability.api.player.PlayerCapabilityContext;
import me.whereareiam.anvil.capability.api.player.PlayerCapabilityProvider;
import org.jetbrains.annotations.NotNull;

/**
 * Supplies a capability without depending on any built-in capability API.
 */
public final class CounterProvider implements PlayerCapabilityProvider<Counter> {
	@Override
	public @NotNull CapabilityDescriptor descriptor() { return CapabilityDescriptor.builder().id("external.counter").build(); }
	@Override
	public @NotNull Class<Counter> capability() { return Counter.class; }
	@Override
	public @NotNull Counter create(@NotNull PlayerCapabilityContext context) {
		AtomicLong value = new AtomicLong();
		return new Counter() {
			@Override public long add(long amount) { return value.addAndGet(amount); }
			@Override public long value() { return value.get(); }
		};
	}
}
