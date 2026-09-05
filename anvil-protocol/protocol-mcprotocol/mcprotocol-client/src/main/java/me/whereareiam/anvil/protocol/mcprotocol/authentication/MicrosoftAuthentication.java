package me.whereareiam.anvil.protocol.mcprotocol.authentication;

import com.google.gson.JsonParser;
import me.whereareiam.anvil.protocol.api.provider.ProtocolAuthentication;
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
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * Microsoft device-code login and refresh workflow backed by private profile persistence.
 */
public final class MicrosoftAuthentication implements ProtocolAuthentication {
	private final AuthenticationProfileStore profiles;

	public MicrosoftAuthentication(@NotNull Path cacheDirectory) {
		profiles = new AuthenticationProfileStore(cacheDirectory);
	}

	@Override
	public void login(@NotNull String profile, @NotNull Consumer<String> output) {
		profiles.validateName(profile);
		try {
			Consumer<MsaDeviceCode> prompt = code -> output.accept("Open " + code.getDirectVerificationUri());
			JavaAuthManager manager = JavaAuthManager.create(MinecraftAuth.createHttpClient()).login(
					DeviceCodeMsaAuthService::new,
					prompt
			);
			AuthenticationSession session = refreshAndSave(profile, manager);
			output.accept("Authenticated " + session.getUsername() + " (" + session.getUuid()
					+ ") as profile '" + profile + "'.");
		} catch (IOException exception) {
			throw new IllegalStateException("Microsoft authentication failed for profile '" + profile + "'", exception);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Microsoft authentication was interrupted for profile '" + profile + "'", exception);
		} catch (TimeoutException exception) {
			throw new IllegalStateException("Microsoft device-code authentication timed out for profile '" + profile + "'", exception);
		}
	}

	/**
	 * Refreshes a saved profile for private delivery to a worker; never log the returned token.
	 */
	public @NotNull AuthenticationSession resolve(@NotNull String profile) {
		try {
			String json = profiles.read(profile);
			JavaAuthManager manager = decodeProfile(profile, json);
			return refreshAndSave(profile, manager);
		} catch (IOException exception) {
			throw new IllegalStateException("Could not refresh authentication profile '" + profile + "'", exception);
		}
	}

	private JavaAuthManager decodeProfile(String profile, String json) {
		try {
			return JavaAuthManager.fromJson(MinecraftAuth.createHttpClient(), JsonParser.parseString(json).getAsJsonObject());
		} catch (RuntimeException exception) {
			// Parser exceptions may include the stored document, including refresh tokens.
			throw new IllegalStateException("Invalid stored authentication profile '" + profile + "'");
		}
	}

	@Override
	public void logout(@NotNull String profile, @NotNull Consumer<String> output) {
		try {
			if (profiles.delete(profile)) {
				output.accept("Removed authentication profile '" + profile + "'.");
				return;
			}
			output.accept("Authentication profile '" + profile + "' did not exist.");
		} catch (IOException exception) {
			throw new IllegalStateException("Could not remove authentication profile '" + profile + "'", exception);
		}
	}

	private AuthenticationSession refreshAndSave(String profileName, JavaAuthManager manager) throws IOException {
		MinecraftProfile profile = manager.getMinecraftProfile().getUpToDate();
		MinecraftToken token = manager.getMinecraftToken().getUpToDate();
		if (profile == null || token == null)
			throw new IllegalStateException("Microsoft account has no licensed Minecraft Java profile");
		profiles.write(profileName, JavaAuthManager.toJson(manager).toString());
		return AuthenticationSession.builder()
				.username(profile.getName())
				.uuid(profile.getId())
				.accessToken(token.getToken())
				.build();
	}
}
