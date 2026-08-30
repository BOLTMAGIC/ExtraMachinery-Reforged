# Changelog

All notable changes to this project will be documented in this file.

## v0.1.3.0.1 — 2026-08-30
### Fixed
- Autocrafting: Fixed issue where input items were not fully consumed when ingredients were distributed across multiple input slots.
- The recipe calculation now correctly sums ingredients cumulatively across all slots, and output is scaled by the actual amount extracted rather than the planned amount.

## v0.1.3.0.0 — 2026-08-15
### Fixed
- Greenhouse not outputting mana

## v0.1.2.9.9 — 2026-07-15
### Fixed
- Fix Base Runic Altar Livingrock Slot tooltip (thx to MiniMaxi)

## v0.1.2.9.8 — 2026-06-17
### Fixed
- Mana Infuser now correctly detects and uses MythicBotany's infuser RecipeType (optional) via robust reflection.

### Changed
- Set superclass RecipeType reflectively so recipes are found; added logging to help debug recipe matching.

## v0.1.2.9.7 — 2026-04-26
### Fixed
- Changed Recipe for the catalyst_mana_infinity from crimson to mazarine

## v0.1.2.9.6 — 2026-04-26

### Fixed
- Automatic filling of Daisy inventories (Base / Upgraded / Advanced / Ultimate) via pipes / hoppers / AE: the insert predicate was adjusted so external systems can insert into empty slots while slots currently processing remain protected.
- AE export and automatic output of finished items remain unchanged: finished items are still automatically exported to the AE network; hoppers/pipes by default can only extract finished slots.

## v0.1.2.9.5 — 2026-04-06
### Fixed
- Fixed machines not getting mana and don't auto output items to the ME system.

## v0.1.2.9.4 — 2026-03-12

### Changed
- Performance: Wide-ranging optimizations to recipe/pattern handling to reduce CPU usage in hot paths
  - Added: `RecipeValidityCache` — a lightweight, conservative cache for frequent calls to `RecipeHelper.isItemValidInput(...)`. ItemStacks with NBT are not cached; cache is invalidated on datapack/recipe reload.
  - Ingredient fast-paths: simple Ingredients are now preloaded internally as `Item[]` arrays and matched by item equality; only complex (NBT/partial-NBT) Ingredients fall back to `Ingredient.test(...)`.
  - Hot-path refactors: iterators/streams in pattern/recipe matching paths replaced with index-based loops, and unnecessary temporary allocations reduced.
  - Cache invalidation: all relevant caches are cleared on datapack/recipe reload (wired into `EventListener`).

### Fixed
- Fixes during refactor/optimization: several compiler errors caused by inconsistent intermediate edits were corrected; project builds successfully (BUILD SUCCESSFUL).

## v0.1.2.9 — 2025-12-23

### Added
- Conditional recipe loading for MythicBotany-dependent machines
  - Four Mana Infuser recipes (`base`, `upgraded`, `advanced`, `ultimate`) now include a top-level Forge condition (`"conditions": [{ "type": "forge:mod_loaded", "modid": "mythicbotany" }]`) so they are only loaded when the `mythicbotany` mod is present.
- Checking if mana storage is full before inserting mana
  - Mana insertion logic now verifies if the target mana storage can accept more mana before performing the insertion, preventing overflows and voids.

### Changed
- JEI integration hardened for optional MythicBotany compatibility
  - Reflection lookup for MythicBotany JEI/recipe types is now cached and guarded by `ModList` checks to avoid ClassNotFound/Linkage errors.

### Fixed
- Prevent recipe/resource parsing errors when MythicBotany is not installed
  - Conditional recipe loading prevents JSON parser errors for missing MythicBotany items (e.g. `mythicbotany:mana_infuser`).
---
*Generated on 2025-12-23.*