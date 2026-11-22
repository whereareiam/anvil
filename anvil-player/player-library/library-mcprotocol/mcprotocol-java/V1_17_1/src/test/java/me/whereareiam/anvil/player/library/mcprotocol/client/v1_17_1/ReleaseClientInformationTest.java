package me.whereareiam.anvil.player.library.mcprotocol.client.v1_17_1;

import com.github.steveice10.mc.protocol.packet.ingame.client.ClientSettingsPacket;
import me.whereareiam.anvil.player.library.mcprotocol.client.model.ClientSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ReleaseClientInformationTest {
	private final ReleaseClientInformation information = new ReleaseClientInformation();

	@Test
	void sendsClientSettingsWithoutTextFiltering() {
		ClientSettingsPacket settings = assertInstanceOf(ClientSettingsPacket.class,
				information.packet(ClientSettings.builder().locale("en_us").viewDistance(8).build()));

		assertEquals("en_us", settings.getLocale());
		assertEquals(8, settings.getRenderDistance());
		assertFalse(settings.isTextFilteringEnabled());
	}
}
