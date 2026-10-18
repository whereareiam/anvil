package me.whereareiam.anvil.protocol.mcprotocol.client.v1_17_1;

import com.github.steveice10.mc.protocol.packet.ingame.client.ClientPongPacket;
import com.github.steveice10.mc.protocol.packet.ingame.client.ClientSettingsPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerKeepAlivePacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerPingPacket;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class ReleaseAnswersTest {
	private final ReleaseAnswers answers = new ReleaseAnswers();

	@Test
	void sendsClientSettingsWithoutTextFiltering() {
		ClientSettingsPacket settings = assertInstanceOf(ClientSettingsPacket.class,
				answers.clientInformation(ClientSettings.builder().locale("en_us").viewDistance(8).build()));

		assertEquals("en_us", settings.getLocale());
		assertEquals(8, settings.getRenderDistance());
		assertFalse(settings.isTextFilteringEnabled());
	}

	@Test
	void answersEveryPingAndNothingElse() {
		assertEquals(7, assertInstanceOf(ClientPongPacket.class, answers.answer(new ServerPingPacket(7))).getId());
		assertNull(answers.answer(new ServerKeepAlivePacket(7)));
	}
}
