package me.whereareiam.anvil.protocol.cloudburst.identity;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * The client data a Bedrock login carries: the device the client runs on, its input and interface settings and the
 * skin it wears. Anvil's players report a desktop client with a plain classic skin, which servers accept without
 * resource packs; an Xbox login reports the device its account signed in on.
 */
@Value
@Builder
class ClientData {
	private static final int SKIN_SIDE = 64;
	private static final int PIXEL_BYTES = 4;
	private static final String SKIN_ID = "c18e65aa-7b21-4637-9b63-8ad63622ef01.Custom";
	private static final String RESOURCE_PATCH = "{\"geometry\":{\"default\":\"geometry.humanoid.custom\"}}";
	private static final String SKIN_DATA = Base64.getEncoder().encodeToString(skin());

	/**
	 * Device id the client reports.
	 */
	@NotNull String deviceId;

	/**
	 * Operating system id the client reports: 7 for Windows, 1 for Android.
	 */
	int deviceOs;

	/**
	 * Random id of this client installation.
	 */
	long clientRandomId;

	/**
	 * Name of the profile in its third-party form, the display name.
	 */
	@NotNull String thirdPartyName;

	/**
	 * Builds the claims of a client of a game version that connects to an address.
	 *
	 * @param gameVersion game version the client reports
	 * @param serverAddress address the client connects to, as {@code host:port}
	 * @return client data claims
	 */
	@NotNull JsonObject claims(@NotNull String gameVersion, @NotNull String serverAddress) {
		JsonObject claims = new JsonObject();
		claims.add("AnimatedImageData", new JsonArray());
		claims.addProperty("ArmSize", "wide");
		claims.addProperty("CapeData", "");
		claims.addProperty("CapeId", "");
		claims.addProperty("CapeImageHeight", 0);
		claims.addProperty("CapeImageWidth", 0);
		claims.addProperty("CapeOnClassicSkin", false);
		claims.addProperty("ClientRandomId", clientRandomId);
		claims.addProperty("CompatibleWithClientSideChunkGen", false);
		claims.addProperty("CurrentInputMode", 1);
		claims.addProperty("DefaultInputMode", 1);
		claims.addProperty("DeviceId", deviceId);
		claims.addProperty("DeviceModel", "Anvil");
		claims.addProperty("DeviceOS", deviceOs);
		claims.addProperty("GameVersion", gameVersion);
		claims.addProperty("GuiScale", 0);
		claims.addProperty("IsEditorMode", false);
		claims.addProperty("LanguageCode", "en_US");
		claims.addProperty("MaxViewDistance", 8);
		claims.addProperty("MemoryTier", 0);
		claims.addProperty("OverrideSkin", false);
		claims.add("PersonaPieces", new JsonArray());
		claims.addProperty("PersonaSkin", false);
		claims.add("PieceTintColors", new JsonArray());
		claims.addProperty("PlatformOfflineId", "");
		claims.addProperty("PlatformOnlineId", "");
		claims.addProperty("PlayFabId", "");
		claims.addProperty("PremiumSkin", false);
		claims.addProperty("SelfSignedId", deviceId);
		claims.addProperty("ServerAddress", serverAddress);
		claims.addProperty("SkinAnimationData", "");
		claims.addProperty("SkinColor", "#0");
		claims.addProperty("SkinData", SKIN_DATA);
		claims.addProperty("SkinGeometryData", base64(""));
		claims.addProperty("SkinGeometryDataEngineVersion", base64("0.0.0"));
		claims.addProperty("SkinId", SKIN_ID);
		claims.addProperty("SkinImageHeight", SKIN_SIDE);
		claims.addProperty("SkinImageWidth", SKIN_SIDE);
		claims.addProperty("SkinResourcePatch", base64(RESOURCE_PATCH));
		claims.addProperty("ThirdPartyName", thirdPartyName);
		claims.addProperty("ThirdPartyNameOnly", false);
		claims.addProperty("TrustedSkin", false);
		claims.addProperty("UIProfile", 0);
		return claims;
	}

	/**
	 * An opaque grey 64 by 64 skin image in RGBA.
	 */
	private static byte[] skin() {
		byte[] pixels = new byte[SKIN_SIDE * SKIN_SIDE * PIXEL_BYTES];
		for (int index = 0; index < pixels.length; index += PIXEL_BYTES) {
			pixels[index] = (byte) 0x80;
			pixels[index + 1] = (byte) 0x80;
			pixels[index + 2] = (byte) 0x80;
			pixels[index + 3] = (byte) 0xFF;
		}
		return pixels;
	}

	private static String base64(String text) {
		return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
	}
}
