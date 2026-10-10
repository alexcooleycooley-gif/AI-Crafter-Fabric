package com.aialchemy.core;

/**
 * The full stat block a generated item may carry. All values are deliberately stored as floats so
 * that clamping is lossless and the vanilla components can be fed without casts.
 */
public record StatSet(
		float damage,
		float attackSpeed,
		int durability,
		float armor,
		float armorToughness,
		float miningSpeed,
		int food,
		float saturation,
		float abilityPower) {

	public static final StatSet EMPTY = new StatSet(0f, 0f, 0, 0f, 0f, 0f, 0, 0f, 0f);

	public StatSet {
		damage = Math.max(0f, damage);
		attackSpeed = Math.max(0f, attackSpeed);
		durability = Math.max(0, durability);
		armor = Math.max(0f, armor);
		armorToughness = Math.max(0f, armorToughness);
		miningSpeed = Math.max(0f, miningSpeed);
		food = Math.max(0, food);
		saturation = Math.max(0f, saturation);
		abilityPower = Math.max(0f, abilityPower);
	}

	/** Attack speed is transmitted as a modifier around the vanilla player base value of 4.0. */
	public double attackSpeedModifier() {
		return this.attackSpeed - 4.0D;
	}

	public boolean isEmpty() {
		return this.equals(EMPTY);
	}
}