package me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2;

import com.github.steveice10.mc.protocol.data.game.BossBarColor;
import com.github.steveice10.mc.protocol.data.game.BossBarDivision;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundBossEventPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundKeepAlivePacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.title.ClientboundClearTitlesPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.title.ClientboundSetActionBarTextPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.title.ClientboundSetSubtitleTextPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.title.ClientboundSetTitleTextPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.title.ClientboundSetTitlesAnimationPacket;
import me.whereareiam.anvil.protocol.java.api.model.screen.BossEventAdd;
import me.whereareiam.anvil.protocol.java.api.model.screen.BossEventRemove;
import me.whereareiam.anvil.protocol.java.api.model.screen.BossEventUpdateName;
import me.whereareiam.anvil.protocol.java.api.model.screen.BossEventUpdateProgress;
import me.whereareiam.anvil.protocol.java.api.model.screen.BossEventUpdateProperties;
import me.whereareiam.anvil.protocol.java.api.model.screen.BossEventUpdateStyle;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
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

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class ScreenPacketsTest {
	private static final UUID BAR = new UUID(0, 1);

	private final ScreenPackets packets = new ScreenPackets();

	@Test
	void decodesTitlesSubtitlesTimesClearsAndActionBarsAsPlainText() {
		assertEquals(new ClientboundSetTitlesAnimation(5, 40, 5),
				packets.decode(new ClientboundSetTitlesAnimationPacket(5, 40, 5)));
		assertEquals(new ClientboundSetSubtitleText("by Alice"),
				packets.decode(new ClientboundSetSubtitleTextPacket(Component.text("by ").append(Component.text("Alice")))));
		assertEquals(new ClientboundSetTitleText("example.announcement Alice"), packets.decode(
				new ClientboundSetTitleTextPacket(Component.translatable("example.announcement", Component.text("Alice")))));
		assertEquals(new ClientboundSetActionBarText("press key.jump"), packets.decode(
				new ClientboundSetActionBarTextPacket(Component.text("press ").append(Component.keybind("key.jump")))));
		assertEquals(new ClientboundClearTitles(true), packets.decode(new ClientboundClearTitlesPacket(true)));
	}

	@Test
	void decodesEachBossBarActionWithTheVanillaColorAndOverlay() {
		assertEquals(new ClientboundBossEvent(BAR, BossEventAdd.builder()
				.name("Event")
				.progress(1F)
				.color(BossEventColor.BLUE)
				.overlay(BossEventOverlay.PROGRESS)
				.playMusic(true)
				.build()), packets.decode(new ClientboundBossEventPacket(BAR, Component.text("Event"), 1F, BossBarColor.CYAN,
				BossBarDivision.NONE, false, true, false)));
		assertEquals(new ClientboundBossEvent(BAR, new BossEventUpdateProgress(0.5F)),
				packets.decode(new ClientboundBossEventPacket(BAR, 0.5F)));
		assertEquals(new ClientboundBossEvent(BAR, new BossEventUpdateName("Event ends soon")),
				packets.decode(new ClientboundBossEventPacket(BAR, Component.text("Event ends soon"))));
		BossEventUpdateStyle style = new BossEventUpdateStyle(BossEventColor.GREEN, BossEventOverlay.NOTCHED_12);
		assertEquals(new ClientboundBossEvent(BAR, style),
				packets.decode(new ClientboundBossEventPacket(BAR, BossBarColor.LIME, BossBarDivision.NOTCHES_12)));
		assertEquals(new ClientboundBossEvent(BAR, new BossEventUpdateProperties(true, false, true)),
				packets.decode(new ClientboundBossEventPacket(BAR, true, false, true)));
		assertEquals(new ClientboundBossEvent(BAR, new BossEventRemove()),
				packets.decode(new ClientboundBossEventPacket(BAR)));
	}

	@Test
	void mapsEveryColorAndOverlayOfTheRelease() {
		List<BossEventColor> colors = Arrays.stream(BossBarColor.values())
				.map(color -> assertInstanceOf(ClientboundBossEvent.class,
						packets.decode(new ClientboundBossEventPacket(BAR, color, BossBarDivision.NONE))).getOperation())
				.map(operation -> assertInstanceOf(BossEventUpdateStyle.class, operation).getColor())
				.toList();
		List<BossEventOverlay> overlays = Arrays.stream(BossBarDivision.values())
				.map(division -> assertInstanceOf(ClientboundBossEvent.class,
						packets.decode(new ClientboundBossEventPacket(BAR, BossBarColor.PINK, division))).getOperation())
				.map(operation -> assertInstanceOf(BossEventUpdateStyle.class, operation).getOverlay())
				.toList();

		assertEquals(List.of(BossEventColor.values()), colors);
		assertEquals(List.of(BossEventOverlay.values()), overlays);
	}

	@Test
	void leavesPacketsOfOtherAreasToOtherTranslations() {
		assertNull(packets.decode(new ClientboundKeepAlivePacket(7)));
		assertNull(packets.encode(new UnknownPacket()));
	}

	@Test
	void carriesEveryScreenPacket() {
		assertEquals(Set.of(
				ClientboundSetTitleText.class,
				ClientboundSetSubtitleText.class,
				ClientboundSetTitlesAnimation.class,
				ClientboundClearTitles.class,
				ClientboundSetActionBarText.class,
				ClientboundBossEvent.class
		), packets.packets());
	}

	private static final class UnknownPacket implements ServerboundPacket {
	}
}
