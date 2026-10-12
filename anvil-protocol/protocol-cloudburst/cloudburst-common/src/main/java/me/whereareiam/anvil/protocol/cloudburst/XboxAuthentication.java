package me.whereareiam.anvil.protocol.cloudburst;

import com.google.gson.JsonParser;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.protocol.api.worker.WorkerAuthentication;
import me.whereareiam.anvil.protocol.api.worker.model.AccountRecord;
import me.whereareiam.anvil.protocol.api.worker.model.AuthenticationSession;
import me.whereareiam.anvil.protocol.api.worker.model.ResolvedAccount;
import me.whereareiam.anvil.protocol.cloudburst.identity.XboxCredentials;
import net.raphimc.minecraftauth.MinecraftAuth;
import net.raphimc.minecraftauth.bedrock.BedrockAuthManager;
import net.raphimc.minecraftauth.bedrock.model.MinecraftCertificateChain;
import net.raphimc.minecraftauth.bedrock.model.MinecraftMultiplayerToken;
import net.raphimc.minecraftauth.msa.model.MsaDeviceCode;
import net.raphimc.minecraftauth.msa.service.impl.DeviceCodeMsaAuthService;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * Microsoft device-code login of Xbox accounts for Bedrock players, and their refresh. The stored credentials are
 * the serialized {@link BedrockAuthManager}; the native worker stores them.
 *
 * <p>Xbox services start a session for a game version: a login reports the newest version of the built-in
 * releases, and a refresh the version of the player that signs in. A resolved session carries the account's
 * multiplayer token as its access token and the session key pair, certificate chain and device id as
 * {@link XboxCredentials}; the worker receives both only over its private standard input.</p>
 */
public final class XboxAuthentication implements WorkerAuthentication {
	@Override
	public @NotNull AccountRecord login(@NotNull String accountId, @NotNull MinecraftVersion version, @NotNull Consumer<String> output) {
		try {
			Consumer<MsaDeviceCode> prompt = code -> output.accept("Open " + code.getDirectVerificationUri());
			BedrockAuthManager manager = BedrockAuthManager.create(MinecraftAuth.createHttpClient(), version.toString())
					.login(DeviceCodeMsaAuthService::new, prompt);
			return refresh(accountId, manager).getAccount();
		} catch (IOException exception) {
			throw new IllegalStateException("Xbox authentication failed for account '" + accountId + "'", exception);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Xbox authentication was interrupted for account '" + accountId + "'", exception);
		} catch (TimeoutException exception) {
			throw new IllegalStateException("Xbox device-code authentication timed out for account '" + accountId + "'", exception);
		}
	}

	/**
	 * Refreshes a stored account for a player of the given Bedrock version; never log the returned token or
	 * credentials.
	 */
	@Override
	public @NotNull ResolvedAccount resolve(@NotNull AccountRecord account, @NotNull MinecraftVersion version) {
		try {
			return refresh(account.getAccountId(), decode(account, version));
		} catch (IOException exception) {
			throw new IllegalStateException("Could not refresh authentication account '" + account.getAccountId() + "'", exception);
		}
	}

	private BedrockAuthManager decode(AccountRecord account, MinecraftVersion version) {
		try {
			return BedrockAuthManager.fromJson(MinecraftAuth.createHttpClient(), version.toString(),
					JsonParser.parseString(account.getCredentials()).getAsJsonObject());
		} catch (RuntimeException exception) {
			// Parser exceptions may include the stored document, including refresh tokens.
			throw new IllegalStateException("Invalid stored authentication account '" + account.getAccountId() + "'");
		}
	}

	private ResolvedAccount refresh(String accountId, BedrockAuthManager manager) throws IOException {
		MinecraftMultiplayerToken token = manager.getMinecraftMultiplayerToken().getUpToDate();
		MinecraftCertificateChain chain = manager.getMinecraftCertificateChain().getUpToDate();
		if (token == null || chain == null)
			throw new IllegalStateException("Xbox account '" + accountId + "' has no Minecraft Bedrock multiplayer session");

		XboxCredentials credentials = XboxCredentials.builder()
				.sessionKeys(manager.getSessionKeyPair())
				.mojangJwt(chain.getMojangJwt())
				.identityJwt(chain.getIdentityJwt())
				.deviceId(manager.getDeviceId().toString().replace("-", ""))
				.build();
		return ResolvedAccount.builder()
				.account(AccountRecord.builder()
						.accountId(accountId)
						.username(token.getDisplayName())
						.uniqueId(token.getUuid())
						.credentials(BedrockAuthManager.toJson(manager).toString())
						.build())
				.session(AuthenticationSession.builder()
						.username(token.getDisplayName())
						.uuid(token.getUuid())
						.accessToken(token.getToken())
						.libraryCredentials(credentials.toText())
						.build())
				.build();
	}
}
