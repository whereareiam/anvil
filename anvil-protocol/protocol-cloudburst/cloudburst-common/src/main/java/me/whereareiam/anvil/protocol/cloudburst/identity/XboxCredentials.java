package me.whereareiam.anvil.protocol.cloudburst.identity;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.Builder;
import lombok.ToString;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * The Xbox secrets a Bedrock login needs besides its multiplayer token: the session key pair that Xbox services
 * certified, the certificate chain they issued for it, the account's XUID that chain names and the device the account
 * signed in on. The account workflow
 * writes them as the library credentials of a resolved session, which reach the worker only over its private
 * standard input, and the worker reads them back into a login identity.
 */
@Value
@Builder
public class XboxCredentials {
	/**
	 * Session key pair the chain certifies.
	 */
	@ToString.Exclude
	@NotNull KeyPair sessionKeys;

	/**
	 * Chain link Mojang signed for the account's Xbox token.
	 */
	@ToString.Exclude
	@NotNull String mojangJwt;

	/**
	 * Chain link naming the account's identity and the session public key.
	 */
	@ToString.Exclude
	@NotNull String identityJwt;

	/**
	 * XUID of the account, as the identity link names it.
	 */
	@NotNull String xuid;

	/**
	 * Device id the account signed in with, without dashes.
	 */
	@NotNull String deviceId;

	/**
	 * Writes the credentials as the text a resolved session carries.
	 *
	 * @return JSON text holding the keys and chain; never log it
	 */
	public @NotNull String toText() {
		JsonObject json = new JsonObject();
		json.addProperty("publicKey", Base64.getEncoder().encodeToString(sessionKeys.getPublic().getEncoded()));
		json.addProperty("privateKey", Base64.getEncoder().encodeToString(sessionKeys.getPrivate().getEncoded()));
		json.addProperty("mojangJwt", mojangJwt);
		json.addProperty("identityJwt", identityJwt);
		json.addProperty("xuid", xuid);
		json.addProperty("deviceId", deviceId);
		return json.toString();
	}

	/**
	 * Reads credentials written by {@link #toText()}.
	 *
	 * @param text JSON text of a resolved session
	 * @return the credentials
	 * @throws IllegalArgumentException when the text is not such credentials; the message never quotes it
	 */
	public static @NotNull XboxCredentials parse(@NotNull String text) {
		try {
			JsonObject json = JsonParser.parseString(text).getAsJsonObject();
			KeyFactory keys = KeyFactory.getInstance("EC");
			KeyPair sessionKeys = new KeyPair(
					keys.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(json.get("publicKey").getAsString()))),
					keys.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(json.get("privateKey").getAsString())))
			);
			return XboxCredentials.builder()
					.sessionKeys(sessionKeys)
					.mojangJwt(json.get("mojangJwt").getAsString())
					.identityJwt(json.get("identityJwt").getAsString())
					.xuid(json.get("xuid").getAsString())
					.deviceId(json.get("deviceId").getAsString())
					.build();
		} catch (RuntimeException | GeneralSecurityException exception) {
			// Parser messages can quote the text, which holds a private key.
			throw new IllegalArgumentException("The Xbox credentials of a Bedrock login are malformed");
		}
	}
}
