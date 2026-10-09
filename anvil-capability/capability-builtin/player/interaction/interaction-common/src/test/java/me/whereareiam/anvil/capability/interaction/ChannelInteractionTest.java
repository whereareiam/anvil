package me.whereareiam.anvil.capability.interaction;

import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import me.whereareiam.anvil.capability.interaction.model.BlockPosition;
import me.whereareiam.anvil.capability.interaction.model.BlockUse;
import me.whereareiam.anvil.capability.interaction.model.EntityUse;
import me.whereareiam.anvil.capability.interaction.model.ItemUse;
import me.whereareiam.anvil.capability.interaction.type.BlockFace;
import me.whereareiam.anvil.capability.interaction.type.EntityInteraction;
import me.whereareiam.anvil.capability.interaction.type.Hand;
import me.whereareiam.anvil.capability.protocol.api.model.EventDescriptor;
import me.whereareiam.anvil.capability.protocol.api.player.channel.CapabilityChannel;
import me.whereareiam.anvil.capability.protocol.api.player.channel.Subscription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChannelInteractionTest {
	@Test
	void requestsAnItemUseWithTheHand() {
		RecordingChannel channel = new RecordingChannel();

		new ChannelInteraction(channel).useItem(Hand.MAIN);

		assertEquals(List.of(new Request("interaction.item", new ItemUse(Hand.MAIN))), channel.requests);
	}

	@Test
	void requestsABlockUseWithTheCoordinatesFaceAndHand() {
		RecordingChannel channel = new RecordingChannel();

		new ChannelInteraction(channel).block(BlockPosition.builder().x(4).y(-60).z(-7).build(), BlockFace.WEST, Hand.OFF);

		assertEquals(List.of(new Request("interaction.block", new BlockUse(4, -60, -7, BlockFace.WEST, Hand.OFF))), channel.requests);
	}

	@Test
	void requestsAnEntityInteractionWithItsKindAndHand() {
		RecordingChannel channel = new RecordingChannel();
		ChannelInteraction interaction = new ChannelInteraction(channel);

		interaction.entity(42, EntityInteraction.ATTACK, Hand.MAIN);
		interaction.entity(43, EntityInteraction.INTERACT, Hand.OFF);

		assertEquals(List.of(
				new Request("interaction.entity", new EntityUse(42, EntityInteraction.ATTACK, Hand.MAIN)),
				new Request("interaction.entity", new EntityUse(43, EntityInteraction.INTERACT, Hand.OFF))
		), channel.requests);
	}

	private record Request(String operation, @Nullable Object payload) {
	}

	/**
	 * Records each request; interaction neither subscribes nor waits.
	 */
	private static final class RecordingChannel implements CapabilityChannel {
		private final List<Request> requests = new ArrayList<>();

		@Override
		public <Q, R> @Nullable R request(@NotNull ChannelOperation<Q, R> channelOperation, @Nullable Q request) {
			requests.add(new Request(channelOperation.getId(), request));
			return null;
		}

		@Override
		public <E> @NotNull Subscription subscribe(@NotNull EventDescriptor<E> eventDescriptor, @NotNull Consumer<E> listener) {
			throw new AssertionError("Interaction observes no events");
		}

		@Override
		public void await(@NotNull BooleanSupplier condition, @NotNull String description, @NotNull Duration timeout) {
			throw new AssertionError("Interaction does not wait");
		}

		@Override
		public @NotNull Set<String> installedCapabilities() {
			return Set.of(InteractionProvider.ID);
		}
	}
}
