package com.aialchemy.core.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * A generated 3D model: a small assembly of cuboids plus the metadata the client renderer needs.
 * This record is the single source of truth for what is validated, stored and sent over the wire.
 */
public record CuboidModel(
		float gripX,
		float gripY,
		float gripZ,
		List<ModelPart> parts,
		Map<String, Float> display,
		IdleEffect idleEffect) {

	public static final String DISPLAY_HAND = "hand";
	public static final String DISPLAY_GROUND = "ground";
	public static final String DISPLAY_FRAME = "frame";

	public CuboidModel {
		parts = List.copyOf(parts);
		display = Map.copyOf(display);
		if (idleEffect == null) {
			idleEffect = IdleEffect.NONE;
		}
	}

	public int quadCount() {
		return this.parts.size() * 6;
	}

	/** Total number of parts flagged emissive. */
	public int emissiveParts() {
		int count = 0;

		for (ModelPart part : this.parts) {
			if (part.emissive()) {
				count++;
			}
		}

		return count;
	}

	public float emissiveShare() {
		return this.parts.isEmpty() ? 0f : (float) emissiveParts() / this.parts.size();
	}

	public Bounds bounds() {
		if (this.parts.isEmpty()) {
			return new Bounds(0f, 0f, 0f, 0f, 0f, 0f);
		}

		float minX = Float.MAX_VALUE;
		float minY = Float.MAX_VALUE;
		float minZ = Float.MAX_VALUE;
		float maxX = -Float.MAX_VALUE;
		float maxY = -Float.MAX_VALUE;
		float maxZ = -Float.MAX_VALUE;

		for (ModelPart part : this.parts) {
			minX = Math.min(minX, part.fromX());
			minY = Math.min(minY, part.fromY());
			minZ = Math.min(minZ, part.fromZ());
			maxX = Math.max(maxX, part.toX());
			maxY = Math.max(maxY, part.toY());
			maxZ = Math.max(maxZ, part.toZ());
		}

		return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
	}

	public float displayScale(String context, float fallback) {
		Float value = this.display.get(context);
		return value == null ? fallback : value;
	}

	/** Deterministic serialization: only vetted JSON is ever stored or sent. */
	public JsonObject toJson() {
		JsonObject root = new JsonObject();

		JsonArray grip = new JsonArray();
		grip.add(round(this.gripX));
		grip.add(round(this.gripY));
		grip.add(round(this.gripZ));
		root.add("grip", grip);

		JsonArray jsonParts = new JsonArray();

		for (ModelPart part : this.parts) {
			JsonObject jsonPart = new JsonObject();
			jsonPart.add("from", array(part.fromX(), part.fromY(), part.fromZ()));
			jsonPart.add("to", array(part.toX(), part.toY(), part.toZ()));
			jsonPart.addProperty("color", part.color());

			if (part.pattern() != Pattern.SOLID) {
				jsonPart.addProperty("pattern", part.pattern().jsonName());
			}

			if (part.emissive()) {
				jsonPart.addProperty("emissive", true);
			}

			if (part.rotation() != null) {
				JsonObject rotation = new JsonObject();
				rotation.addProperty("axis", String.valueOf(part.rotation().axis()));
				rotation.addProperty("angle", round(part.rotation().angle()));
				rotation.add("origin", array(part.rotation().originX(), part.rotation().originY(), part.rotation().originZ()));
				jsonPart.add("rotation", rotation);
			}

			jsonParts.add(jsonPart);
		}

		root.add("parts", jsonParts);

		if (!this.display.isEmpty()) {
			JsonObject display = new JsonObject();
			this.display.forEach((key, value) -> display.addProperty(key, round(value)));
			root.add("display", display);
		}

		if (this.idleEffect != IdleEffect.NONE) {
			root.addProperty("idle_effect", this.idleEffect.jsonName());
		}

		return root;
	}

	public static CuboidModel fromJson(JsonObject root) {
		JsonArray grip = root.getAsJsonArray("grip");
		float gripX = 8f;
		float gripY = 0f;
		float gripZ = 8f;

		if (grip != null && grip.size() >= 3) {
			gripX = grip.get(0).getAsFloat();
			gripY = grip.get(1).getAsFloat();
			gripZ = grip.get(2).getAsFloat();
		}

		List<ModelPart> parts = new ArrayList<>();
		JsonArray jsonParts = root.getAsJsonArray("parts");

		if (jsonParts != null) {
			for (JsonElement element : jsonParts) {
				JsonObject jsonPart = element.getAsJsonObject();
				JsonArray from = jsonPart.getAsJsonArray("from");
				JsonArray to = jsonPart.getAsJsonArray("to");
				PartRotation rotation = null;

				if (jsonPart.has("rotation") && jsonPart.get("rotation").isJsonObject()) {
					JsonObject jsonRotation = jsonPart.getAsJsonObject("rotation");
					JsonArray origin = jsonRotation.getAsJsonArray("origin");
					rotation = PartRotation.of(
							jsonRotation.has("axis") ? jsonRotation.get("axis").getAsString() : "y",
							jsonRotation.has("angle") ? jsonRotation.get("angle").getAsFloat() : 0f,
							origin != null && origin.size() >= 3 ? origin.get(0).getAsFloat() : 8f,
							origin != null && origin.size() >= 3 ? origin.get(1).getAsFloat() : 8f,
							origin != null && origin.size() >= 3 ? origin.get(2).getAsFloat() : 8f);
				}

				parts.add(new ModelPart(
						from.get(0).getAsFloat(), from.get(1).getAsFloat(), from.get(2).getAsFloat(),
						to.get(0).getAsFloat(), to.get(1).getAsFloat(), to.get(2).getAsFloat(),
						jsonPart.has("color") ? jsonPart.get("color").getAsInt() : 1,
						Pattern.byJsonName(jsonPart.has("pattern") ? jsonPart.get("pattern").getAsString() : null),
						jsonPart.has("emissive") && jsonPart.get("emissive").getAsBoolean(),
						rotation));
			}
		}

		Map<String, Float> display = new LinkedHashMap<>();

		if (root.has("display") && root.get("display").isJsonObject()) {
			root.getAsJsonObject("display").entrySet().forEach(entry -> {
				if (entry.getValue().isJsonPrimitive()) {
					display.put(entry.getKey(), entry.getValue().getAsFloat());
				}
			});
		}

		return new CuboidModel(gripX, gripY, gripZ, parts, display,
				IdleEffect.byJsonName(root.has("idle_effect") ? root.get("idle_effect").getAsString() : null));
	}

	private static JsonArray array(float... values) {
		JsonArray array = new JsonArray();

		for (float value : values) {
			array.add(round(value));
		}

		return array;
	}

	private static float round(float value) {
		return Math.round(value * 1000f) / 1000f;
	}

	/** Axis aligned bounding box helper. */
	public record Bounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {

		public boolean contains(float x, float y, float z, float tolerance) {
			return x >= this.minX - tolerance && x <= this.maxX + tolerance
					&& y >= this.minY - tolerance && y <= this.maxY + tolerance
					&& z >= this.minZ - tolerance && z <= this.maxZ + tolerance;
		}
	}
}