package com.aialchemy.core.script;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaClosure;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Prototype;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.lib.BaseLib;
import org.luaj.vm2.lib.DebugLib;
import org.luaj.vm2.lib.MathLib;
import org.luaj.vm2.lib.PackageLib;
import org.luaj.vm2.lib.StringLib;
import org.luaj.vm2.lib.TableLib;
import org.luaj.vm2.lib.VarArgFunction;

import com.aialchemy.core.validation.Issue;

/**
 * The sandboxed Lua runtime (design document sections 8 and 16).
 *
 * <p>The design was verified against every hostile case in the threat model before it was wired
 * into the mod; see {@code ScriptSandboxTest} for the corpus. In short:</p>
 *
 * <ul>
 *   <li>a fresh {@link Globals} is built per call from a prototype compiled once per item, so
 *       scripts cannot share state;</li>
 *   <li>only base, table, string and math survive installation, and {@code package}, {@code debug},
 *       {@code io}, {@code os}, {@code luajava}, {@code require}, {@code dofile}, {@code loadfile},
 *       {@code load}, {@code loadstring}, {@code collectgarbage}, {@code getmetatable},
 *       {@code setmetatable}, {@code rawget} and {@code rawset} are removed before a script runs;</li>
 *   <li>the instruction hook is installed from Java <em>before</em> the visible debug library is
 *       taken away, so scripts cannot see or remove it;</li>
 *   <li>instruction, wall clock and exact per-thread allocation budgets abort a runaway script -
 *       including the {@code s = s .. s} doubling bomb, which no library cap can catch;</li>
 *   <li>{@code string.rep} and {@code table.concat} are replaced by capped versions.</li>
 * </ul>
 */
public final class ScriptSandbox {

	private ScriptSandbox() {
	}

	/** Result of validating and compiling a script. */
	public record CompileResult(CompiledScript script, String error, Issue.Collector issues) {

		public boolean ok() {
			return this.script != null;
		}
	}

	/** Compiles, statically checks and dry runs a script. */
	public static CompileResult compile(String scriptId, String source, ScriptLimits limits) {
		Issue.Collector issues = new Issue.Collector();

		if (source == null || source.isBlank()) {
			issues.error("script.empty", "no script supplied");
			return new CompileResult(null, "empty script", issues);
		}

		int bytes = source.getBytes(StandardCharsets.UTF_8).length;

		if (bytes > limits.maxSourceBytes()) {
			issues.error("script.size", "script is " + bytes + " bytes, the limit is " + limits.maxSourceBytes());
			return new CompileResult(null, "script too large", issues);
		}

		StaticScriptCheck.check(source, issues);

		if (issues.hasErrors()) {
			return new CompileResult(null, issues.errorSummary(), issues);
		}

		Prototype prototype;

		try {
			Globals compileGlobals = new Globals();
			new BaseLib().call(LuaValue.valueOf("base"), compileGlobals);
			LuaC.install(compileGlobals);
			LuaValue chunk = compileGlobals.load(source, "@" + scriptId);

			if (!(chunk instanceof LuaClosure closure)) {
				issues.error("script.compile", "script did not compile to a closure");
				return new CompileResult(null, "not a chunk", issues);
			}

			prototype = closure.p;
		} catch (LuaError error) {
			issues.error("script.syntax", error.getMessage());
			return new CompileResult(null, error.getMessage(), issues);
		} catch (Throwable throwable) {
			issues.error("script.compile", throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
			return new CompileResult(null, "compile failed", issues);
		}

		// Dry run: load the chunk in the sandbox with no host API and see which hooks it defines.
		Set<ScriptHook> hooks = new LinkedHashSet<>();

		try {
			InstructionGuard guard = new InstructionGuard(limits, 4_000, 2L * 1024L * 1024L);
			Globals environment = sandbox(guard);
			ScriptHostBinder.attach(environment, new LuaTable(), new LuaTable(), new LuaTable());
			new LuaClosure(prototype, environment).call();

			for (ScriptHook hook : ScriptHook.values()) {
				if (environment.get(hook.luaName()).isfunction()) {
					hooks.add(hook);
				}
			}
		} catch (Throwable throwable) {
			ScriptAbort abort = findAbort(throwable);

			if (abort != null && abort.reason() == ScriptAbort.Reason.INSTRUCTIONS) {
				issues.warning("script.dryrun.heavy", "script does a lot of work at load time and may be rejected");
			} else {
				issues.error("script.dryrun", describe(throwable));
				return new CompileResult(null, describe(throwable), issues);
			}
		}

		if (hooks.isEmpty()) {
			issues.error("script.hooks", "script defines none of the supported hooks");
			return new CompileResult(null, "no known hook defined", issues);
		}

		return new CompileResult(new CompiledScript(scriptId, source, prototype, hooks), null, issues);
	}

	/** Runs one hook. Never throws: every failure is reported through the result. */
	public static ScriptCallResult run(CompiledScript script, ScriptHook hook, ScriptHostBinder binder,
			ScriptLimits limits, ScriptCallContext context) {
		if (!script.defines(hook)) {
			return ScriptCallResult.missingHook();
		}

		InstructionGuard guard = new InstructionGuard(limits, limits.instructionsPerCall(), limits.memoryBytes());
		CallBudget budget = new CallBudget(limits);
		long start = System.nanoTime();

		try {
			Globals environment = sandbox(guard);
			binder.bind(environment, budget, context);

			// The chunk body is what defines the hook functions, so it runs before the hook itself.
			script.newClosure(environment).call();
			LuaValue hookFunction = environment.get(hook.luaName());

			if (!hookFunction.isfunction()) {
				return ScriptCallResult.missingHook();
			}

			LuaValue player = environment.get("player");
			LuaValue world = environment.get("world");
			LuaValue target = orEmptyTable(environment.get("target"));
			LuaValue position = orEmptyTable(environment.get("pos"));
			LuaValue damageSource = orEmptyTable(environment.get("damage_source"));
			LuaValue damageAmount = environment.get("damage_amount");
			LuaValue amount = damageAmount.isnumber() ? damageAmount : LuaValue.valueOf(1);

			Varargs arguments = switch (hook) {
				case ON_USE, ON_TICK -> LuaValue.varargsOf(player, world);
				case ON_HIT -> LuaValue.varargsOf(player, target);
				case ON_MINE -> LuaValue.varargsOf(player, world, position);
				case ON_DAMAGED -> LuaValue.varargsOf(player, damageSource, amount);
				default -> LuaValue.varargsOf(new LuaValue[] {player});
			};

			hookFunction.invoke(arguments);

			long elapsed = System.nanoTime() - start;
			return ScriptCallResult.ok(guard.instructions(), guard.allocatedBytes(), elapsed, budget.apiCalls());
		} catch (Throwable throwable) {
			long elapsed = System.nanoTime() - start;
			ScriptAbort abort = findAbort(throwable);
			ScriptAbort.Reason reason = abort != null ? abort.reason()
					: (budget.firstAbort() != null ? budget.firstAbort() : ScriptAbort.Reason.RUNTIME);

			return ScriptCallResult.aborted(reason, describe(throwable), guard.instructions(),
					guard.allocatedBytes(), elapsed, budget.apiCalls());
		}
	}

	/**
	 * Builds the sandboxed environment. Ordering matters: {@code TableLib} and {@code StringLib}
	 * index {@code package.loaded} while installing themselves, so a package table must exist first
	 * and is removed again immediately afterwards.
	 */
	public static Globals sandbox(InstructionGuard guard) {
		Globals environment = new Globals();
		new BaseLib().call(LuaValue.valueOf("base"), environment);
		new PackageLib().call(LuaValue.valueOf("package"), environment);
		new TableLib().call(LuaValue.valueOf("table"), environment);
		new StringLib().call(LuaValue.valueOf("string"), environment);
		new MathLib().call(LuaValue.valueOf("math"), environment);

		// Install the instruction hook, then take the visible debug library away again.
		guard.call(LuaValue.valueOf("debug"), environment);

		environment.set("debug", LuaValue.NIL);
		environment.set("package", LuaValue.NIL);
		environment.set("require", LuaValue.NIL);
		environment.set("module", LuaValue.NIL);
		environment.set("dofile", LuaValue.NIL);
		environment.set("loadfile", LuaValue.NIL);
		environment.set("load", LuaValue.NIL);
		environment.set("loadstring", LuaValue.NIL);
		environment.set("collectgarbage", LuaValue.NIL);
		environment.set("print", LuaValue.NIL);
		environment.set("getmetatable", LuaValue.NIL);
		environment.set("setmetatable", LuaValue.NIL);
		environment.set("rawget", LuaValue.NIL);
		environment.set("rawset", LuaValue.NIL);

		capLibraryAllocations(environment);

		environment.set("log", new VarArgFunction() {
			@Override
			public Varargs invoke(Varargs args) {
				return LuaValue.NIL;
			}
		});

		return environment;
	}

	/** Replaces the two library functions that can allocate unbounded memory. */
	private static void capLibraryAllocations(Globals environment) {
		int maxString = 4_096;

		environment.get("string").set("rep", new VarArgFunction() {
			@Override
			public Varargs invoke(Varargs args) {
				String value = args.checkjstring(1);
				int count = args.checkint(2);

				if ((long) value.length() * count > maxString) {
					throw new LuaError(new ScriptAbort(ScriptAbort.Reason.MEMORY));
				}

				return LuaValue.valueOf(value.repeat(count));
			}
		});

		environment.get("table").set("concat", new VarArgFunction() {
			@Override
			public Varargs invoke(Varargs args) {
				LuaTable table = args.checktable(1);
				String separator = args.optjstring(2, "");
				StringBuilder builder = new StringBuilder();
				int length = table.length();

				for (int index = 1; index <= length; index++) {
					builder.append(table.get(index).tojstring()).append(separator);

					if (builder.length() > maxString) {
						throw new LuaError(new ScriptAbort(ScriptAbort.Reason.MEMORY));
					}
				}

				return LuaValue.valueOf(builder.toString());
			}
		});
	}

	/** Walks a throwable chain looking for the abort that stopped the script. */
	public static ScriptAbort findAbort(Throwable throwable) {
		Throwable current = throwable;

		for (int depth = 0; current != null && depth < 12; depth++) {
			if (current instanceof ScriptAbort abort) {
				return abort;
			}

			if (current instanceof LuaError error && error.getMessageObject() != null
					&& error.getMessageObject().isuserdata() && error.getMessageObject().touserdata() instanceof ScriptAbort abort) {
				return abort;
			}

			current = current.getCause() == current ? null : current.getCause();
		}

		return null;
	}

	private static String describe(Throwable throwable) {
		ScriptAbort abort = findAbort(throwable);

		if (abort != null) {
			return abort.reason().description();
		}

		String message = throwable.getMessage();

		if (message == null) {
			message = throwable.getClass().getSimpleName();
		}

		return message.length() > 300 ? message.substring(0, 300) + "..." : message;
	}

	private static LuaValue orEmptyTable(LuaValue value) {
		return value == null || value.isnil() ? new LuaTable() : value;
	}

	/**
	 * The instruction hook. It is also the memory and wall clock watchdog: allocation is measured
	 * exactly, per thread, through {@code ThreadMXBean}, which is the only way to catch a script that
	 * doubles a string until the JVM dies.
	 */
	public static final class InstructionGuard extends DebugLib {

		private final int instructionLimit;
		private final long deadline;
		private final long memoryBudget;
		private final com.sun.management.ThreadMXBean allocationBean;
		private final long startAllocated;
		private final long threadId;

		private int instructions;

		public InstructionGuard(ScriptLimits limits, int instructionLimit, long memoryBudget) {
			this.instructionLimit = instructionLimit;
			this.memoryBudget = memoryBudget;
			this.deadline = System.nanoTime() + limits.wallClockMillis() * 1_000_000L;
			java.lang.management.ThreadMXBean bean = ManagementFactory.getThreadMXBean();
			this.allocationBean = bean instanceof com.sun.management.ThreadMXBean sun ? sun : null;
			this.threadId = Thread.currentThread().getId();
			this.startAllocated = allocated();
		}

		public int instructions() {
			return this.instructions;
		}

		public long allocatedBytes() {
			return allocated() - this.startAllocated;
		}

		private long allocated() {
			return this.allocationBean == null ? 0L : Math.max(0L, this.allocationBean.getThreadAllocatedBytes(this.threadId));
		}

		@Override
		public void onInstruction(int pc, Varargs v, int top) {
			if (++this.instructions > this.instructionLimit) {
				throw new LuaError(new ScriptAbort(ScriptAbort.Reason.INSTRUCTIONS));
			}

			// Checking every 32 instructions keeps the watchdog cheap while still stopping the
			// string-doubling bomb long before the heap is at risk.
			if ((this.instructions & 0x1F) == 0) {
				if (allocated() - this.startAllocated > this.memoryBudget) {
					throw new LuaError(new ScriptAbort(ScriptAbort.Reason.MEMORY));
				}

				if (System.nanoTime() > this.deadline) {
					throw new LuaError(new ScriptAbort(ScriptAbort.Reason.WALL_CLOCK));
				}
			}
		}
	}
}
