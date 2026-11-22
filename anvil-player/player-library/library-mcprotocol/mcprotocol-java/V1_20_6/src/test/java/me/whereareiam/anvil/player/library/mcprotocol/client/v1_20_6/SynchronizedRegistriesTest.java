package me.whereareiam.anvil.player.library.mcprotocol.client.v1_20_6;

import me.whereareiam.anvil.edition.java.protocol.model.configuration.RegistryEntry;
import me.whereareiam.anvil.edition.java.protocol.model.nbt.NbtCompound;
import me.whereareiam.anvil.edition.java.protocol.packet.configuration.ClientboundRegistryData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SynchronizedRegistriesTest {
	private static final String CLOCKS = "minecraft:world_clock";

	private final SynchronizedRegistries registries = new SynchronizedRegistries();

	@Test
	void looksUpAnEntryByItsIndexInRegistryOrder() {
		registries.record(new ClientboundRegistryData(CLOCKS,
				List.of(clock("minecraft:overworld"), clock("minecraft:the_end"))));

		assertEquals(clock("minecraft:the_end"), registries.entry(CLOCKS, 1));
		assertNull(registries.entry(CLOCKS, 2));
		assertNull(registries.entry(CLOCKS, -1));
	}

	@Test
	void findsNothingInARegistryTheConfigurationDidNotSynchronize() {
		assertNull(registries.entry(CLOCKS, 0));
	}

	@Test
	void replacesARegistryALaterConfigurationSendsAgain() {
		registries.record(new ClientboundRegistryData(CLOCKS, List.of(clock("minecraft:overworld"))));
		registries.record(new ClientboundRegistryData(CLOCKS, List.of(clock("example:slow"))));

		assertEquals(clock("example:slow"), registries.entry(CLOCKS, 0));
	}

	private static RegistryEntry clock(String key) {
		return new RegistryEntry(key, NbtCompound.EMPTY);
	}
}
