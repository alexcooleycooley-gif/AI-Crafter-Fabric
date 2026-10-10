package com.aialchemy.client.render;

import java.util.Set;

import com.aialchemy.AiAlchemy;
import com.aialchemy.core.Shell;

import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.renderer.item.ItemModel;

/**
 * Installs {@link AlchemyItemModel} over the shell items (design document section 6.2).
 *
 * <p>Fabric's model loading API hands us the baked model for each item id; for the eight shell items
 * we wrap it, so vanilla keeps owning the JSON (and the particle icon and the fallback texture)
 * while the actual drawing comes from generated data.</p>
 */
public final class AlchemyModelPlugin implements ModelLoadingPlugin {

	private static final Set<String> SHELL_PATHS = shellPaths();

	private AlchemyModelPlugin() {
	}

	public static void register() {
		ModelLoadingPlugin.register(new AlchemyModelPlugin());
		AiAlchemy.LOGGER.info("AI Alchemy registered its dynamic item model for {} shells", SHELL_PATHS.size());
	}

	@Override
	public void initialize(Context context) {
		context.modifyItemModelAfterBake().register((model, bakeContext) -> {
			String path = bakeContext.itemId().getPath();

			if (!SHELL_PATHS.contains(path)) {
				return model;
			}

			return new AlchemyItemModel(model);
		});
	}

	private static Set<String> shellPaths() {
		Set<String> paths = new java.util.HashSet<>();

		for (Shell shell : Shell.values()) {
			paths.add(shell.itemPath());
		}

		return Set.copyOf(paths);
	}

	/** Used by tests and by the client entrypoint when reporting status. */
	public static boolean isShellItem(net.minecraft.resources.Identifier itemId) {
		return SHELL_PATHS.contains(itemId.getPath());
	}

	static ItemModel wrapForTest(ItemModel model, String path) {
		return SHELL_PATHS.contains(path) ? new AlchemyItemModel(model) : model;
	}
}