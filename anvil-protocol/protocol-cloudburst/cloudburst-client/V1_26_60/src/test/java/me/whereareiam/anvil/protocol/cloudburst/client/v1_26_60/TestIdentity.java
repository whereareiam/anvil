package me.whereareiam.anvil.protocol.cloudburst.client.v1_26_60;

import me.whereareiam.anvil.protocol.cloudburst.client.ClientIdentity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.List;

/**
 * An identity whose chain and client data name what they were made of instead of being signed.
 */
final class TestIdentity implements ClientIdentity {
	private final KeyPair keyPair;
	private final @Nullable String token;

	TestIdentity(@Nullable String token) throws Exception {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
		generator.initialize(new ECGenParameterSpec("secp384r1"));
		this.keyPair = generator.generateKeyPair();
		this.token = token;
	}

	@Override
	public @NotNull KeyPair keyPair() {
		return keyPair;
	}

	@Override
	public @NotNull List<String> chain() {
		return List.of("chain-link");
	}

	@Override
	public @Nullable String token() {
		return token;
	}

	@Override
	public boolean authenticated() {
		return token != null;
	}

	@Override
	public @NotNull String clientData(@NotNull String gameVersion, @NotNull String serverAddress) {
		return "client-data " + gameVersion + " " + serverAddress;
	}
}
