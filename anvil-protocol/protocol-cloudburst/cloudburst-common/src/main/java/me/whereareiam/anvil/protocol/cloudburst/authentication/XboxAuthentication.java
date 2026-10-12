package me.whereareiam.anvil.protocol.cloudburst.authentication;

import com.google.gson.JsonParser;
import me.whereareiam.anvil.api.model.player.AuthenticationAccount;
import me.whereareiam.anvil.protocol.cloudburst.client.CloudburstClient;
import me.whereareiam.anvil.protocol.cloudburst.identity.XboxCredentials;
import me.whereareiam.anvil.protocol.worker.WorkerAuthentication;
import me.whereareiam.anvil.protocol.worker.model.AuthenticationSession;
import net.raphimc.minecraftauth.MinecraftAuth;
import net.raphimc.minecraftauth.bedrock.BedrockAuthManager;
import net.raphimc.minecraftauth.bedrock.model.MinecraftCertificateChain;
import net.raphimc.minecraftauth.bedrock.model.MinecraftMultiplayerToken;
import net.raphimc.minecraftauth.msa.model.MsaDeviceCode;
import net.raphimc.minecraftauth.msa.service.impl.DeviceCodeMsaAuthService;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * Microsoft device-code login of Xbox accounts for Bedrock players, and their refresh, backed by private account
 * persistence.
 *
 * <p>A resolved session carries the account's multiplayer token as its access token and the session key pair,
 * certificate chain and device id as {@link XboxCredentials}; the worker receives both only over its private
 * standard input.</p>
 */
public final class XboxAuthentication implements WorkerAuthentication {
	/**
	 * Game version reported to Xbox services when a session starts. Services accept any current release; the
	 * newest version of the built-in release data is reported.
	 */
	private static final String GAME_VERSION = "1.26.60";

	private final AuthenticationAccountStore accounts;

	/**
	 * Creates the workflow of the accounts in a directory.
	 *
	 * @param accountsDirectory local account store directory
	 */
	public XboxAuthentication(@NotNull Path accountsDirectory) {
		accounts = new AuthenticationAccountStore(accountsDirectory);
	}

	@Override
	public void login(@NotNull String accountId, @NotNull Consumer<String> output) {
		accounts.accountFile(accountId);
		try {
			Consumer<MsaDeviceCode> prompt = code -> output.accept("Open " + code.getDirectVerificationUri());
			BedrockAuthManager manager = BedrockAuthManager.create(MinecraftAuth.createHttpClient(), GAME_VERSION)
					.login(DeviceCodeMsaAuthService::new, prompt);
			AuthenticationSession session = refreshAndSave(accountId, manager);
			output.accept("Authenticated " + session.getUsername() + " (" + session.getUuid()
					+ ") as account '" + accountId + "'.");
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
	 * Refreshes a saved account for private delivery to a worker; never log the returned token or credentials.
	 */
	@Override
	public @NotNull AuthenticationSession resolve(@NotNull String accountId) {
		try {
			BedrockAuthManager manager = decode(accountId, accounts.credentials(accountId));
			return refreshAndSave(accountId, manager);
		} catch (IOException exception) {
			throw new IllegalStateException("Could not refresh authentication account '" + accountId + "'", exception);
		}
	}

	@Override
	public void logout(@NotNull String accountId, @NotNull Consumer<String> output) {
		try {
			if (accounts.delete(accountId)) {
				output.accept("Removed authentication account '" + accountId + "'.");
				return;
			}
			output.accept("Authentication account '" + accountId + "' did not exist.");
		} catch (IOException exception) {
			throw new IllegalStateException("Could not remove authentication account '" + accountId + "'", exception);
		}
	}

	@Override
	public @NotNull Collection<AuthenticationAccount> accounts() {
		try {
			return accounts.accounts(CloudburstClient.LIBRARY_ID);
		} catch (IOException exception) {
			throw new IllegalStateException("Could not list authentication accounts", exception);
		}
	}

	private BedrockAuthManager decode(String accountId, String json) {
		try {
			return BedrockAuthManager.fromJson(MinecraftAuth.createHttpClient(), GAME_VERSION, JsonParser.parseString(json).getAsJsonObject());
		} catch (RuntimeException exception) {
			// Parser exceptions may include the stored document, including refresh tokens.
			throw new IllegalStateException("Invalid stored authentication account '" + accountId + "'");
		}
	}

	private AuthenticationSession refreshAndSave(String accountId, BedrockAuthManager manager) throws IOException {
		MinecraftMultiplayerToken token = manager.getMinecraftMultiplayerToken().getUpToDate();
		MinecraftCertificateChain chain = manager.getMinecraftCertificateChain().getUpToDate();
		if (token == null || chain == null)
			throw new IllegalStateException("Xbox account '" + accountId + "' has no Minecraft Bedrock multiplayer session");

		accounts.write(accountId, CloudburstClient.LIBRARY_ID, token.getDisplayName(), token.getUuid(), BedrockAuthManager.toJson(manager).toString());
		XboxCredentials credentials = XboxCredentials.builder()
				.sessionKeys(manager.getSessionKeyPair())
				.mojangJwt(chain.getMojangJwt())
				.identityJwt(chain.getIdentityJwt())
				.deviceId(manager.getDeviceId().toString().replace("-", ""))
				.build();
		return AuthenticationSession.builder()
				.username(token.getDisplayName())
				.uuid(token.getUuid())
				.accessToken(token.getToken())
				.libraryCredentials(credentials.toText())
				.build();
	}
}
