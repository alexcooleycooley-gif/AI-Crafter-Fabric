package com.aialchemy.core.validation;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The only status effects, sounds and particles a generated item may reference (design document
 * section 9.5). Allowlisting rather than range checking keeps the AI out of the parts of the
 * registry that are not meant for generic items.
 */
public final class EffectAllowlist {

	private EffectAllowlist() {
	}

	public static final Set<String> EFFECTS = Set.of(
			"minecraft:speed", "minecraft:slowness", "minecraft:haste", "minecraft:mining_fatigue",
			"minecraft:strength", "minecraft:instant_health", "minecraft:instant_damage",
			"minecraft:jump_boost", "minecraft:nausea", "minecraft:regeneration", "minecraft:resistance",
			"minecraft:fire_resistance", "minecraft:water_breathing", "minecraft:invisibility",
			"minecraft:blindness", "minecraft:night_vision", "minecraft:hunger", "minecraft:weakness",
			"minecraft:poison", "minecraft:wither", "minecraft:health_boost", "minecraft:absorption",
			"minecraft:saturation", "minecraft:glowing", "minecraft:levitation", "minecraft:luck",
			"minecraft:slow_falling", "minecraft:conduit_power", "minecraft:dolphins_grace",
			"minecraft:darkness");

	/** Sound events scripts may play through {@code world:play_sound}. */
	public static final Set<String> SOUNDS = Set.of(
			"minecraft:entity.player.levelup", "minecraft:entity.experience_orb.pickup",
			"minecraft:block.note_block.pling", "minecraft:entity.arrow.shoot",
			"minecraft:entity.ender_pearl.throw", "minecraft:entity.generic.explode",
			"minecraft:block.glass.break", "minecraft:entity.firework_rocket.launch",
			"minecraft:entity.lightning_bolt.thunder", "minecraft:block.amethyst_block.chime",
			"minecraft:entity.illager_celebrate", "minecraft:block.beacon.activate",
			"minecraft:entity.wither.shoot", "minecraft:block.portal.trigger",
			"minecraft:entity.zombie.attack_iron_door", "minecraft:ui.button.click",
			"minecraft:item.trident.thunder", "minecraft:entity.blaze.shoot");

	/** Particle types scripts may spawn through {@code world:spawn_particles}. */
	public static final Set<String> PARTICLES = Set.of(
			"minecraft:flame", "minecraft:soul_flame", "minecraft:smoke", "minecraft:crit",
			"minecraft:enchant", "minecraft:electric_spark", "minecraft:end_rod",
			"minecraft:heart", "minecraft:witch", "minecraft:portal", "minecraft:snowflake",
			"minecraft:happy_villager", "minecraft:note", "minecraft:cloud",
			"minecraft:dust", "minecraft:glow", "minecraft:sculk_soul", "minecraft:totem_of_undying",
			"minecraft:sonic_boom", "minecraft:dragon_breath");

	/** Entity types scripts may launch through {@code world.spawn_projectile}. */
	public static final Set<String> PROJECTILES = Set.of(
			"minecraft:arrow", "minecraft:snowball", "minecraft:egg", "minecraft:small_fireball",
			"minecraft:fireball", "minecraft:wither_skull", "minecraft:ender_pearl",
			"minecraft:splash_potion", "minecraft:experience_bottle", "minecraft:llama_spit");

	public static Set<String> effects() {
		return new LinkedHashSet<>(EFFECTS);
	}

	public static Set<String> sounds() {
		return new LinkedHashSet<>(SOUNDS);
	}

	public static Set<String> particles() {
		return new LinkedHashSet<>(PARTICLES);
	}

	public static Set<String> projectiles() {
		return new LinkedHashSet<>(PROJECTILES);
	}

	public static List<String> sorted(Set<String> values) {
		return values.stream().sorted().toList();
	}
}