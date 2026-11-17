package me.whereareiam.anvil.capability.protocol.api.player;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.api.model.player.PlayerConnection;
import me.whereareiam.anvil.api.model.player.PlayerProfile;
import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.capability.api.event.CapabilityEvent;
import me.whereareiam.anvil.protocol.api.channel.ProtocolChannel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * The context a protocol-backed player provider creates its capability in, for the tests of host providers. It hands
 * the provider the test's channel, usually a {@code TestProtocolChannel}, and the capabilities the test
 * {@link #withCapability offers} as the provider's declared dependencies. The player is Alice, who requested her
 * offline profile, has not been granted one yet, speaks Minecraft 1.21.11 and is connected to the process
 * {@code proxy}; the library supplies no services.
 *
 * <p>The built-in capabilities register no lifecycle actions, so the context refuses a registration: a provider that
 * starts registering one fails its tests until they check what it registered.</p>
 *
 * <pre>{@code
 * TestProtocolChannel channel = new TestProtocolChannel(Set.of(DialogsChannel.ID));
 * Dialogs dialogs = new DialogsProvider().create(new TestProtocolPlayerCapabilityContext(channel)
 * 		.withCapability(Commands.class, commands));
 * }</pre>
 */
public final class TestProtocolPlayerCapabilityContext implements ProtocolPlayerCapabilityContext {
	private static final String PLAYER = "Alice";

	private final ProtocolChannel channel;
	private final Map<Class<?>, PlayerCapability> capabilities = new ConcurrentHashMap<>();

	/**
	 * Creates the context of a player whose library hands its capabilities the given channel.
	 *
	 * @param channel the player's channel
	 */
	public TestProtocolPlayerCapabilityContext(@NotNull ProtocolChannel channel) {
		this.channel = channel;
	}

	/**
	 * Offers a capability the provider declared as a dependency, as composition would after creating it.
	 *
	 * @param type the capability type the provider looks up
	 * @param capability the capability
	 * @param <T> capability type
	 * @return this context
	 */
	public <T extends PlayerCapability> @NotNull TestProtocolPlayerCapabilityContext withCapability(
			@NotNull Class<T> type,
			@NotNull T capability
	) {
		capabilities.put(type, capability);

		return this;
	}

	@Override
	public @NotNull ProtocolChannel channel() {
		return channel;
	}

	@Override
	public @NotNull <T> Optional<T> findService(@NotNull Class<T> type) {
		return Optional.empty();
	}

	@Override
	public @NotNull String playerName() {
		return PLAYER;
	}

	@Override
	public @NotNull MinecraftVersion clientVersion() {
		return MinecraftVersion.parse("1.21.11");
	}

	@Override
	public @NotNull PlayerProfile requestedProfile() {
		return new PlayerProfile(PLAYER, UUID.nameUUIDFromBytes(PLAYER.getBytes(StandardCharsets.UTF_8)));
	}

	@Override
	public @Nullable PlayerProfile grantedProfile() {
		return null;
	}

	@Override
	public @NotNull PlayerConnection connection() {
		return PlayerConnection.to("proxy");
	}

	@Override
	public <E extends CapabilityEvent> void on(@NotNull Class<E> event, @NotNull Consumer<E> action) {
		throw new AssertionError("The capability registered an action for " + event.getSimpleName()
				+ ", which the built-in capabilities never do");
	}

	@Override
	public @NotNull <T extends PlayerCapability> Optional<T> findCapability(@NotNull Class<T> type) {
		return Optional.ofNullable(capabilities.get(type)).map(type::cast);
	}
}
