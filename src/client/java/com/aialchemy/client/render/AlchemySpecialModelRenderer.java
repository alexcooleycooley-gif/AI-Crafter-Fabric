package com.aialchemy.client.render;

import java.util.function.Consumer;

import com.aialchemy.component.AlchemyData;
import com.aialchemy.core.model.IdleEffect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import org.joml.Vector3fc;

/**
 * Draws a generated item (design document section 6.2, the {@code DynamicItemRenderer} slot).
 *
 * <p>Two paths, chosen per display context:</p>
 * <ul>
 *   <li><b>gui</b> - the 16x16 icon is drawn as a flat, double sided quad, so the inventory looks
 *       crisp and no texture atlas is involved;</li>
 *   <li><b>everything else</b> - the cuboids are drawn from generated vertex data, coloured from the
 *       item's palette and sampling a shared one pixel white texture, which is why no texture has to
 *       be produced for a model at all.</li>
 * </ul>
 *
 * <p>Keeping the 3D path inside vanilla's submit pipeline means lighting, item frames, translucent
 * shells and the enchantment glint keep working for free.</p>
 */
public final class AlchemySpecialModelRenderer implements SpecialModelRenderer<RenderArgument> {

	public static final AlchemySpecialModelRenderer INSTANCE = new AlchemySpecialModelRenderer();

	// Vanilla style directional shading.
	private static final float SHADE_UP = 1.0f;
	private static final float SHADE_DOWN = 0.5f;
	private static final float SHADE_NORTH = 0.8f;
	private static final float SHADE_SOUTH = 0.8f;
	private static final float SHADE_WEST = 0.6f;
	private static final float SHADE_EAST = 0.6f;

	private AlchemySpecialModelRenderer() {
	}

	@Override
	public void submit(RenderArgument argument, ItemDisplayContext context, PoseStack pose, SubmitNodeCollector collector,
			int light, int overlay, boolean foil, int seed) {
		if (argument == null) {
			return;
		}

		if (context == ItemDisplayContext.GUI || argument.parts().isEmpty()) {
			Identifier texture = AlchemyData.iconTexture(argument.iconId());
			collector.submitCustomGeometry(pose, RenderTypes.entityCutoutNoCull(texture),
					(entry, consumer) -> drawFlatIcon(entry, consumer, light, overlay));
			return;
		}

		Identifier white = AlchemyData.whiteTexture();
		Draw draw = new Draw(white, argument, animationAngle(argument));
		collector.submitCustomGeometry(pose, RenderTypes.entityCutoutNoCull(white),
				(entry, consumer) -> drawCuboids(entry, consumer, light, overlay, draw));
	}

	@Override
	public void getExtents(Consumer<Vector3fc> consumer) {
		// Extents are supplied by the item model through the render state layer.
	}

	@Override
	public RenderArgument extractArgument(ItemStack stack) {
		// The argument is built by AlchemyItemModel, which is the only place that has both the stack
		// and the display context.
		return null;
	}

	// Icon -----------------------------------------------------------------

	private static void drawFlatIcon(PoseStack.Pose pose, VertexConsumer consumer, int light, int overlay) {
		iconQuad(pose, consumer, 8.5f, false, light, overlay);
		iconQuad(pose, consumer, 7.5f, true, light, overlay);
	}

	private static void iconQuad(PoseStack.Pose pose, VertexConsumer consumer, float z, boolean back, int light,
			int overlay) {
		int rgb = 0xFFFFFF;
		int alpha = 0xFF;
		float u0 = back ? 1f : 0f;
		float u1 = back ? 0f : 1f;
		float normal = back ? -1f : 1f;

			vertex(pose, consumer, 0f, 0f, z, u0, 0f, rgb, alpha, light, overlay, 0f, 0f, normal);
			vertex(pose, consumer, 16f, 0f, z, u1, 0f, rgb, alpha, light, overlay, 0f, 0f, normal);
			vertex(pose, consumer, 16f, 16f, z, u1, 1f, rgb, alpha, light, overlay, 0f, 0f, normal);
			vertex(pose, consumer, 0f, 16f, z, u0, 1f, rgb, alpha, light, overlay, 0f, 0f, normal);
	}

	// Cuboids --------------------------------------------------------------

		private static void drawCuboids(PoseStack.Pose pose, VertexConsumer consumer, int light, int overlay, Draw draw) {
			for (RenderArgument.PartGeometry part : draw.argument.parts()) {
				int partLight = part.emissive() ? LightTexture.FULL_BRIGHT : light;
				drawBox(pose, consumer, part, partLight, overlay, draw);
			}
		}

		private static void drawBox(PoseStack.Pose pose, VertexConsumer consumer, RenderArgument.PartGeometry part,
				int light, int overlay, Draw draw) {
		float minX = part.minX();
		float minY = part.minY();
		float minZ = part.minZ();
		float maxX = part.maxX();
		float maxY = part.maxY();
		float maxZ = part.maxZ();
		int rgb = part.rgb();
		int alpha = Math.max(4, part.alpha());

		// Down, Up, North, South, West, East, matching vanilla's winding and shading.
		face(pose, consumer, draw, minX, minY, maxZ, maxX, minY, maxZ, maxX, minY, minZ, minX, minY, minZ,
					rgb, alpha, SHADE_DOWN, 0f, -1f, 0f, light, overlay);
		face(pose, consumer, draw, minX, maxY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, minX, maxY, maxZ,
					rgb, alpha, SHADE_UP, 0f, 1f, 0f, light, overlay);
		face(pose, consumer, draw, maxX, minY, minZ, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ,
					rgb, alpha, SHADE_NORTH, 0f, 0f, -1f, light, overlay);
		face(pose, consumer, draw, minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ,
					rgb, alpha, SHADE_SOUTH, 0f, 0f, 1f, light, overlay);
		face(pose, consumer, draw, minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ,
					rgb, alpha, SHADE_WEST, -1f, 0f, 0f, light, overlay);
		face(pose, consumer, draw, maxX, minY, maxZ, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ,
					rgb, alpha, SHADE_EAST, 1f, 0f, 0f, light, overlay);
	}

	private static void face(PoseStack.Pose pose, VertexConsumer consumer, Draw draw,
			float x0, float y0, float z0,
			float x1, float y1, float z1,
			float x2, float y2, float z2,
			float x3, float y3, float z3,
				int rgb, int alpha, float shade, float nx, float ny, float nz, int light, int overlay) {
		int color = shade(rgb, shade);
		// A small UV square inside the shared white texture: the colour comes from the vertex.
			quadVertex(pose, consumer, draw, x0, y0, z0, 0.25f, 0.25f, color, alpha, light, overlay, nx, ny, nz);
			quadVertex(pose, consumer, draw, x1, y1, z1, 0.75f, 0.25f, color, alpha, light, overlay, nx, ny, nz);
			quadVertex(pose, consumer, draw, x2, y2, z2, 0.75f, 0.75f, color, alpha, light, overlay, nx, ny, nz);
			quadVertex(pose, consumer, draw, x3, y3, z3, 0.25f, 0.75f, color, alpha, light, overlay, nx, ny, nz);
	}

	private static void quadVertex(PoseStack.Pose pose, VertexConsumer consumer, Draw draw,
			float x, float y, float z, float u, float v, int rgb, int alpha, int light, int overlay,
			float nx, float ny, float nz) {
			float[] position = draw.position(x, y, z);
			float[] normal = draw.normal(nx, ny, nz);
			vertex(pose, consumer, position[0], position[1], position[2], u, v, rgb, alpha, light,
					overlay, normal[0], normal[1], normal[2]);
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer consumer, float x, float y, float z,
			float u, float v, int rgb, int alpha, int light, int overlay, float nx, float ny, float nz) {
		consumer.addVertex(pose, x, y, z)
					.setColor((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, alpha)
					.setUv(u, v)
					.setOverlay(overlay)
					.setLight(light)
				.setNormal(pose, nx, ny, nz);
	}

	private static int shade(int rgb, float factor) {
		int red = Math.min(255, Math.round(((rgb >> 16) & 0xFF) * factor));
		int green = Math.min(255, Math.round(((rgb >> 8) & 0xFF) * factor));
		int blue = Math.min(255, Math.round((rgb & 0xFF) * factor));
		return (red << 16) | (green << 8) | blue;
	}

	/** Drives {@code spin}, {@code bob} and {@code pulse} without touching the stored model. */
	private static float animationAngle(RenderArgument argument) {
		if (argument.idleEffect() == IdleEffect.NONE) {
			return 0f;
		}

		long now = System.currentTimeMillis();
		return switch (argument.idleEffect()) {
			case SPIN -> (now % 3_000L) / 3_000f * 360f;
			case BOB -> (float) Math.sin(now / 400.0D) * 0.8f;
			case PULSE -> (float) Math.sin(now / 500.0D) * 0.03f;
			case NONE -> 0f;
		};
	}

	/** Per frame transform state shared by every vertex of one submit call. */
	private static final class Draw {

		private final RenderArgument argument;
		private final float animation;
		private final float centreX = 8f;
		private final float centreY = 8f;
		private final float centreZ = 8f;

		private Draw(Identifier texture, RenderArgument argument, float animation) {
			this.argument = argument;
			this.animation = animation;
		}

		private float[] position(float x, float y, float z) {
			float px = x;
			float py = y;
			float pz = z;

			switch (this.argument.idleEffect()) {
				case PULSE -> {
					float factor = 1f + this.animation;
					px = this.centreX + (px - this.centreX) * factor;
					py = this.centreY + (py - this.centreY) * factor;
					pz = this.centreZ + (pz - this.centreZ) * factor;
				}
				case BOB -> py += this.animation;
				case SPIN -> {
					double radians = Math.toRadians(this.animation);
					float cos = (float) Math.cos(radians);
					float sin = (float) Math.sin(radians);
					float dx = px - this.centreX;
					float dz = pz - this.centreZ;
					px = this.centreX + dx * cos + dz * sin;
					pz = this.centreZ - dx * sin + dz * cos;
				}
				default -> {
				}
			}

			return new float[] {px, py, pz};
		}

		private float[] normal(float nx, float ny, float nz) {
			if (this.argument.idleEffect() != IdleEffect.SPIN) {
				return new float[] {nx, ny, nz};
			}

			double radians = Math.toRadians(this.animation);
			float cos = (float) Math.cos(radians);
			float sin = (float) Math.sin(radians);
			return new float[] {nx * cos + nz * sin, ny, -nx * sin + nz * cos};
		}
	}
}
