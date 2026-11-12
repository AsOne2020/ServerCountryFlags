package me.khajiitos.servercountryflags.common;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import me.khajiitos.servercountryflags.common.config.Config;
import me.khajiitos.servercountryflags.common.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.resolver.ResolvedServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddressResolver;
import net.minecraft.client.multiplayer.resolver.ServerRedirectHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class ServerCountryFlags {
	public static final String MOD_ID = "servercountryflags";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final String API_NAME = "http://ip-api.com/json/";
	public static final int API_FIELDS = 541395;
	private static volatile String apiLanguage = null;
	public static ServerList serverList; // Servers from the server list

	public static final Map<String, APIResponse> servers = new HashMap<>();
	private static final Set<String> pendingServers = new HashSet<>();
	private static volatile Map<String, Float> flagAspectRatios = Map.of();
	private static final Set<String> unknownCountryCodes = new HashSet<>();
	private static final ServerRedirectHandler redirectResolver = ServerRedirectHandler.createDnsSrvRedirectHandler();

	public static volatile LocationInfo localLocation;
	private static int lookupGeneration;
	private static boolean localLookupPending;

	public static void init() {
		Config.init();
		NetworkChangeDetector.check();
		updateLocalLocationInfo();
	}

	public static void loadFlags(ResourceManager manager) {
		Map<String, Float> ratios = new HashMap<>();
		Map<Identifier, Resource> flags = manager.listResources("textures/gui/flags",
				id -> id.getNamespace().equals(MOD_ID) && id.getPath().endsWith(".png"));
		for (Map.Entry<Identifier, Resource> entry : flags.entrySet()) {
			String path = entry.getKey().getPath();
			try (InputStream stream = entry.getValue().open(); NativeImage image = NativeImage.read(stream)) {
				ratios.put(path.substring(path.lastIndexOf('/') + 1, path.length() - 4),
						(float) image.getWidth() / image.getHeight());
			} catch (IOException e) {
				LOGGER.warn("Failed to read flag {}", entry.getKey(), e);
			}
		}
		flagAspectRatios = Map.copyOf(ratios);
	}

	public static void clearLocations() {
		lookupGeneration++;
		servers.clear();
		pendingServers.clear();
		localLookupPending = false;
		localLocation = null;
	}

	public static void updateAPILanguage(String language) {
		final String oldApiLanguage = apiLanguage;

		if (Config.cfg.forceEnglish) {
			apiLanguage = null;
		} else if (language != null) {
			if (language.startsWith("en")) apiLanguage = null;
			else if (language.startsWith("de")) apiLanguage = "de";
			else if (language.startsWith("es")) apiLanguage = "es";
			else if (language.startsWith("pt")) apiLanguage = "pt-BR";
			else if (language.startsWith("fr")) apiLanguage = "fr";
			else if (language.startsWith("ja")) apiLanguage = "ja";
			else if (language.startsWith("zh")) apiLanguage = "zh-CN";
			else if (language.startsWith("ru")) apiLanguage = "ru";
			else apiLanguage = null;
		}

		if (!Objects.equals(apiLanguage, oldApiLanguage)) {
			clearLocations();
		}
	}

	private static @NotNull APIResponse getAPIResponse(String ip) {
		if (!APITimeoutManager.tryStartRequest()) {
			return new APIResponse(APIResponse.Status.COOLDOWN, null);
		}

		String language = apiLanguage;
		String apiUrl = API_NAME + ip + "?fields=" + API_FIELDS + (language == null ? "" : "&lang=" + language);
		HttpURLConnection connection = null;
		int requestsLeft = -1;
		int secondsLeft = -1;
		try {
			connection = (HttpURLConnection) URI.create(apiUrl).toURL().openConnection();
			connection.setConnectTimeout(3000);
			connection.setReadTimeout(3000);
			int status = connection.getResponseCode();
			requestsLeft = connection.getHeaderFieldInt("X-Rl", -1);
			secondsLeft = connection.getHeaderFieldInt("X-Ttl", -1);
			if (status == 429) {
				requestsLeft = 0;
				return new APIResponse(APIResponse.Status.COOLDOWN, null);
			}

			try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
				JsonElement response = JsonParser.parseReader(reader);
				return new APIResponse(APIResponse.Status.SUCCESS, new LocationInfo(response));
			}
		} catch (IOException | InvalidAPIResponseException | JsonParseException | IllegalStateException | IllegalArgumentException | UnsupportedOperationException e) {
			LOGGER.debug("Location lookup failed for {}", ip, e);
			return new APIResponse(APIResponse.Status.UNKNOWN, null);
		} finally {
			APITimeoutManager.finishRequest(requestsLeft, secondsLeft);
			if (connection != null) {
				connection.disconnect();
			}
		}
	}

	public static boolean isIpLocal(InetAddress address) {
		return address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress() || address.isAnyLocalAddress();
	}

	public static void requestServerLocation(String serverAddress) {
		APIResponse current = servers.get(serverAddress);
		if ((current != null && current.status() == APIResponse.Status.SUCCESS) || !pendingServers.add(serverAddress)) {
			return;
		}

		final String cleanServerAddress;
		// Prefixes used by the Raknetify mod
		if (serverAddress.startsWith("raknet;")) {
			cleanServerAddress = serverAddress.substring("raknet;".length());
		} else if (serverAddress.startsWith("raknetl;")) {
			cleanServerAddress = serverAddress.substring("raknetl;".length());
		} else {
			cleanServerAddress = serverAddress;
		}

		int generation = lookupGeneration;
		boolean resolveRedirects = Config.cfg.resolveRedirects;
		CompletableFuture.supplyAsync(() -> {
			ServerAddress address = ServerAddress.parseString(cleanServerAddress);
			if (resolveRedirects) {
				address = redirectResolver.lookupRedirect(address).orElse(address);
			}

			Optional<ResolvedServerAddress> resolvedAddress = ServerAddressResolver.SYSTEM.resolve(address);
			if (resolvedAddress.isEmpty()) {
				return new APIResponse(APIResponse.Status.UNKNOWN, null);
			}
			InetAddress ip = resolvedAddress.get().asInetSocketAddress().getAddress();
			return getAPIResponse(isIpLocal(ip) ? "" : ip.getHostAddress());
		}).whenComplete((response, error) -> Minecraft.getInstance().execute(() -> {
			if (generation != lookupGeneration) {
				return;
			}
			pendingServers.remove(serverAddress);
			if (error != null) {
				LOGGER.debug("Failed to resolve server {}", serverAddress, error);
			} else {
				APIResponse oldResponse = servers.get(serverAddress);
				if (response.locationInfo() != null) {
					response.locationInfo().updateDistanceFromLocal();
					servers.put(serverAddress, response);
				} else if (oldResponse == null || oldResponse.locationInfo() == null) {
					servers.put(serverAddress, response);
				}
			}
		}));
	}

	public static void updateLocalLocationInfo() {
		if (localLookupPending) {
			return;
		}
		localLookupPending = true;
		int generation = lookupGeneration;
		CompletableFuture.supplyAsync(() -> getAPIResponse("")).whenComplete((response, error) ->
				Minecraft.getInstance().execute(() -> {
					if (generation != lookupGeneration) {
						return;
					}
					localLookupPending = false;
					if (error != null) {
						LOGGER.debug("Failed to look up local location", error);
					} else if (response.locationInfo() != null) {
						localLocation = response.locationInfo();
						for (APIResponse serverResponse : servers.values()) {
							if (serverResponse.locationInfo() != null) {
								serverResponse.locationInfo().updateDistanceFromLocal();
							}
						}
					}
				}));
	}

	public static FlagRenderInfo getFlagRenderInfo(APIResponse apiResponse) {
		String countryCode;
		double aspectRatio;
		List<FormattedCharSequence> tooltip = new ArrayList<>();

		if (apiResponse == null || apiResponse.unknown()) {
			if (!Config.cfg.displayUnknownFlag) {
				return null;
			}
			tooltip.add(Component.translatable("servercountryflags.locationInfo.unknown").getVisualOrderText());
			countryCode = "unknown";
			aspectRatio = 1.5;
		} else if (apiResponse.cooldown()) {
			if (!Config.cfg.displayCooldownFlag) {
				return null;
			}
			tooltip.add(Component.translatable("servercountryflags.locationInfo.cooldown").getVisualOrderText());
			countryCode = "timeout";
			aspectRatio = 1.5;
		} else {
			LocationInfo locationInfo = apiResponse.locationInfo();
			Float ratio = flagAspectRatios.get(locationInfo.countryCode);
			if (ratio != null) {
				countryCode = locationInfo.countryCode;
				aspectRatio = ratio;
			} else {
				if (!ServerCountryFlags.unknownCountryCodes.contains(locationInfo.countryCode)) {
					ServerCountryFlags.LOGGER.error("Unknown country code: " + locationInfo.countryCode);
					ServerCountryFlags.unknownCountryCodes.add(locationInfo.countryCode);
				}

				if (Config.cfg.displayUnknownFlag) {
					countryCode = "unknown";
					aspectRatio = 1.5;
				} else {
					return null;
				}
			}

			tooltip.add(Component.literal((Config.cfg.showDistrict && !locationInfo.districtName.isEmpty() ? (locationInfo.districtName + ", ") : "") + locationInfo.cityName + ", " + locationInfo.countryName).getVisualOrderText());

			if (Config.cfg.showISP && !locationInfo.ispName.isEmpty()) {
				tooltip.add(Component.translatable("servercountryflags.locationInfo.isp", locationInfo.ispName).getVisualOrderText());
			}

			if (Config.cfg.showDistance) {
				double distanceFromLocal = locationInfo.getDistanceFromLocal(Config.cfg.useKm);
				if (distanceFromLocal != -1.0) {
					tooltip.add(Component.translatable("servercountryflags.locationInfo.distance", (int)distanceFromLocal, Component.translatable(Config.cfg.useKm ? "servercountryflags.locationInfo.km" : "servercountryflags.locationInfo.mi")).withStyle(ChatFormatting.ITALIC).getVisualOrderText());
				}
			}
		}

		return new FlagRenderInfo(countryCode, aspectRatio, tooltip);
	}
}
