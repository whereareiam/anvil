package me.whereareiam.anvil.protocol.mcprotocol.client.v1_17_1;

import com.github.steveice10.mc.protocol.packet.ingame.server.title.ServerClearTitlesPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.title.ServerSetActionBarTextPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.title.ServerSetSubtitleTextPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.title.ServerSetTitleTextPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.title.ServerSetTitlesAnimationPacket;
import me.whereareiam.anvil.protocol.java.api.packet.screen.ClientboundClearTitles;
import me.whereareiam.anvil.protocol.java.api.packet.screen.ClientboundSetActionBarText;
import me.whereareiam.anvil.protocol.java.api.packet.screen.ClientboundSetSubtitleText;
import me.whereareiam.anvil.protocol.java.api.packet.screen.ClientboundSetTitleText;
import me.whereareiam.anvil.protocol.java.api.packet.screen.ClientboundSetTitlesAnimation;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScreenPacketsTest {
	private final ScreenPackets packets = new ScreenPackets();

	@Test
	void decodesTheTitlePacketsOfTheRelease() {
		assertEquals(new ClientboundSetTitleText("anvil:title"),
				packets.decode(new ServerSetTitleTextPacket(Component.text("anvil:title"))));
		assertEquals(new ClientboundSetSubtitleText("anvil:subtitle"),
				packets.decode(new ServerSetSubtitleTextPacket(Component.text("anvil:subtitle"))));
		assertEquals(new ClientboundSetTitlesAnimation(5, 40, 5),
				packets.decode(new ServerSetTitlesAnimationPacket(5, 40, 5)));
		assertEquals(new ClientboundClearTitles(true), packets.decode(new ServerClearTitlesPacket(true)));
		assertEquals(new ClientboundSetActionBarText("anvil:action-bar"),
				packets.decode(new ServerSetActionBarTextPacket(Component.text("anvil:action-bar"))));
	}
}
