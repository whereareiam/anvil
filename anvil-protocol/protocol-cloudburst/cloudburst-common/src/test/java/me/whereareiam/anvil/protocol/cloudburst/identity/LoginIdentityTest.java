package me.whereareiam.anvil.protocol.cloudburst.identity;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.whereareiam.anvil.protocol.cloudburst.client.model.ClientProfile;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginIdentityTest {
	private final ClientProfile profile = ClientProfile.builder().name("Alice").uniqueId(UUID.randomUUID()).build();

	@Test
	void signsAnOfflineChainThatNamesTheProfileAndItsOwnKey() throws Exception {
		LoginIdentity identity = LoginIdentity.offline(profile);

		List<String> chain = identity.chain();
		assertEquals(1, chain.size());
		assertFalse(identity.authenticated());
		assertNull(identity.token());

		String link = chain.getFirst();
		String key = Base64.getEncoder().encodeToString(identity.keyPair().getPublic().getEncoded());
		assertEquals("ES384", part(link, 0).get("alg").getAsString());
		assertEquals(key, part(link, 0).get("x5u").getAsString());
		assertTrue(verifies(link, identity.keyPair().getPublic()));

		JsonObject claims = part(link, 1);
		assertEquals(key, claims.get("identityPublicKey").getAsString());
		JsonObject extraData = claims.getAsJsonObject("extraData");
		assertEquals("Alice", extraData.get("displayName").getAsString());
		assertEquals(profile.getUniqueId().toString(), extraData.get("identity").getAsString());
		assertEquals("", extraData.get("XUID").getAsString());
		assertTrue(claims.get("exp").getAsLong() > claims.get("nbf").getAsLong());
	}

	@Test
	void encodesTokenPartsInTheStandardAlphabetThatNukkitServersDecode() {
		LoginIdentity identity = LoginIdentity.offline(profile);

		for (String token : List.of(identity.chain().getFirst(), identity.clientData("1.21.130", "127.0.0.1:19132")))
			for (String part : token.split("\\."))
				assertFalse(part.contains("-") || part.contains("_") || part.contains("="), part);
	}

	@Test
	void signsClientDataOfTheGameVersionAndAddressWithTheIdentityKey() throws Exception {
		LoginIdentity identity = LoginIdentity.offline(profile);

		String clientData = identity.clientData("1.21.130", "127.0.0.1:19132");

		assertTrue(verifies(clientData, identity.keyPair().getPublic()));
		JsonObject claims = part(clientData, 1);
		assertEquals("1.21.130", claims.get("GameVersion").getAsString());
		assertEquals("127.0.0.1:19132", claims.get("ServerAddress").getAsString());
		assertEquals("Alice", claims.get("ThirdPartyName").getAsString());
		assertEquals(64 * 64 * 4, Base64.getDecoder().decode(claims.get("SkinData").getAsString()).length);
		JsonObject patch = JsonParser.parseString(new String(Base64.getDecoder().decode(claims.get("SkinResourcePatch").getAsString()), StandardCharsets.UTF_8))
				.getAsJsonObject();
		assertEquals("geometry.humanoid.custom", patch.getAsJsonObject("geometry").get("default").getAsString());
	}

	@Test
	void handsAnXboxChainToTheCertifiedSessionKeyAndKeepsTheToken() throws Exception {
		KeyPair mojang = LoginSigner.generateKeyPair();
		KeyPair session = LoginSigner.generateKeyPair();
		JsonObject identityClaims = new JsonObject();
		identityClaims.addProperty("identityPublicKey", LoginSigner.publicKey(session));
		String mojangJwt = LoginSigner.sign(new JsonObject(), mojang);
		String identityJwt = LoginSigner.sign(identityClaims, mojang);
		XboxCredentials credentials = XboxCredentials.builder()
				.sessionKeys(session)
				.mojangJwt(mojangJwt)
				.identityJwt(identityJwt)
				.xuid("2535400000000000")
				.deviceId("0123456789abcdef")
				.build();

		LoginIdentity identity = LoginIdentity.xbox(profile, "multiplayer-token", XboxCredentials.parse(credentials.toText()));

		assertTrue(identity.authenticated());
		assertEquals("multiplayer-token", identity.token());
		assertEquals(session.getPublic(), identity.keyPair().getPublic());
		assertEquals(List.of(mojangJwt, identityJwt), identity.chain().subList(1, 3));
		String first = identity.chain().getFirst();
		assertTrue(verifies(first, session.getPublic()));
		assertEquals(LoginSigner.publicKey(mojang), part(first, 1).get("identityPublicKey").getAsString());
		assertTrue(part(first, 1).get("certificateAuthority").getAsBoolean());
		assertEquals("0123456789abcdef", part(identity.clientData("1.21.130", "127.0.0.1:19132"), 1).get("DeviceId").getAsString());
	}

	@Test
	void refusesMalformedXboxCredentialsWithoutQuotingThem() {
		var failure = assertThrows(IllegalArgumentException.class, () -> XboxCredentials.parse("{\"privateKey\":\"secret\""));

		assertEquals("The Xbox credentials of a Bedrock login are malformed", failure.getMessage());
	}

	private static JsonObject part(String token, int index) {
		String part = token.split("\\.")[index];
		return JsonParser.parseString(new String(Base64.getDecoder().decode(part), StandardCharsets.UTF_8)).getAsJsonObject();
	}

	private static boolean verifies(String token, PublicKey key) throws Exception {
		int end = token.lastIndexOf('.');
		Signature signature = Signature.getInstance("SHA384withECDSAinP1363Format");
		signature.initVerify(KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(key.getEncoded())));
		signature.update(token.substring(0, end).getBytes(StandardCharsets.US_ASCII));
		return signature.verify(Base64.getDecoder().decode(token.substring(end + 1)));
	}
}
