# Implementation notes

Working notes for anyone maintaining this mod: the parts of the 1.21.11 API that are not obvious, the
architectural seams, and the measurements behind the sandbox. The user-facing documentation is in
[`../README.md`](../README.md).

## Toolchain

- Minecraft 1.21.11, Fabric Loader 0.19.5, Fabric API 0.141.6+1.21.11
- Loom `net.fabricmc.fabric-loom-remap` 1.18.4, Gradle wrapper 9.7.1
- **Mojang official mappings** (`loom.officialMojangMappings()`), not Yarn
- The build JVM must be **JDK 25**; `options.release = 21` so the jar runs on 21+
- `./gradlew build` also runs the test suite; `splitEnvironmentSourceSets()` keeps `src/client/java`
  separate from `src/main/java`
- Mapped Minecraft jars for API spelunking:
  `~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/{minecraft-common,minecraft-clientonly}/*/*.jar`

## Module layout

```
src/main/java/com/aialchemy/
├── AiAlchemy.java            mod initialiser, lifecycle, world state switch
├── core/                     pure Java, no Minecraft imports, unit tested
│   ├── Shell, Rarity, Ingredient, RecipeKey, StatSet, StatRanges
│   ├── ItemValueTable, PowerBudget
│   ├── icon/                 Palette, PixelIcon, IconValidator, IconRenderer, ProceduralIcon
│   ├── model/                CuboidModel, ModelPart, PartRotation, Pattern, IdleEffect,
│   │                         ModelLimits, ModelValidator, StockModels
│   ├── json/                 ItemCandidate (AI schema), ItemJson (cache serialisation)
│   ├── validation/           Issue, ContentFilter, EffectAllowlist, ValidationPipeline
│   ├── script/               ScriptSandbox and its small value types
│   └── prompt/               PromptBuilder
├── component/                AlchemyData component, ModComponents
├── item/                     ModItems, AlchemyItem, ItemFactory (AlchemyData -> ItemStack)
├── config/                   AlchemyConfig
├── storage/                  AlchemyStorage (world files)
├── generation/               AiProvider, HttpAiProvider, MockAiProvider, OfflineAiProvider,
│                             GenerationService
├── network/                  AlchemyPayloads, AlchemyNetworking
├── script/                   ScriptRuntime, HookContext, McScriptHostBinder
└── server/                   WorldAlchemyState, TransmuteService, CraftingGrid, AssetSync,
                              RateLimits, ServerHooks, AlchemyCommands

src/client/java/com/aialchemy/client/
├── AiAlchemyClient.java      client initialiser
├── ClientAssets.java         icon textures, model cache, disk cache, asset requests
├── render/                   AlchemyModelPlugin, AlchemyItemModel, AlchemySpecialModelRenderer,
│                             RenderArgument, ItemTransforms
└── ui/                       CraftingScreenHook (the Transmute button)
```

The bright line is `core/`: it holds every rule, limit and validation step, has no Minecraft
dependency, and is covered by `./gradlew test`. The Minecraft packages are thin adapters: they read
world state, call `core`, and apply the result.

## Version specific API facts (verified against 1.21.11, Mojang mappings)

### Registration and components

- `net.minecraft.resources.Identifier` replaces `ResourceLocation`.
- Items: `new Item.Properties().setId(ResourceKey<Item>)`, then
  `Registry.register(BuiltInRegistries.ITEM, key, item)`.
- Data components: `Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id,
  DataComponentType.<T>builder().persistent(codec).networkSynchronized(streamCodec).build())`, with
  `ByteBufCodecs.STRING_UTF8 / VAR_INT / BOOL / byteArray(int)` and `StreamCodec.composite(...)`.

### Item hooks and events

- `Item` still calls `use`, `finishUsingItem`, `postHurtEnemy`, `mineBlock`, `getDestroySpeed`,
  `appendHoverText(ItemStack, Item.TooltipContext, TooltipDisplay, Consumer<Component>, TooltipFlag)`.
- There is **no** `inventoryTick` on `Item` in 1.21.11, so `on_tick`, `on_equip` and `on_unequip`
  are driven by `ServerHooks` and `ScriptRuntime` rather than by a vanilla override.
- `on_damaged` uses Fabric's `ServerLivingEntityEvents.AFTER_DAMAGE`.
- Permissions changed: `CommandSourceStack.permissions()` returns a
  `net.minecraft.server.permissions.PermissionSet`; use
  `Permissions.COMMANDS_GAMEMASTER` / `COMMANDS_ADMIN` instead of `hasPermission(int)`.
- `ServerPlayer.serverLevel()` is gone: `ServerPlayer.level()` returns a `ServerLevel` directly.

### Client rendering

The 1.21.11 item renderer is submit based:

- `ItemModel#update(ItemStackRenderState, ItemStack, ItemModelResolver, ItemDisplayContext,
  ClientLevel, ItemOwner, int seed)`
- `ItemStackRenderState#newLayer()`, `appendModelIdentityElement(Object)`
- `ItemStackRenderState.LayerRenderState#setupSpecialModel(SpecialModelRenderer<T>, T)`,
  `setTransform(ItemTransform)`, `setExtents(Supplier<Vector3fc[]>)`, `setFoilType(...)`,
  `setParticleIcon(...)`
- `SpecialModelRenderer#submit(T, ItemDisplayContext, PoseStack, SubmitNodeCollector, int light,
  int overlay, boolean foil, int seed)`
- `SubmitNodeCollector#submitCustomGeometry(PoseStack, RenderType, CustomGeometryRenderer)` with
  `CustomGeometryRenderer#render(PoseStack.Pose, VertexConsumer)`
- `RenderTypes.entityCutoutNoCull(Identifier)`, `LightTexture.FULL_BRIGHT` is now
  `net.minecraft.client.renderer.LightTexture`
- `DynamicTexture(String|Supplier<String>, NativeImage)`, `TextureManager#register(...)`,
  `NativeImage.read(byte[])`, `NativeImage.setPixelABGR(int, int, int)`

Fabric installs the model:

```java
context.modifyItemModelAfterBake().register((model, bakeContext) -> {
    if (SHELL_PATHS.contains(bakeContext.itemId().getPath())) return new AlchemyItemModel(model);
    return model;
});
```

### How a generated item is drawn

There is no texture atlas entry and no baked model for a generated item, so the drawing is split:

- **GUI**: the 16x16 PNG that arrived over the network is registered as a `DynamicTexture` and drawn
  as a flat, double sided quad at `z = 7.5 / 8.5` in the 0..16 item space.
- **World**: the cuboids are drawn as generated vertices (six faces each) sampling a shared 1x1 white
  texture, coloured per part from the item's palette with vanilla style directional shading
  (up 1.0, down 0.5, north/south 0.8, east/west 0.6). Emissive parts are submitted with
  `LightTexture.FULL_BRIGHT`.
- Display transforms mirror vanilla's `item/handheld` table and multiply in the model's validated
  `hand`, `ground` and `frame` scales (clamped 0.5 - 1.5).
- `idle_effect` (`spin`, `bob`, `pulse`) is applied per frame to the vertex positions and normals, so
  the stored model stays immutable.

Two seams keep the version specific risk in one place: `AlchemyModelPlugin` (registration) and
`AlchemySpecialModelRenderer` (drawing). Everything else in the mod passes plain data around.

## The Lua sandbox

Recipes that were measured, not guessed:

- `new Globals()`, then install `BaseLib`, `PackageLib`, `TableLib`, `StringLib`, `MathLib` **in that
  order**: `TableLib` and `StringLib` index `package.loaded` during construction and fail without
  `PackageLib` first.
- Compile with a separate `Globals` plus `LuaC.install`; run each call as
  `new LuaClosure(prototype, freshGlobals)` so a script cannot leave state behind between calls.
- After construction, set `debug`, `package`, `require`, `module`, `dofile`, `loadfile`, `load`,
  `loadstring`, `collectgarbage`, `print`, `getmetatable`, `setmetatable`, `rawget`, `rawset` to NIL.
  `Globals.debuglib` remains a public field, so the guard keeps running even though scripts can no
  longer see `debug`.
- `DebugLib.onInstruction(int, Varargs, int)` is the instruction hook; overriding it is what makes
  `while true do end` a contained abort.
- Instructions alone are not enough: `s = s .. s` allocates faster than it executes. A watchdog thread
  samples `ThreadMXBean.getThreadAllocatedBytes(id)` and aborts when the call exceeds
  `memoryBytes`. The abort arrives as a `LuaError`; walk `getCause()` to find the `ScriptAbort`.
- `pcall` cannot swallow an abort: the abort is rethrown as a `LuaError` that the guard recognises on
  the way out, so a `pcall` loop still terminates.

The hostile corpus lives in `src/test/java/.../ScriptSandboxTest.java` and is the specification for
all of the above.

## Numbers

| Budget | Default | Reasoning |
| --- | --- | --- |
| Instructions per call | 20,000 | An honest hook is a few hundred instructions; this is two orders of magnitude of headroom |
| Wall clock per call | 10 ms | One fifth of a 50 ms tick, and loose enough that a GC pause cannot kill an honest script |
| Allocation per call | 2 MiB | Survives ordinary table use, kills doubling bombs |
| API calls per call | 100 | |
| Entity spawns / particles | 4 / 64 | |
| Effect reach | 32 blocks | |
| Tick throttle | 10 ticks | |
| Script tick budget | 3 ms total | Shared by every player, so the worst case per tick is bounded |
| Aborts before disable | 3 | |

## Testing strategy

The hosted chat-completions request explicitly sets `max_tokens` to 8192 by default because a
complete item includes a 16x16 icon, model geometry and optional Lua. Responses with
`finish_reason: "length"` are rejected at the provider boundary instead of passing partial JSON
to validation; the normal generation retry path can then request a fresh complete item.

`./gradlew test` runs 39 tests across three suites:

- `ScriptSandboxTest` - the hostile corpus: infinite loops, recursion, allocation bombs, `pcall`
  swallowing, escape hatches, static check rejection, wall clock, size caps.
- `ValidationTest` - stat clamping per shell and tier, value table and tiers, recipe key canonicality,
  model topology and bounds, rotation steps, stock templates, the content filter, every branch of the
  pipeline ladder, and a cache round trip.
- `IconTest` - palette parsing, validator rejections, PNG encode/decode symmetry, procedural
  determinism.

Anything in `core/` should be reachable from a test without booting Minecraft. When you add a rule
there, add the case that breaks it.
