# PKShield & PKSword Android

**Pokémon Shield & Pokémon Sword (Nintendo Switch, update 1.3.2) running natively on Android phones.** This is not emulation: the games' own ARM64 code runs
directly on the phone's processor, rewritten only where Android requires it, and their graphics (NVN) are rebuilt on Vulkan.

> [!IMPORTANT]
> **Fork Notice & Disclaimer:** This project is a personal fork of the original repository:
> **https://github.com/simipoB/PKShield-Android**
>
> The modifications and additional features here were implemented strictly as a personal project. The code does not adhere to strict C/C++ standards, and features have not been 100% thoroughly verified across devices. As a result, this fork may contain significant bugs and regressions that are **completely unknown to the original developer**. Please **do not report issues from this fork to the upstream repository or the original author**.

> [!WARNING]
> **Beta.** Tested on a Samsung Galaxy S25+ (Snapdragon 8 Elite, Adreno 830) with Qualcomm's driver and with Mesa Turnip, as well as Adreno 6xx and 7xx devices. Other phones are
> expected to work but have not been tested yet: reports are welcome (see [CONTRIBUTING.md](CONTRIBUTING.md)).

> [!NOTE]
> **No game files, compiled game code, shaders or keys are included, and none will be provided.** You need your own legally obtained dump of
> Pokémon Shield or Pokémon Sword (base game and update 1.3.2) and the `prod.keys` of your own console. Instructions on how to obtain them will not be provided.

## Features

- **Dual-Version Support (Sword & Shield)**:
  - Play either **Pokémon Shield** (`01008DB008C2C000`) or **Pokémon Sword** (`0100ABF008968000`), or both within a single app.
  - **Shared Storage**: Common assets in RomFS (~95% of game data) are shared across versions to save 10–15 GB of phone storage; only version-specific modules, shaders (`shaders_sword.pak`, `shaders_shield.pak`), DLCs, and saves (`save_sword`, `save_shield`) are stored separately.
  - **In-App Version Switcher**: Easily switch between Pokémon Sword and Pokémon Shield from the setup/launcher screen when both are installed.
  - **Dedicated Shortcuts**: Separate pinned and dynamic home screen shortcuts for Pokémon Sword and Pokémon Shield with their respective official titles and icons.
- **20:9 Ultrawide Aspect Ratio**:
  - Native 20:9 3D camera viewport patch eliminates black side borders on modern smartphones without stretching or distorting 3D scene geometry.
  - Toggle between 20:9 (fill) and 16:9 (fit) in Settings.
- **60 FPS Support & Pacing**:
  - Built-in 60 FPS patch (present interval 1 with adaptive step pacing) for smooth gameplay.
  - Selectable framerate target (30 FPS or 60 FPS) in Settings.
- **Physical Controller Button Remapping**:
  - Custom button mapping configuration screen for Bluetooth and USB gamepads.
  - Remap A, B, X, Y, D-pad, bumpers, triggers, stick buttons, and + / − buttons, with custom and default layout presets.
- **Shader & Pipeline Precompiler (Anti-Stutter)**:
  - **Startup Prewarm**: Pre-compiles Vulkan graphics pipelines (`VkPipeline`) before launching the game, preventing runtime compilation hitches and micro-stutter (*tirones*) during battles, animations, and world exploration.
  - **Multi-Threaded Worker Pool**: Uses parallel worker threads (up to 6 CPU cores) to compile pipelines concurrently using Vulkan's thread-safe `VkPipelineCache`.
  - **Bundled Pipeline Seeds**: Shipped with pre-recorded pipeline manifests (`vk_pipelines_seed.bin`, 1,172 unique pipeline configurations from real gameplay) for both Pokémon Shield and Pokémon Sword, eliminating stutter from the very first run.
  - **Visual Startup Progress UI**: Displays an overlay with a progress bar, compiled count (`Compiling shaders... X / Y`), percentage indicator, and a "Skip" button to jump immediately into the game.
  - **Dynamic Pipeline Caching**: Pipelines encountered during play are automatically saved to versioned caches (`vk_pipelines_sword.bin` / `vk_pipelines_shield.bin`) and driver cache files (`vk_pipeline_cache_*.bin`).
  - **Configurable**: Can be enabled or disabled anytime in Settings (**Precompile shaders at startup** / `DM_PREWARM_STARTUP`).
- **Memory Optimizations & Trimming**:
  - Reclaims unused Vulkan device memory blocks (`nvn_vk_trim`) and purges allocator pages (Scudo `M_PURGE`) upon Android memory pressure callbacks (`onTrimMemory`, `APP_CMD_LOW_MEMORY`).
  - Proactive kernel unmapping with `MADV_DONTNEED` to prevent Low Memory Killer (LMK) crashes.
- **CPU Thread Pinning**:
  - Optional CPU affinity setting keeps game and rendering threads away from slow efficiency cores, directing work to high-performance cores.
- **GPU Compatibility & Driver Switching**:
  - Choose between the phone's system GPU driver or custom Vulkan drivers (Mesa Turnip via libadrenotools).
  - Vulkan presentation modes: `mailbox` (lowest latency, tear-free), `fifo` (vsync locked), or `immediate`.
  - Fixes and optimizations for Qualcomm Adreno 6xx, 7xx, and 8xx series GPUs.

## How it works

A Switch game is ARM64 code, and so is a phone. Instead of interpreting or recompiling that code, the engine loads the game's own modules and
runs them: a rewriter (`dm/tools/patch_nso.py`) changes the few instruction patterns Android's kernel and threads do not allow, and a host
runtime (`dm/host`) stands in for the Switch's system services. The game's GPU commands are replayed on Vulkan (`dm/gfx/nvn/nvn_vk.c`), with its
shaders translated to SPIR-V by Ryujinx's shader translator, on the phone, during setup.

## Install and play

1. Install `PokemonShield.apk` (from the releases, or build it yourself: [docs/BUILDING.md](docs/BUILDING.md)) and open it.
2. **Game files → Choose**: your `.nsp` / `.xci` files for Pokémon Shield or Pokémon Sword (base game, update 1.3.2, any DLC). **Keys → Choose**: your `prod.keys` (and
   `title.keys` if your dump needs it). The keys stay in the app's private storage: never uploaded, never part of the APK.
3. **Set up the game.** The phone unpacks the game, rewrites its code and translates its shaders. When it is done the app *is* the game: it
   opens straight into it every time, and your dump files are no longer needed. A shortcut with the game's own icon and title (Pokémon Shield or Pokémon Sword) goes on the home screen.
4. **Dual setup (optional)**: Install the other game version at any time from Settings → **Set up second game**. The common RomFS files are reused automatically, taking only a fraction of the storage space.

Long-press the app's icon for **Settings**: graphics quality, aspect ratio, target FPS, controller button mapping, CPU pinning, touch controls, graphics driver, exporting/importing save data, or setting the game up again.
A few shaders the game builds while it runs are saved the first time and added at the next start.
If a file picker does not expose a path the app can read directly, the selected game file is copied into the app's storage; this needs free space for both the copy and the setup output.

## Requirements

- Android 12 or newer, arm64, Vulkan 1.1 or newer with dynamic rendering (most phones from 2020 on; Vulkan 1.3 recommended), about 15 GB free (shared for both versions).
- **Adreno** (Snapdragon 6xx, 7xx, 8xx): tested. Qualcomm's driver or a custom one (Mesa Turnip) chosen in the app.
- **Mali / PowerVR / Xclipse**: supported. Textures these GPUs cannot sample (BC formats) are decoded as they load; on Mali
  the framebuffers are set up to keep its compression (AFBC).

## Playing

- **Touch**: a stick on the left, A B X Y and the camera stick on the right, L / ZL and R / ZR on top, − and + at the top centre, a D-pad.
  Controls fade when untouched; touch anywhere to bring them back. Two fingers tapped together = B.
- **Edit the layout** in game: hold − for 2 seconds, then drag to move, pinch to resize, double-tap to hide or show, hold + to reset,
  hold − again to save.
- **Controllers**: connecting a Bluetooth / USB controller hides the touch controls; a pair of Joy-Cons plays as one controller. Map buttons in **Settings → Controller button mapping**.
- **Settings**:
  - **Graphics quality**: *High* (TV mode, 1080p), *Low* (handheld mode, 720p, for weaker phones) or *Auto* (High, switching to Low while the phone is hot).
  - **Aspect ratio**: *20:9* (ultrawide, fills modern phone displays) or *Fit* (standard 16:9).
  - **Target FPS**: *60 FPS* or *30 FPS*.
  - **Presentation mode**: *Mailbox*, *FIFO*, or *Immediate*.
  - **CPU thread pinning**: Keeps game threads prioritized on fast performance cores.
  - **Shader precompilation**: Toggle startup pipeline precompilation on/off (`DM_PREWARM_STARTUP`).
  - **Touch controls**: Opacity and fade time.
  - **Save management**: Export and import save data per version (`save_shield` / `save_sword`) as a ZIP.
- **Logs** for bug reports: `Android/data/com.nxnative.pokemonshield/files/log.txt` (and `log.prev.txt` for the run before).

## Known gaps

- No online features (Y-Comm, link trades, online raids).
- The water in the Wild Area renders darker than on the Switch.

## Building

[docs/BUILDING.md](docs/BUILDING.md): the APK from source on Linux or Windows (WSL) with one command.

## Credits

- **[d2r-mobile](https://github.com/jhaizhou-ops/d2r-mobile)** and **nxnative** (MIT): the native-execution engine, the NSO rewriter, the host
  runtime and the NVN layer this project is built from.
- **Ryujinx** (MIT): the shader translator (`dm/third_party/ryujinx`).
- **[LibHac](https://github.com/Thealexbarney/LibHac)**: reading NSP / XCI files on the phone.
- **[libadrenotools](https://github.com/bylaws/libadrenotools)** (BSD-2-Clause): custom Adreno drivers. **Mesa Turnip**: the driver it loads.
- **[Atmosphère](https://github.com/Atmosphere-NX/Atmosphere)**: the reference for Horizon's synchronization semantics. **yuzu**: Maxwell
  instruction encodings.
- **SPIRV-Cross** (Apache-2.0), **CPython** (PSF), **capstone** (BSD), **LZ4** (BSD), **libvpx** (BSD-3-Clause), **stb_vorbis** (MIT / public
  domain), **Vulkan Headers** (Apache-2.0 / MIT), **DejaVu Sans** (the button icons' letters), Android **native_app_glue** (Apache-2.0).
- **LASRecompAndroid** and **BOTWRecompAndroid**: ideas for Android presentation and CPU scheduling.

Pokémon, Pokémon Shield, and Pokémon Sword are trademarks of Nintendo, Creatures Inc. and GAME FREAK inc. This project is not affiliated with or endorsed by
Nintendo, The Pokémon Company, Creatures Inc. or GAME FREAK inc.
