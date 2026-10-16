package me.whereareiam.anvil.protocol.cloudburst.identity;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

/**
 * Creates the P-384 keys of Bedrock logins and signs their tokens as compact ES384 JSON web signatures whose
 * {@code x5u} header names the signing public key, as Bedrock clients sign their chain links and client data.
 *
 * <p>The signer encodes the parts of these tokens in the standard Base64 alphabet: servers descended from Nukkit
 * decode them only in that alphabet, and JSON web signature libraries accept it too.</p>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class LoginSigner {
	private static final Base64.Encoder PART = Base64.getEncoder().withoutPadding();

	/**
	 * Generates a new P-384 key pair.
	 */
	static @NotNull KeyPair generateKeyPair() {
		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
			generator.initialize(new ECGenParameterSpec("secp384r1"));
			return generator.generateKeyPair();
		} catch (GeneralSecurityException exception) {
			throw new IllegalStateException("This JVM cannot create the P-384 keys of Bedrock logins", exception);
		}
	}

	/**
	 * Returns the Base64 encoding of a key pair's public key, as {@code x5u} headers and {@code identityPublicKey}
	 * claims name it.
	 */
	static @NotNull String publicKey(@NotNull KeyPair keys) {
		return Base64.getEncoder().encodeToString(keys.getPublic().getEncoded());
	}

	/**
	 * Signs a payload with a key pair's private key.
	 *
	 * @return compact ES384 signature of the payload
	 */
	static @NotNull String sign(@NotNull JsonObject payload, @NotNull KeyPair keys) {
		JsonObject header = new JsonObject();
		header.addProperty("alg", "ES384");
		header.addProperty("x5u", publicKey(keys));
		String content = encode(header) + "." + encode(payload);

		try {
			Signature signature = Signature.getInstance("SHA384withECDSAinP1363Format");
			signature.initSign(keys.getPrivate());
			signature.update(content.getBytes(StandardCharsets.US_ASCII));
			return content + "." + PART.encodeToString(signature.sign());
		} catch (GeneralSecurityException exception) {
			throw new IllegalStateException("Could not sign a Bedrock login token", exception);
		}
	}

	/**
	 * Reads the header of a compact signature without verifying it.
	 *
	 * @throws IllegalArgumentException when the token is not a compact signature
	 */
	static @NotNull JsonObject header(@NotNull String token) {
		int end = token.indexOf('.');
		if (end < 0) throw new IllegalArgumentException("Not a compact JSON web signature");

		// Tokens of Xbox services use the URL-safe alphabet.
		String part = token.substring(0, end).replace('-', '+').replace('_', '/');
		String header = new String(Base64.getDecoder().decode(part), StandardCharsets.UTF_8);
		return JsonParser.parseString(header).getAsJsonObject();
	}

	private static String encode(JsonObject json) {
		return PART.encodeToString(json.toString().getBytes(StandardCharsets.UTF_8));
	}
}
