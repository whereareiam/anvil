package me.whereareiam.anvil.capability.players;

import me.whereareiam.anvil.api.model.player.Expectation;
import me.whereareiam.anvil.api.model.player.PlayerProfile;
import me.whereareiam.anvil.api.model.player.PlayerState;
import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.capability.api.model.platform.PlatformEvent;
import me.whereareiam.anvil.capability.api.platform.ProcessPlatform;
import me.whereareiam.anvil.capability.api.type.PlatformEventType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A process whose platform reports the joins and leaves a test adds as they happen.
 */
final class ReportingProcess implements ProcessPlatform {
	private final boolean proxy;
	private final List<PlatformEvent> events = new CopyOnWriteArrayList<>();

	ReportingProcess(boolean proxy) {
		this.proxy = proxy;
	}

	ReportingProcess joined(@NotNull PlayerProfile profile, @Nullable String backend) {
		return report(PlatformEventType.JOINED, profile, backend);
	}

	ReportingProcess left(@NotNull PlayerProfile profile, @Nullable String backend) {
		return report(PlatformEventType.LEFT, profile, backend);
	}

	private ReportingProcess report(PlatformEventType type, PlayerProfile profile, @Nullable String backend) {
		long position = events.size() + 1;
		events.add(PlatformEvent.builder()
				.process("lobby")
				.proxy(proxy)
				.position(position)
				.at(Instant.ofEpochSecond(position))
				.type(type)
				.profile(profile)
				.server(backend)
				.build());
		return this;
	}

	@Override
	public boolean execute(@NotNull String command) {
		throw new AssertionError("The players capability runs no commands");
	}

	@Override
	public @NotNull List<PlatformEvent> events() {
		return List.copyOf(events);
	}

	/**
	 * A simulated player that was granted the given profiles.
	 */
	static SimulatedPlayer player(@NotNull String name, @NotNull PlayerProfile... granted) {
		return new SimulatedPlayer() {
			@Override
			public @NotNull String name() {
				return name;
			}

			@Override
			public @NotNull String clientVersion() {
				return "1.21.11";
			}

			@Override
			public @NotNull PlayerProfile requestedProfile() {
				return new PlayerProfile(name, UUID.nameUUIDFromBytes(name.getBytes()));
			}

			@Override
			public @Nullable PlayerProfile grantedProfile() {
				return granted.length == 0 ? null : granted[granted.length - 1];
			}

			@Override
			public @NotNull List<PlayerProfile> grantedProfiles() {
				return List.of(granted);
			}

			@Override
			public @NotNull <C extends PlayerCapability> C capability(@NotNull Class<C> type) {
				throw new AssertionError("The players capability uses no player capability");
			}

			@Override
			public boolean hasCapability(@NotNull Class<? extends PlayerCapability> type) {
				return false;
			}

			@Override
			public int awaitFirst(@NotNull Duration timeout, @NotNull Expectation... expectations) {
				throw new AssertionError("The players capability awaits nothing on the player");
			}

			@Override
			public @NotNull PlayerState state() {
				return PlayerState.builder().build();
			}

			@Override
			public void destroy() {
				throw new AssertionError("The players capability destroys no player");
			}
		};
	}
}
