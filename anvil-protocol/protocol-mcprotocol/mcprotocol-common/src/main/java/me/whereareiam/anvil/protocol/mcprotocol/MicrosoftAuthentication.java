package me.whereareiam.anvil.protocol.mcprotocol;

import com.google.gson.JsonParser;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.protocol.api.worker.WorkerAuthentication;
import me.whereareiam.anvil.protocol.api.worker.model.AccountRecord;
import me.whereareiam.anvil.protocol.api.worker.model.AuthenticationSession;
import me.whereareiam.anvil.protocol.api.worker.model.ResolvedAccount;
import net.raphimc.minecraftauth.MinecraftAuth;
import net.raphimc.minecraftauth.java.JavaAuthManager;
import net.raphimc.minecraftauth.java.model.MinecraftProfile;
import net.raphimc.minecraftauth.java.model.MinecraftToken;
import net.raphimc.minecraftauth.msa.model.MsaDeviceCode;
import net.raphimc.minecraftauth.msa.service.impl.DeviceCodeMsaAuthService;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * Microsoft device-code login of Java Edition accounts, and their refresh. The stored credentials are the
 * serialized {@link JavaAuthManager}; the native worker stores them.
 */
public final class MicrosoftAuthentication implements WorkerAuthentication {
	@Override
	public @NotNull AccountRecord login(@NotNull String accountId, @NotNull MinecraftVersion version, @NotNull Consumer<String> output) {
		try {
			Consumer<MsaDeviceCode> prompt = code -> output.accept("Open " + code.getDirectVerificationUri());
			JavaAuthManager manager = JavaAuthManager.create(MinecraftAuth.createHttpClient()).login(
					DeviceCodeMsaAuthService::new,
					prompt
			);
			return refresh(accountId, manager).getAccount();
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
	 * Refreshes a stored account for private delivery to a worker; Java Edition sessions do not depend on the game
	 * version. Never log the returned token.
	 */
	@Override
	public @NotNull ResolvedAccount resolve(@NotNull AccountRecord account, @NotNull MinecraftVersion version) {
		try {
			return refresh(account.getAccountId(), decode(account));
		} catch (IOException exception) {
			throw new IllegalStateException("Could not refresh authentication account '" + account.getAccountId() + "'", exception);
		}
	}

	private JavaAuthManager decode(AccountRecord account) {
		try {
			return JavaAuthManager.fromJson(MinecraftAuth.createHttpClient(), JsonParser.parseString(account.getCredentials()).getAsJsonObject());
		} catch (RuntimeException exception) {
			// Parser exceptions may include the stored document, including refresh tokens.
			throw new IllegalStateException("Invalid stored authentication account '" + account.getAccountId() + "'");
		}
	}

	private ResolvedAccount refresh(String accountId, JavaAuthManager manager) throws IOException {
		MinecraftProfile profile = manager.getMinecraftProfile().getUpToDate();
		MinecraftToken token = manager.getMinecraftToken().getUpToDate();
		if (profile == null || token == null)
			throw new IllegalStateException("Microsoft account has no licensed Minecraft Java profile");

		return ResolvedAccount.builder()
				.account(AccountRecord.builder()
						.accountId(accountId)
						.username(profile.getName())
						.uniqueId(profile.getId())
						.credentials(JavaAuthManager.toJson(manager).toString())
						.build())
				.session(AuthenticationSession.builder()
						.username(profile.getName())
						.uuid(profile.getId())
						.accessToken(token.getToken())
						.build())
				.build();
	}
}
