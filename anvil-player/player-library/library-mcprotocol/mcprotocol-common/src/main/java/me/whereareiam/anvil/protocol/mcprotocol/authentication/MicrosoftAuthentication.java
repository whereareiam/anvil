package me.whereareiam.anvil.protocol.mcprotocol.authentication;

import com.google.gson.JsonParser;
import me.whereareiam.anvil.api.model.player.AuthenticationAccount;
import me.whereareiam.anvil.protocol.api.library.ProtocolAuthentication;
import me.whereareiam.anvil.protocol.mcprotocol.client.McProtocolClient;
import me.whereareiam.anvil.protocol.mcprotocol.model.AuthenticationSession;
import net.raphimc.minecraftauth.MinecraftAuth;
import net.raphimc.minecraftauth.java.JavaAuthManager;
import net.raphimc.minecraftauth.java.model.MinecraftProfile;
import net.raphimc.minecraftauth.java.model.MinecraftToken;
import net.raphimc.minecraftauth.msa.model.MsaDeviceCode;
import net.raphimc.minecraftauth.msa.service.impl.DeviceCodeMsaAuthService;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * Microsoft device-code login and refresh workflow backed by private account persistence.
 */
public final class MicrosoftAuthentication implements ProtocolAuthentication {
	private final AuthenticationAccountStore accounts;

	public MicrosoftAuthentication(@NotNull Path accountsDirectory) {
		accounts = new AuthenticationAccountStore(accountsDirectory);
	}

	@Override
	public void login(@NotNull String accountId, @NotNull Consumer<String> output) {
		accounts.accountFile(accountId);
		try {
			Consumer<MsaDeviceCode> prompt = code -> output.accept("Open " + code.getDirectVerificationUri());
			JavaAuthManager manager = JavaAuthManager.create(MinecraftAuth.createHttpClient()).login(
					DeviceCodeMsaAuthService::new,
					prompt
			);
			AuthenticationSession session = refreshAndSave(accountId, manager);
			output.accept("Authenticated " + session.getUsername() + " (" + session.getUuid()
					+ ") as account '" + accountId + "'.");
		} catch (IOException exception) {
			throw new IllegalStateException("Microsoft authentication failed for account '" + accountId + "'", exception);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Microsoft authentication was interrupted for account '" + accountId + "'", exception);
		} catch (TimeoutException exception) {
			throw new IllegalStateException("Microsoft device-code authentication timed out for account '" + accountId + "'", exception);
		}
	}

	/**
	 * Refreshes a saved account for private delivery to a worker; never log the returned token.
	 */
	public @NotNull AuthenticationSession resolve(@NotNull String accountId) {
		try {
			String json = accounts.credentials(accountId);
			JavaAuthManager manager = decodeProfile(accountId, json);
			return refreshAndSave(accountId, manager);
		} catch (IOException exception) {
			throw new IllegalStateException("Could not refresh authentication account '" + accountId + "'", exception);
		}
	}

	private JavaAuthManager decodeProfile(String accountId, String json) {
		try {
			return JavaAuthManager.fromJson(MinecraftAuth.createHttpClient(), JsonParser.parseString(json).getAsJsonObject());
		} catch (RuntimeException exception) {
			// Parser exceptions may include the stored document, including refresh tokens.
			throw new IllegalStateException("Invalid stored authentication account '" + accountId + "'");
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
			return accounts.accounts(McProtocolClient.LIBRARY_ID);
		} catch (IOException exception) {
			throw new IllegalStateException("Could not list authentication accounts", exception);
		}
	}

	private AuthenticationSession refreshAndSave(String accountId, JavaAuthManager manager) throws IOException {
		MinecraftProfile profile = manager.getMinecraftProfile().getUpToDate();
		MinecraftToken token = manager.getMinecraftToken().getUpToDate();
		if (profile == null || token == null)
			throw new IllegalStateException("Microsoft account has no licensed Minecraft Java profile");
		accounts.write(accountId, McProtocolClient.LIBRARY_ID, profile.getName(), profile.getId(), JavaAuthManager.toJson(manager).toString());
		return AuthenticationSession.builder()
				.username(profile.getName())
				.uuid(profile.getId())
				.accessToken(token.getToken())
				.build();
	}
}
