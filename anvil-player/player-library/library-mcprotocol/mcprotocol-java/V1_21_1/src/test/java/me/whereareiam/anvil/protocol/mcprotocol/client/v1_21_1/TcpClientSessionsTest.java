package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_1;

import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientProfile;
import org.geysermc.mcprotocollib.auth.GameProfile;
import org.geysermc.mcprotocollib.protocol.packet.login.clientbound.ClientboundGameProfilePacket;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TcpClientSessionsTest {
	@Test
	void readsTheProfileTheLoginSuccessGrantsInTheLibrarysOwnProfile() {
		UUID granted = new UUID(0, 2);

		ClientProfile profile = TcpClientSessions.granted(
				new ClientboundGameProfilePacket(new GameProfile(granted, "Alice_Renamed"), false));

		assertEquals(ClientProfile.builder().name("Alice_Renamed").uniqueId(granted).build(), profile);
	}
}
