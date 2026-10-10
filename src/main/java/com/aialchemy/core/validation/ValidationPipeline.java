package com.aialchemy.core.validation;

import java.util.ArrayList;
import java.util.List;

import com.aialchemy.core.GeneratedItem;
import com.aialchemy.core.ItemEffect;
import com.aialchemy.core.Rarity;
import com.aialchemy.core.Shell;
import com.aialchemy.core.StatRanges;
import com.aialchemy.core.StatSet;
import com.aialchemy.core.ValidationContext;
import com.aialchemy.core.icon.IconValidator;
import com.aialchemy.core.icon.Palette;
import com.aialchemy.core.icon.PixelIcon;
import com.aialchemy.core.icon.ProceduralIcon;
import com.aialchemy.core.json.ItemCandidate;
import com.aialchemy.core.json.ItemJson;
import com.aialchemy.core.model.CuboidModel;
import com.aialchemy.core.model.ModelValidator;
import com.aialchemy.core.model.StockModels;
import com.aialchemy.core.script.CompiledScript;
import com.aialchemy.core.script.ScriptCallContext;
import com.aialchemy.core.script.ScriptCallResult;
import com.aialchemy.core.script.ScriptHook;
import com.aialchemy.core.script.ScriptHostBinder;
import com.aialchemy.core.script.ScriptLimits;
import com.aialchemy.core.script.ScriptSandbox;

/**
 * The seven step validation pipeline from design document section 7.4, with its fallback ladder.
 *
 * <ol>
 *   <li>JSON parse and schema check</li>
 *   <li>stat clamping to the tier ranges</li>
 *   <li>content filter on name and lore</li>
 *   <li>icon check, falling back to a procedural sprite</li>
 *   <li>model check, falling back to the shell's recoloured stock template</li>
 *   <li>Lua static check</li>
 *   <li>Lua dry run, falling back to a stats-only item</li>
 * </ol>
 *
 * <p>Steps 1 to 3 are fatal: a response that fails them is retried or, after the retries are used
 * up, discarded. Steps 4 to 7 degrade gracefully, because a partially broken item is still a usable
 * item and the player has already spent materials.</p>
 */
public final class ValidationPipeline {

	private ValidationPipeline() {
	}

	/** Everything the pipeline needs that is not inside the AI response. */
	public record Input(
			String recipeKey,
			String itemId,
			String discoverer,
			long createdAt,
			long seed,
			List<Integer> ingredientColors,
			ValidationContext context,
			ScriptLimits scriptLimits) {
	}

	/** Outcome of one pass through the pipeline. */
	public record Result(
			GeneratedItem item,
			String fatalError,
			Issue.Collector issues,
			boolean fallbackIcon,
			boolean fallbackModel,
			boolean fallbackScript,
			CompiledScript compiledScript) {

		public boolean ok() {
			return this.item != null;
		}
	}

	public static Result process(String rawResponse, Input input) {
		Issue.Collector issues = new Issue.Collector();
		ValidationContext context = input.context();

		// 1. JSON parse and schema check.
		ItemJson.ParseResult parsed = ItemJson.parse(rawResponse);

		if (!parsed.ok()) {
			return new Result(null, parsed.error(), issues, false, false, false, null);
		}

		ItemCandidate candidate = parsed.candidate();

		Shell shell = Shell.byJsonName(candidate.shell()).orElse(null);

		if (shell == null) {
			return new Result(null, "unknown shell '" + candidate.shell() + "'", issues, false, false, false, null);
		}

		Palette palette;

		try {
			palette = Palette.parse(candidate.palette());
		} catch (IllegalArgumentException exception) {
			return new Result(null, "invalid palette: " + exception.getMessage(), issues, false, false, false, null);
		}

		// 3. Content filter.
		Issue.Collector textIssues = ContentFilter.validate(candidate.name(), candidate.lore(), context);
		merge(issues, textIssues);

		if (textIssues.hasErrors()) {
			return new Result(null, textIssues.errorSummary(), issues, false, false, false, null);
		}

		// 2. Stat clamping.
		StatRanges ranges = StatRanges.forTier(context.tier());
		StatSet rawStats = new StatSet(
				candidate.stats().damage(), candidate.stats().attackSpeed(), candidate.stats().durability(),
				candidate.stats().armor(), candidate.stats().armorToughness(), candidate.stats().miningSpeed(),
				candidate.stats().food(), candidate.stats().saturation(), candidate.stats().abilityPower());
		StatSet clamped = ranges.clamp(rawStats, shell);

		if (!clamped.equals(rawStats)) {
			issues.warning("stats.clamped", "AI stats were clamped to tier " + context.tier() + " ranges");
		}

		List<ItemEffect> effects = filterEffects(candidate.effects(), context, issues);
		Rarity rarity = Rarity.byJsonName(candidate.rarity());

		// 4. Icon check (never fatal).
		boolean fallbackIcon = false;
		PixelIcon icon;

		if (IconValidator.validate(candidate.icon(), palette).hasErrors()) {
			fallbackIcon = true;
			icon = ProceduralIcon.build(palette, shell, input.seed());
			issues.warning("icon.fallback", "AI icon rejected, a procedural sprite was built from the ingredient colours");
		} else {
			icon = PixelIcon.parse(candidate.icon());
		}

		// 5. Model check (never fatal).
		boolean fallbackModel = false;
		CuboidModel model = null;

		if (context.generateModels()) {
			if (candidate.model() == null) {
				fallbackModel = true;
				model = StockModels.forShell(shell, palette);
				issues.warning("model.fallback", "no model supplied, using the shell template");
			} else {
				CuboidModel parsedModel = CuboidModel.fromJson(candidate.model());
				Issue.Collector modelIssues = ModelValidator.validate(parsedModel, context, palette);
				merge(issues, modelIssues);

				if (modelIssues.hasErrors()) {
					fallbackModel = true;
					model = StockModels.forShell(shell, palette);
					issues.warning("model.fallback", "AI model rejected (" + modelIssues.errorSummary() + "), using the shell template");
				} else {
					model = parsedModel;
				}
			}
		}

		// 6 and 7. Lua static check and dry run (never fatal).
		boolean fallbackScript = false;
		CompiledScript compiled = null;
		String script = candidate.lua();

		if (!context.allowScripts()) {
			fallbackScript = true;
			script = null;
			issues.warning("script.disabled", "scripts are disabled by configuration, item is stats only");
		} else if (script != null && !script.isBlank()) {
			ScriptSandbox.CompileResult compileResult = ScriptSandbox.compile(input.itemId(), script, input.scriptLimits());
			merge(issues, compileResult.issues());

				if (compileResult.ok()) {
					// 7. Dry run: with no world attached, a script that never terminates, recurses
					// forever or allocates its way out is caught here, before it is stored.
					ScriptCallResult dryRun = ScriptSandbox.run(compileResult.script(), ScriptHook.ON_USE,
							ScriptHostBinder.NONE, input.scriptLimits(),
							new ScriptCallContext(input.itemId(), ScriptHook.ON_USE, candidate.name(), context.tier(),
									0L, input.seed()));

					if (dryRun.ok() || dryRun.hookMissing()) {
						compiled = compileResult.script();
					} else {
						fallbackScript = true;
						script = null;
						issues.warning("script.fallback", "AI script failed its dry run (" + dryRun.describe()
								+ "), item is stats only");
					}
				} else {
				fallbackScript = true;
				script = null;
				issues.warning("script.fallback", "AI script rejected (" + compileResult.error() + "), item is stats only");
			}
		}

		GeneratedItem item = new GeneratedItem(
				input.itemId(),
				input.recipeKey(),
				sanitizeName(candidate.name(), shell),
				list(candidate.lore()),
				shell,
				rarity,
				clamped,
				effects,
				palette,
				icon,
				model,
				script,
				context.tier(),
				input.discoverer(),
				input.createdAt(),
				issues.issues(),
				fallbackScript);

		return new Result(item, null, issues, fallbackIcon, fallbackModel, fallbackScript, compiled);
	}

	private static List<ItemEffect> filterEffects(List<ItemCandidate.EffectSpec> raw, ValidationContext context, Issue.Collector issues) {
		List<ItemEffect> effects = new ArrayList<>();
		int budget = StatRanges.forTier(context.tier()).effects();

		for (ItemCandidate.EffectSpec spec : raw) {
			if (effects.size() >= budget) {
				issues.warning("effects.trimmed", "more effects than tier " + context.tier() + " allows, extras dropped");
				break;
			}

			ItemEffect effect = new ItemEffect(spec.id(), spec.ticks(), spec.amplifier());

			if (!EffectAllowlist.EFFECTS.contains(effect.id())) {
				issues.warning("effects.rejected", "effect '" + effect.id() + "' is not allowlisted and was dropped");
				continue;
			}

			effects.add(effect);
		}

		return effects;
	}

	/** Strips anything that could confuse the client and keeps the name to a sane length. */
	private static String sanitizeName(String name, Shell shell) {
		String sanitized = name.replace('\n', ' ').replace('\r', ' ').replace('§', ' ').trim();

		if (sanitized.isEmpty()) {
			sanitized = "Alchemised " + shell.jsonName();
		}

		return sanitized.length() > 40 ? sanitized.substring(0, 40) : sanitized;
	}

	private static List<String> list(List<String> values) {
		List<String> sanitized = new ArrayList<>();

		for (String value : values) {
			if (value == null) {
				continue;
			}

			String line = value.replace('\n', ' ').replace('§', ' ').trim();

			if (!line.isEmpty()) {
				sanitized.add(line.length() > 160 ? line.substring(0, 160) : line);
			}
		}

		return sanitized;
	}

	private static void merge(Issue.Collector target, Issue.Collector source) {
		for (Issue issue : source.issues()) {
			if (issue.severity() == Issue.Severity.ERROR) {
				target.error(issue.code(), issue.message());
			} else {
				target.warning(issue.code(), issue.message());
			}
		}
	}
}
