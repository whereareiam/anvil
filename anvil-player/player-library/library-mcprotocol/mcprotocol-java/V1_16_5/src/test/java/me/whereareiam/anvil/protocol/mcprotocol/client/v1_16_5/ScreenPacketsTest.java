package me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5;

import com.github.steveice10.mc.protocol.data.game.BossBarColor;
import com.github.steveice10.mc.protocol.data.game.BossBarDivision;
import com.github.steveice10.mc.protocol.data.game.TitleAction;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerBossBarPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerTitlePacket;
import me.whereareiam.anvil.protocol.java.api.model.screen.BossEventAdd;
import me.whereareiam.anvil.protocol.java.api.model.screen.BossEventUpdateProgress;
import me.whereareiam.anvil.protocol.java.api.packet.screen.ClientboundBossEvent;
import me.whereareiam.anvil.protocol.java.api.packet.screen.ClientboundClearTitles;
import me.whereareiam.anvil.protocol.java.api.packet.screen.ClientboundSetActionBarText;
import me.whereareiam.anvil.protocol.java.api.packet.screen.ClientboundSetSubtitleText;
import me.whereareiam.anvil.protocol.java.api.packet.screen.ClientboundSetTitleText;
import me.whereareiam.anvil.protocol.java.api.packet.screen.ClientboundSetTitlesAnimation;
import me.whereareiam.anvil.protocol.java.api.type.BossEventColor;
import me.whereareiam.anvil.protocol.java.api.type.BossEventOverlay;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScreenPacketsTest {
	private static final UUID BAR = new UUID(0, 3);

	private final ScreenPackets packets = new ScreenPackets();

	@Test
	void reportsEachActionOfTheTitlePacketAsItsOwnPacket() {
		assertEquals(new ClientboundSetTitleText("anvil:title"),
				packets.decode(new ServerTitlePacket(TitleAction.TITLE, Component.text("anvil:title"))));
		assertEquals(new ClientboundSetSubtitleText("anvil:subtitle"),
				packets.decode(new ServerTitlePacket(TitleAction.SUBTITLE, Component.text("anvil:subtitle"))));
		assertEquals(new ClientboundSetActionBarText("anvil:action-bar"),
				packets.decode(new ServerTitlePacket(TitleAction.ACTION_BAR, Component.text("anvil:action-bar"))));
		assertEquals(new ClientboundSetTitlesAnimation(5, 40, 5), packets.decode(new ServerTitlePacket(5, 40, 5)));
		assertEquals(new ClientboundClearTitles(false), packets.decode(new ServerTitlePacket(TitleAction.CLEAR)));
		assertEquals(new ClientboundClearTitles(true), packets.decode(new ServerTitlePacket(TitleAction.RESET)));
	}

	@Test
	void decodesBossBarsByTheirAction() {
		assertEquals(new ClientboundBossEvent(BAR, BossEventAdd.builder()
						.name("anvil:boss")
						.progress(0.5F)
						.color(BossEventColor.BLUE)
						.overlay(BossEventOverlay.NOTCHED_10)
						.darkenScreen(true)
						.playMusic(false)
						.createWorldFog(false)
						.build()),
				packets.decode(new ServerBossBarPacket(BAR, Component.text("anvil:boss"), 0.5F, BossBarColor.CYAN,
						BossBarDivision.NOTCHES_10, true, false, false)));
		assertEquals(new ClientboundBossEvent(BAR, new BossEventUpdateProgress(0.25F)),
				packets.decode(new ServerBossBarPacket(BAR, 0.25F)));
	}
}
