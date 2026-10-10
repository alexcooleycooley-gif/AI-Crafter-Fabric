# AI Alchemy

A Fabric mod for **Minecraft 1.21.11** where the crafting grid becomes a prompt. Put ingredients in,
press **Transmute**, and a language model invents an item: a name, lore, stats, a 16x16 icon, a
cuboid 3D model and a short Lua script that gives it behaviour.

Nothing is pre-authored. Every item is generated at runtime, validated, sanitised and then stored in
the world's cache, so the next player to discover the same recipe gets it instantly and for free.

> `3x cobblestone + 2x stick` might become a *Pebble Knocker*; `1x nether star + 4x blaze rod` might
> become a *Starforge Brand* that sparks when it lands a hit.

---

## How it works

| Stage | What happens |
| --- | --- |
| **Craft** | The server reads the real crafting grid out of the open menu. Item ids, counts and rarity only: display names never leave the server, which is the prompt injection defence. |
| **Value** | Ingredients are scored against a data-driven value table and mapped to one of four power tiers. |
| **Ask** | The tier decides the hard stat ceilings and model limits written into the prompt, so a pile of cobblestone cannot roll a netherite sword. |
| **Validate** | The reply is parsed, clamped and filtered in seven steps. A fatal failure is retried with the error attached, then replaced by a deterministic offline item. |
| **Deliver** | The item goes into the player's inventory, the icon and model are pushed to every client, and the recipe plus its metadata are cached in the world. |

Four ladders keep a hostile or merely broken model from ruining a world:

- **Stats** are clamped into tier ranges and to what the shell can actually use.
- **Icons** must be 16 rows of 16 palette indices; a rejected icon is replaced by a procedural sprite
  built from the ingredient colours.
- **Models** must be one connected cluster inside the 16 unit grid; a rejected model falls back to a
  template for that shell.
- **Scripts** are statically checked, then dry run against a no-op world before they are ever stored;
  a script that loops or allocates its way out is dropped and the item ships as stats only.

## Shells

Minecraft freezes its item registry at startup, so an AI item cannot be a brand new registry entry.
Each generated item is one of eight pre-registered **shells** carrying an `AlchemyData` component that
holds its real identity, name, icon, model, stats and effects:

`alchemy_weapon`, `alchemy_tool`, `alchemy_armor_head`, `alchemy_armor_chest`, `alchemy_armor_legs`,
`alchemy_armor_feet`, `alchemy_food`, `alchemy_trinket`

## Requirements

- Minecraft **1.21.11** with **Fabric Loader 0.19.5+**
- **Fabric API 0.141.6+1.21.11**
- A server-side AI endpoint (OpenAI compatible) — or none at all, see *Offline mode*

The mod is **required on both sides**: the client half draws the generated icons and models. No
resource pack is needed, because generated assets travel over the network.

## Installation

1. Put `ai-alchemy-<version>.jar` and Fabric API into `mods/` on the server and on every client.
2. Start the server once: `config/ai-alchemy.json` is created.
3. Set `endpoint`, `apiKey` and `model`, then restart the server.

```json
{
  "aiProvider": "hosted",
  "endpoint": "https://api.openai.com/v1/chat/completions",
  "apiKey": "sk-...",
  "model": "gpt-4o-mini",
  "offlineMode": false,
  "mockProvider": false
}
```

`apiKey` is read from the world's config file. Keep that file out of backups you share, or use an
endpoint that authenticates by network instead.

### Offline mode

`"offlineMode": true` disables all network calls. Transmutation still works: the mod builds a
deterministic item from the shell template, the ingredient palette and the computed tier. That is
also what happens automatically after the provider fails `retries` times, so a dead endpoint never
blocks a crafting table.

`"mockProvider": true` is for development: the same deterministic generator, without touching the
provider at all.

## Command reference

| Command | Permission | Purpose |
| --- | --- | --- |
| `/alchemy journal` | everyone | Your discovery log |
| `/alchemy inspect` | everyone | Dump the held item's generated metadata |
| `/alchemy status` | everyone | Provider, cache and script counters |
| `/alchemy usage` | everyone | Request counters and your remaining quota |
| `/alchemy export` | op (gamemaster) | Write the whole cache to one JSON file |
| `/alchemy import <json>` | op (gamemaster) | Merge a shared cache |
| `/alchemy ban recipe <key>` | op (gamemaster) | Disable a recipe |
| `/alchemy unban recipe <key>` | op (gamemaster) | Re-enable it |
| `/alchemy ban-item <itemId>` | op (gamemaster) | Disable an item |
| `/alchemy enable-script <itemId>` | op (gamemaster) | Re-enable a script that aborted too often |
| `/alchemy reset-cache` | admin | Drop every cached recipe |

`/alchemy export` writes `<world>/ai_alchemy/export-<timestamp>.json`. That single file is the
recommended way to share a curated set of items between servers: copy it in and run
`/alchemy import <json>`.

## Configuration

`config/ai-alchemy.json`, re-read at server start:

| Key | Default | Meaning |
| --- | --- | --- |
| `aiProvider` | `hosted` | `hosted` uses `endpoint`; `offline` and `mock` never touch the network |
| `endpoint`, `apiKey`, `model` | — | OpenAI compatible chat completions endpoint |
| `requestTimeoutSeconds` | `45` | Per request timeout |
| `retries` | `2` | Retries when validation rejects an answer |
| `powerCap` | `1.0` | Scales every tier threshold; lower it on a survival server |
| `vanillaPriority` | `true` | Never overrule a recipe vanilla already understands |
| `cacheResults` | `true` | Cache per recipe key in the world |
| `offlineMode` | `false` | Build items locally, no network |
| `mockProvider` | `false` | Deterministic provider for testing |
| `allowScripts` | `true` | Master switch for Lua behaviour |
| `generateModels` | `true` | Master switch for 3D models |
| `maxModelParts` | `48` | Global cap, applied on top of the per tier cap |
| `modelRenderDistance` | `32` | Distance at which generated models are drawn in the world |
| `playerCooldownSeconds` | `15` | Between two generations for one player |
| `dailyLimitPerPlayer` | `50` | Generations per player per day (cache hits are free) |
| `maxConcurrentRequests` | `4` | Provider worker threads |
| `contentFilter` | `strict` | `off`, `basic` or `strict` |
| `shapedMatters` | `false` | Whether grid position is part of the recipe key |
| `scriptInstructionsPerCall` | `20000` | Lua instruction budget per hook call |
| `scriptWallClockMillis` | `10` | Wall clock budget per hook call |
| `scriptMemoryBytes` | `2097152` | Allocation budget per hook call |
| `scriptApiCallsPerCall` | `100` | Host API calls per hook call |
| `scriptEntitySpawnsPerCall` | `4` | Spawned entities per hook call |
| `scriptParticlesPerCall` | `64` | Particles per hook call |
| `scriptEffectRange` | `32.0` | Blocks a script may reach from the caster |
| `scriptRequiredCooldownTicks` | `10` | Floor applied to `player.set_cooldown` |
| `scriptTickThrottleTicks` | `10` | Minimum spacing of `on_tick` per item |
| `scriptAbortsBeforeDisable` | `3` | Aborts before a script is disabled |
| `scriptTickBudgetMicros` | `3000` | Total script time per server tick, across all players |

### Ingredient values

Power tiers come from a value table. The defaults live in the jar
(`data/ai_alchemy/item_values.json`), and a world can override them by creating
`<world>/ai_alchemy/item_values.json`:

```json
{
  "unknown_default": 2,
  "values": {
    "minecraft:iron_ingot": 5,
    "mynamespace:custom_gem": 40
  }
}
```

Unknown items fall back to `unknown_default`, or to their rarity's default value.

## Generated Lua behaviour

The model writes a short Lua 5.1 script. It may define any of these hooks:

| Hook | Called when |
| --- | --- |
| `on_use(player, world)` | the item is used |
| `on_hit(attacker, target)` | the item lands a melee hit |
| `on_mine(player, world, pos)` | a block is broken with the item |
| `on_eat(player, world)` | food is finished |
| `on_tick(player, world)` | throttled, and only while the item is held or worn |
| `on_equip(player, world)` / `on_unequip(player, world)` | put on or taken off |
| `on_damaged(player, world, source, amount)` | the wearer takes damage |

The exposed API is deliberately tiny:

```lua
-- player
player.pos()  player.look_direction()  player.health()  player.heal(n)  player.damage(n)
player.add_effect(id, ticks, amplifier)  player.knockback(dir, strength)
player.set_cooldown(ticks)  player.send_message(text)  player.is_sneaking()
player.held_item_name()  player.name()

-- world
world.tick()  world.time_until_day()  world.is_raining()
world.spawn_projectile(id, dir, speed)  world.spawn_particles(id, pos, count)
world.play_sound(id, pos)  world.entities_near(pos, radius)
world.block_at(pos)  world.light_level(pos)  world.ignite(pos)

-- util
util.vec(x, y, z)  util.random()  util.clamp(v, min, max)  util.distance(a, b)  util.log(text)
```

Effects, sounds, particles and projectiles are **allowlisted**: a script asking for anything outside
the list is aborted rather than executed. Coordinates further than `scriptEffectRange` from the caster
are refused too.

### The sandbox

The interpreter is LuaJ running inside the JVM: there is no file, network, process or Java access.
`io`, `os`, `package`, `require`, `load`, `loadfile`, `dofile`, `debug`, `collectgarbage`,
`getmetatable`, `setmetatable`, `rawget` and `rawset` are removed. Three budgets are enforced with a
custom debug hook plus a `ThreadMXBean` allocation watchdog:

- instructions per call,
- wall clock per call,
- allocated bytes per call (a naive `s = s .. s` aborts instead of exhausting the heap),

plus host level caps on API calls, entity spawns, particles and reach, and a global per tick time
budget shared by every player. A script that aborts `scriptAbortsBeforeDisable` times is disabled: the
item keeps its stats and simply stops casting. `/alchemy enable-script <itemId>` brings it back.

## Storage layout

Everything the server generates lives under `<world>/ai_alchemy/`:

```
<world>/ai_alchemy/
├── cache.json        generated items by recipe key
├── icons/<id>.png    16x16 icon payloads
├── models/<id>.json  cuboid model payloads
├── scripts/<id>.lua  generated behaviour scripts
├── journal.json      per player discovery log
├── bans.json         recipes and items disabled by operators
└── item_values.json  optional ingredient value override
```

An item stack carries only its 24 character id, not its metadata, so a shared backpack survives a
cache reset. Export the cache first anyway.

## Building from source

```bash
git clone https://github.com/alexcooleycooley-gif/AI-Crafter-Fabric
cd AI-Crafter-Fabric
JAVA_HOME=/path/to/jdk-25 ./gradlew build
```

- Loom `net.fabricmc.fabric-loom-remap` 1.18.4, Gradle wrapper 9.7.1
- **JDK 25** is required to *build*; the jar targets Java 21
- Official Mojang mappings, not Yarn
- LuaJ 3.0.1 is bundled into the jar with `include(...)`
- `./gradlew test` runs the sandbox hostile corpus and the validation suite

## Testing

```bash
JAVA_HOME=/path/to/jdk-25 ./gradlew test
```

The suite covers the interesting failure modes rather than the happy path: infinite loops, recursion,
`string.rep` and `table.concat` allocation bombs, `pcall`-swallowed aborts, escape hatch attempts,
disconnected and out-of-bounds models, absurd stats, banned names, prompt injection echoes,
prose-wrapped JSON, and a full cache round trip.

## License

This project is available under the CC0 license, like the Fabric template it was built from.