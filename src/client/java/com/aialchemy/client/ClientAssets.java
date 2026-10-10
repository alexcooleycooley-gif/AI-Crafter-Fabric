package com.aialchemy.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.aialchemy.AiAlchemy;
import com.aialchemy.component.AlchemyData;
import com.aialchemy.core.icon.PixelIcon;
import com.aialchemy.core.model.CuboidModel;

import com.mojang.blaze3d.platform.NativeImage;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * The client half of the asset pipeline (design document section 12).
 *
 * <p>Icons and models arrive as small packets, are cached on disk so a second login costs no
 * traffic at all, and are converted on demand: a PNG becomes a {@link DynamicTexture} registered
 * under {@code ai_alchemy:dynamic/icon/<id>}, and the model JSON becomes a {@link CuboidModel}.</p>
 *
 * <p>Every GPU touch happens on the client thread: packets only fill the pending queues.</p>
 */
public final class ClientAssets {

	private static final ClientAssets INSTANCE = new ClientAssets();

	private final Map<String, byte[]> iconBytes = new ConcurrentHashMap<>();
	private final Map<String, CuboidModel> models = new ConcurrentHashMap<>();
	private final Map<String, Identifier> registeredIcons = new ConcurrentHashMap<>();
	private final List<String> pendingTextures = new ArrayList<>();
	private final List<String> pendingRequests = new ArrayList<>();
	private final Map<String, Long> lastRequest = new HashMap<>();

	private Path cacheDir;
	private boolean whiteTextureRegistered;

	public static ClientAssets get() {
		return INSTANCE;
	}

	private Path cacheDir() {
		if (this.cacheDir == null) {
			this.cacheDir = FabricLoader.getInstance().getGameDir().resolve("ai_alchemy_cache");

			try {
				Files.createDirectories(this.cacheDir.resolve("icons"));
				Files.createDirectories(this.cacheDir.resolve("models"));
			} catch (IOException exception) {
				AiAlchemy.LOGGER.warn("Could not create the client asset cache", exception);
			}
		}

		return this.cacheDir;
	}

	// Incoming payloads ---------------------------------------------------------

	public void receiveIcon(String iconId, byte[] png) {
		this.iconBytes.put(iconId, png);

		try {
			Files.write(cacheDir().resolve("icons").resolve(iconId + ".png"), png);
		} catch (IOException exception) {
			AiAlchemy.LOGGER.debug("Could not cache icon {}: {}", iconId, exception.getMessage());
		}

		synchronized (this.pendingTextures) {
			if (!this.pendingTextures.contains(iconId)) {
				this.pendingTextures.add(iconId);
			}
		}
	}

	public void receiveModel(String modelId, String json) {
		try {
			JsonObject root = JsonParser.parseString(json).getAsJsonObject();
			CuboidModel model = CuboidModel.fromJson(root);
			this.models.put(modelId, model);
			Files.writeString(cacheDir().resolve("models").resolve(modelId + ".json"), json);
		} catch (IOException | RuntimeException exception) {
			AiAlchemy.LOGGER.warn("Rejected model payload {}: {}", modelId, exception.getMessage());
		}
	}

	public void loadDiskCache() {
		Path icons = cacheDir().resolve("icons");

		if (Files.isDirectory(icons)) {
			try (var stream = Files.list(icons)) {
				stream.filter(path -> path.toString().endsWith(".png")).forEach(path -> {
					String id = path.getFileName().toString().replace(".png", "");

					try {
						receiveIcon(id, Files.readAllBytes(path));
					} catch (IOException ignored) {
						// skip unreadable cache entries
					}
				});
			} catch (IOException exception) {
				AiAlchemy.LOGGER.debug("Could not read the icon cache", exception);
			}
		}

		Path modelDir = cacheDir().resolve("models");

		if (Files.isDirectory(modelDir)) {
			try (var stream = Files.list(modelDir)) {
				stream.filter(path -> path.toString().endsWith(".json")).forEach(path -> {
					try {
						receiveModel(path.getFileName().toString().replace(".json", ""), Files.readString(path));
					} catch (IOException ignored) {
						// skip unreadable cache entries
					}
				});
			} catch (IOException exception) {
				AiAlchemy.LOGGER.debug("Could not read the model cache", exception);
			}
		}
	}

	// Lookups ------------------------------------------------------------------

	public Identifier iconTexture(String iconId) {
		Identifier existing = this.registeredIcons.get(iconId);

		if (existing != null) {
			return existing;
		}

		// Not registered yet: queue it and hand back the id, which will resolve on the next frame.
		synchronized (this.pendingTextures) {
			if (!this.pendingTextures.contains(iconId)) {
				this.pendingTextures.add(iconId);
			}
		}

		Identifier identifier = AlchemyData.iconTexture(iconId);
		this.registeredIcons.put(iconId, identifier);
		return identifier;
	}

	public CuboidModel model(String modelId) {
		return this.models.get(modelId);
	}

	public boolean hasModel(String modelId) {
		return this.models.containsKey(modelId);
	}

	public boolean hasIcon(String iconId) {
		return this.registeredIcons.containsKey(iconId) && this.iconBytes.containsKey(iconId);
	}

	/** The shared one pixel white texture that all coloured model geometry samples. */
	public Identifier whiteTexture() {
		return AlchemyData.whiteTexture();
	}

	// Client thread work -------------------------------------------------------

	/** Flushes pending texture uploads. Called from the client tick, on the client thread. */
	public void flush() {
		Minecraft client = Minecraft.getInstance();

		if (client.getTextureManager() == null) {
			return;
		}

		if (!this.whiteTextureRegistered) {
			registerWhite(client);
		}

		List<String> toRegister;

		synchronized (this.pendingTextures) {
			toRegister = new ArrayList<>(this.pendingTextures);
			this.pendingTextures.clear();
		}

		for (String iconId : toRegister) {
			byte[] png = this.iconBytes.get(iconId);

			if (png == null) {
				continue;
			}

			try {
				NativeImage image = NativeImage.read(png);
				DynamicTexture texture = new DynamicTexture(() -> "ai_alchemy icon " + iconId, image);
				client.getTextureManager().register(AlchemyData.iconTexture(iconId), texture);
				this.registeredIcons.put(iconId, AlchemyData.iconTexture(iconId));
			} catch (IOException | RuntimeException exception) {
				AiAlchemy.LOGGER.warn("Could not upload icon {}: {}", iconId, exception.getMessage());
				// Ask the server for it again later instead of hammering it every tick.
				if (this.lastRequest.getOrDefault(iconId, 0L) + 5_000L < System.currentTimeMillis()) {
					this.lastRequest.put(iconId, System.currentTimeMillis());
					this.pendingRequests.add(iconId);
				}
			}
		}
	}

	private void registerWhite(Minecraft client) {
		try {
			NativeImage image = new NativeImage(NativeImage.Format.RGBA, 1, 1, false);
			image.setPixelABGR(0, 0, 0xFFFFFFFF);
			client.getTextureManager().register(AlchemyData.whiteTexture(), new DynamicTexture(() -> "ai_alchemy white", image));
			this.whiteTextureRegistered = true;
		} catch (RuntimeException exception) {
			AiAlchemy.LOGGER.warn("Could not register the shared white texture", exception);
		}
	}

	/** Icons the client failed to decode, to be requested from the server on the next flush. */
	public List<String> drainAssetRequests() {
		synchronized (this.pendingRequests) {
			List<String> copy = new ArrayList<>(this.pendingRequests);
			this.pendingRequests.clear();
			return copy;
		}
	}

	/** Convenience for the renderer: an icon that is known to be uploaded. */
	public boolean isTextureReady(String iconId) {
		return this.registeredIcons.containsKey(iconId) && this.iconBytes.containsKey(iconId);
	}

	public int cachedIconCount() {
		return this.iconBytes.size();
	}

	public int cachedModelCount() {
		return this.models.size();
	}

	/** Used by tests: a blank 16x16 icon for items whose payload never arrives. */
	public static PixelIcon placeholderIcon() {
		String[] rows = new String[16];

		for (int index = 0; index < 16; index++) {
			rows[index] = "0000000000000000";
		}

		return new PixelIcon(rows);
	}
}