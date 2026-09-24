# Arsenal

Modern firearms for Minecraft 26.3 (NeoForge). Eighteen real weapons built and modified at a **Weapon Workbench**,
their own ammunition for every calibre, detachable suppressors, optics, magazines and laser sights, four grenades,
four one-of-a-kind weapons of mass destruction, a uranium-fuelled Tactical Nuke that levels a 200 block radius
down past the deepslate, and a full-size, flyable **F-14 Tomcat** with its cannon, missiles, rockets and bombs.

---

## Installing

**CurseForge app** (what you use):

1. Create a custom profile → Minecraft **26.3** → **NeoForge**.
2. ⋮ → **Open Folder** → drop `arsenal-1.0.0.jar` into `mods`.

If CurseForge does not list a NeoForge build for 26.3, use the official installer from
[projects.neoforged.net](https://projects.neoforged.net/neoforged/neoforge), run the vanilla launcher once with the
NeoForge profile, and put the jar in `%appdata%\.minecraft\mods` instead.

The jar is at `build/libs/arsenal-1.0.0.jar`, with a copy in the project root.

---

## Controls

| Action | Default |
| --- | --- |
| Fire | **Right mouse** — hold it down on automatic weapons |
| Reload | **R** |
| Change ammunition (buckshot ↔ slug) | **Sneak + R** |
| Aim down the sights | **Sneak**, with an optic fitted or any sniper rifle |

The ammo counter sits above the bottom-right of the hotbar. Sprinting throws your aim off badly, crouching steadies
it, and an unsuppressed shot pulls every hostile within earshot towards you.

In the F-14 (all rebindable under Controls → Gameplay, "Jet: ..."):

| Action | Default |
| --- | --- |
| Steer | **Mouse** — the jet flies to where you look |
| Throttle | **W / S** — hold W at 100% to push through the detent into afterburner |
| Roll on command | **A / D** |
| M61 cannon | **Left mouse** (hold) |
| Fire the selected store | **Right mouse** — hold it to ripple Zuni rockets |
| Next store | **V** |
| Flares | **B** |
| Landing gear | **G** |
| Wheel brakes (ground) / speed brake (air) | **Space** |
| Canopy (parked) | **K** |
| Free look | **Left Alt** (hold) — look around while the jet holds its course |
| Climb out (on the ground) / eject (in the air) | **Shift** — hold it for over a second to eject |

---

## Getting started

1. Mine iron and craft a **Weapon Workbench** (`3 iron / iron + crafting table + iron / 3 smooth stone`).
2. Make **Crude Steel Blend** (1 iron + 2 coal, shapeless) and smelt it in a **blast furnace** → **Steel Ingot**.
   Steel is the backbone of everything in the mod.
3. Right-click the workbench. The left panel is the full catalogue — pick a category tab, click an item, and the
   right panel shows exactly what it costs and how much of it you have. **Build** makes one, **x8** makes eight.

Nothing in the mod can be crafted on a vanilla bench, so the workbench catalogue *is* the recipe book — you never
need JEI to find anything.

---

## The arsenal

### Assault rifles

| Weapon | Calibre | Mag | Damage | Notes |
| --- | --- | --- | --- | --- |
| AK-47 | 7.62mm | 30 | 9.0 | Hits hard, kicks hard |
| M4A1 Carbine | 5.56mm | 30 | 7.5 | Fast and controllable |
| FN SCAR-H | 7.62mm | 20 | 11.5 | Heaviest automatic |
| Steyr AUG | 5.56mm | 42 | 7.0 | Bullpup, huge magazine |

### Handguns

| Weapon | Calibre | Mag | Damage |
| --- | --- | --- | --- |
| Glock 17 | 9mm | 17 | 5.0 |
| Beretta M9 | 9mm | 15 | 5.5 |
| M1911 | .45 ACP | 7 | 7.5 |
| Desert Eagle | .50 AE | 7 | 13.0 |

### Sniper rifles

| Weapon | Calibre | Mag | Damage | Notes |
| --- | --- | --- | --- | --- |
| SVD Dragunov | 7.62mm | 10 | 18 | Semi-automatic, 140 blocks |
| Barrett M82 | .50 BMG | 10 | 30 | Punches through up to six targets |
| AWP Magnum | .338 Lapua | 5 | 45 | 220 blocks, 2.5x on a headshot |

### Shotguns

Every shotgun takes **12 Gauge Buckshot** (a cloud of pellets) or **12 Gauge Slug** (one heavy projectile, ~4.6x the
damage per pellet, far tighter). Sneak + R swaps between them.

| Weapon | Mag | Pellets | Notes |
| --- | --- | --- | --- |
| Remington 870 | 6 | 8 | Pump-action |
| SPAS-12 | 8 | 9 | Semi-automatic |
| AA-12 | 20 | 7 | Fully automatic, drum-fed |
| Sawed-Off | 2 | 12 | Both barrels in a tenth of a second |

### Heavy weapons

- **RPG-7** — one rocket, a real projectile you can watch fly: a 3D rocket with a burning motor, a smoke trail and
  its own roar. It leaves the tube at 1.3 blocks a tick and the sustainer speeds it up to 2.3. The launcher kicks
  hard (recoil 14, a shove back and a shaken screen; crouch to brace). Two rockets, Sneak + R swaps them:
  - **PG-7V** (HEAT) — a 4.6 power blast, 14 damage out to ten blocks.
  - **TBG-7V** (thermobaric) — three times that: a 14 power blast with fire, 45 damage out to sixteen blocks. A
    little slower out of the tube (1.1, up to 1.9). Built at the workbench from a rocket motor, two warhead casings,
    four explosive compound, four blaze powder and two fire charges.
- **M32 Grenade Launcher** — six 40mm rounds lobbed on an arc.
- **MK-IV Railgun** — charged: hold the trigger and the five coil compartments along the barrel light up one after
  another from the breech to the muzzle over two seconds (with a rising whine), and the whole gun turns white-hot
  when full; let go to fire.
  A tap does a quarter of the damage and stops in the first body. A full charge does 90 (1.5x its 60), punches through
  walls and up to eight bodies, and from 75% charge the slug hits the first wall with a shockwave. Three tungsten
  slugs, 1.5s between shots.

### Attachments

Four slots, one part each, all removable at the workbench at any time.

| Slot | Options |
| --- | --- |
| Muzzle | **Suppressor** (silent, mobs stay unaware) · **Muzzle Brake** (−40% spread, −35% recoil) · **Heavy Barrel** (+20% damage, +30% range, slower) |
| Optic | **Red Dot** (−25% spread) · **ACOG Scope** (4x, +25% range) · **Thermal Scope** (8x, everything alive glows while you aim) |
| Magazine | **Extended** (+50%) · **Drum** (+120%, slow reload) · **Quickdraw** (−45% reload) |
| Underbarrel | **Laser Sight** (−30% spread) · **Foregrip** (−20% spread, −30% recoil) · **Bipod** (−75% spread while crouched) |

### Grenades

- **Frag** — 3s fuse, blast plus splinters out to twelve blocks through line of sight.
- **Incendiary** — sets a seven-block radius alight and everything in it burning.
- **Flashbang** — blindness, nausea, slowness and mining fatigue out to eighteen blocks. No damage.
- **Smoke** — twenty seconds of cover; anything inside it is blind.

Grenades bounce off walls and cook on a fuse, so you can bank them round a corner.

### Weapons of mass destruction

- **Singularity Charge** — collapses into a black hole that drags everything within forty blocks in and grinds it
  down for eight seconds, then evaporates and takes a 28-block sphere of the world with it.
- **Thermobaric Bomb** — a fuel-air firestorm: flattens everything standing for forty blocks, cooks everything
  alive for forty-eight, and leaves the ground scorched and burning rather than cratered.
- **Ion Cannon Beacon** — throw it, and six seconds later an orbital gun walks its beam outward in a spiral,
  punching twenty-two overlapping shafts through the landscape over the next three minutes.
- **Chemical Warhead** — no damage to the terrain at all. A nerve agent cloud forty-six blocks across that lingers
  for three minutes and kills everything that breathes in it.

### Tactical Nuke

Placed as a block. Arm it with **flint and steel** or a **redstone signal** — ten seconds of countdown, and breaking
the warhead in that window defuses it.

Then: a 200 block radius, 90 blocks deep at ground zero (well past the deepslate, down to bedrock near the centre),
70 blocks of everything above it swept away, scattered fires, blindness and a long dose of **Radiation** for anything
that survived within 400 blocks, and a mushroom cloud.

The crater is carved outward in rings over roughly half a minute rather than in one frame, so it reads as a
shockwave instead of freezing the server. The carving also stops for the tick after 25 ms, so a slower machine
takes a little longer to finish the crater instead of stuttering (both budgets are at the top of
[`BlastScheduler.java`](src/main/java/com/afjan/arsenal/combat/BlastScheduler.java)).

**Cost:** 6 Plutonium Cores, 8 Enriched Uranium, 4 Warhead Casings, 8 Explosive Compound, 16 Steel, 4 Circuit
Boards, 4 Precision Parts and a Nether Star. A single Plutonium Core is 6 Enriched Uranium (24 Uranium Ingots) plus
a netherite ingot — so the warhead alone is roughly **150 uranium ingots**, and uranium only generates below y 16 at
about diamond rarity.

### F-14 Tomcat

A two-seat swing-wing fighter at 1:1 scale to the player: 19 m long, 19.5 m across with the wings spread, a helmeted
pilot and RIO in the cockpit. Built at the workbench's **AIR** tab from 48 Steel, 12 Circuit Boards, 8 Precision
Parts, 6 Gun Barrels, 2 Rocket Motors, 2 Netherite Ingots, 6 Glass and an Elytra. It comes fully armed.

- **Parking and boarding.** Use the item on open ground and the jet rolls out facing the way you look. Right-click
  it to climb in (a second player takes the back seat). Sneak + right-click with an empty hand packs a parked jet
  back into its item; the loadout, cannon rounds and damage stay on the item.
- **Flying.** The mouse flies the *flight path*: the jet banks and pulls onto wherever you look, like a pilot would,
  never pulling harder than the wings can take (it will not stall itself). It rotates at ~110 km/h and lifts off
  after ~150 blocks at military power (~60 with afterburner), cruises at ~250 km/h (~350 on afterburner). The flaps
  come down with the gear at low speed and the wings sweep themselves from 20° to 68° as it speeds up, like the real
  air data computer. Near the runway the nose is held below the angle that would scrape the tail.
- **Landing.** Gear down, ~130 km/h, a gentle descent. Too hard a touchdown, a wing or the nose down, or the gear up
  at speed wrecks it; a slow belly landing costs a big chunk of the airframe. Flying into terrain at speed or ditching
  in water destroys it in a fireball.
- **Weapons.** The **M61 Vulcan** fires 675 20 mm rounds (5 damage each, 220 blocks, tracers). The pylons carry two
  **AIM-9 Sidewinders** (heat seekers: hold the target in the HUD's seeker circle until the growl turns into the lock
  tone, 0.6 s), two **AIM-54 Phoenix** (radar missiles: twice the reach, a wider cone, a heavier warhead, 1.5 s to
  lock; they drop off the pylon before the motor lights), eight **Zuni rockets** in two LAU-10 pods and two **Mk 82
  bombs** (the HUD circle shows where a bomb released now would land). 36 **flares**, four per press; a Sidewinder
  may chase them instead. A missile locked onto your jet sets off the warning tone and MISSILE on the HUD.
- **HUD.** The one screen overlay the mod draws, a green fighter head-up display like the real thing: gun cross,
  flight path marker, pitch ladder, heading, airspeed (km/h), altitude and height above the ground, climb rate, g,
  angle of attack, throttle, gear/flaps/brakes, the stores with the selected one marked, the seeker circle and lock
  box, the bomb impact point, and flashing STALL / PULL UP / MISSILE / GEAR / FIRE warnings.
- **Rearming and repairs.** Right-click the parked jet with a missile, rocket or bomb (loads one), 20mm Cannon Shells
  (+225 rounds), Flare Cartridges (+12) or a Steel Ingot (repairs 25 of the 250-point airframe).
- **Damage.** Hitboxes cover the nose, cockpit, fuselage, wings (they follow the sweep), tail and fins. It smokes
  below 45% and burns below 20%. Its own crew cannot hurt it; in creative, a sneaking punch removes it.
- **Moving parts.** Wing sweep, flaps and slats, spoilers (roll and lift dump after touchdown), stabilators,
  rudders, both speed brakes, the canopy, gear legs with doors and turning wheels, nozzles opening in afterburner,
  spinning fans, a telescoping boarding ladder, afterburner flames with shock diamonds, the cannon's muzzle flash,
  navigation lights and strobes, wingtip vapour in hard turns and contrails up high.

---

## Materials

**Uranium Ore** generates in the Overworld below y 16 (deepslate variant below y 8), roughly as rare as diamond,
and needs a diamond pickaxe. Everything else is built from steel: Gun Barrels, Weapon Receivers, Trigger
Assemblies, Weapon Stocks, Precision Parts (steel + diamond), Optical Lenses, Laser Modules, Circuit Boards,
Explosive Compound, Rocket Motors and Warhead Casings.

Ammunition is Brass Casings (copper + gold nugget) + Propellant (gunpowder + blaze powder + redstone) + Bullet Tips,
in batches of 6–16 rounds.

---

## Working on the mod

All textures, models, sounds and JSON are **generated** — edit the scripts, never the output:

```bash
py tools/gen_textures.py preview && py tools/gen_data.py && py tools/gen_sounds.py && py tools/check_assets.py
```

- `gun_models.py` — every gun, throwable and launched round (the rockets and the 40mm grenade, which are also what
  flies) is a 3D cuboid model with a hand-painted texture atlas. It also solves the display transforms (first
  person, third person in the crossbow-hold pose, GUI, ground, item frame), writes the model variants an item
  switches between (empty, the railgun's five charge steps, the RPG's second warhead), writes
  `client/GunViewmodels.java` (muzzle positions for flashes and tracers) and, on its own
  (`py tools/gun_models.py [names]`, `railgun@` for every variant), renders review sheets to `build/models_*.png`
  without starting the game.
- `gen_sounds.py` — every sound is synthesised (no recordings): gunshots are built from a pressure pulse, the
  supersonic crack, the muzzle blast, a low body thump, the action cycling, ground reflections, an environment
  tail and slapback echoes. It needs an ffmpeg with libvorbis (found automatically; set `FFMPEG` otherwise) and
  writes `sounds/**.ogg` + `sounds.json`; `preview` adds `build/sounds_preview.png`.
- `vehicle_models.py` — the F-14: lofted fuselage, nacelles, swing wings, tail, canopy, cockpit, crew, gear and
  stores built as quads, painted into a 2048×512 atlas (panel lines, rivets, soot, the VF-84 Jolly Rogers markings)
  and written as `vehicle/f14.mesh` (58 animated parts) plus the item icon. On its own,
  `py tools/vehicle_models.py side top front q1 q2 nose tail under wing cockpit gear` renders review views to
  `build/f14_*.png`. `gen_textures.py` calls it.
- `gen_data.py` deletes and rewrites its output folders, so hand edits to generated JSON are lost.
- `check_assets.py` walks the item ids straight out of the Java enums and fails if any of them is missing a
  texture, model, client item definition or translation, if a translation key, key mapping, texture or mesh named in
  the Java code is missing, or if `ModSounds` and `sounds.json` disagree — the class of bug the game tests cannot
  see.

Before shipping, `runGameTestServer` must print "All … required tests passed":

```bash
./gradlew --no-configuration-cache runGameTestServer build
```

### Layout

- `gun/` — `GunType` (every weapon's statistics), `Caliber`, `Attachment`, `GunData` (the item component)
- `combat/` — `Ballistics` (firing), `Reloading`, `RailCharge` (the railgun's server-timed charge, synced to
  everyone through an attachment), `Ordnance` + `Detonations` (what each bomb does), `BlastScheduler` (large craters
  carved over many ticks, plus the delayed-task queue)
- `craft/Blueprints` — the whole workbench catalogue
- `menu/` + `client/WeaponWorkbenchScreen` — the bench UI, drawn from rectangles with no GUI sheet
- `client/ArsenalClient` — trigger (hold-and-release for the railgun), reload key, recoil, aim-down-sights, ammo HUD;
  it also keeps the off hand out of right-click while a gun is held (`GunItem#use` passes, so vanilla never replays
  the re-equip dip that made held guns bob while right-click was down)
- `client/GunFeel` — viewmodel kick, view punch, muzzle flash, tracers and the charge-scaled railgun beam, the
  two-handed arm pose; the model switches `LoadedProperty` (`arsenal:loaded`), `ChargeProperty` (`arsenal:charge`,
  the railgun's coil compartments) and `RoundProperty` (`arsenal:round`, the RPG's warhead)
- `client/OrdnanceRenderer` — rockets and 40mm grenades as their 3D model turned along the flight path, with the
  rocket motor's flame; `client/ClientSounds` — sounds that follow something (charge whine, rocket roar)
- `registry/ModSounds` — every sound event, plus the near/distant broadcast (far listeners hear a duller shot)
- `vehicle/` — the F-14, common side: `FlightModel` (the physics and the mouse-aim autopilot), `F14Entity` (flight,
  ground contact, terrain checks, animation state, weapons, crew, damage), `F14Part` (hitboxes), `Store` (the
  loadout packed into one int), `JetWeapons` (M61, pylons, flares), `JetControl` (the pilot's packet), `F14Item`
- `client/vehicle/` — `F14Renderer` + `VehicleMesh` (draws and animates the mesh), `JetClient` (controls, seekers,
  camera, hidden hotbar), `JetHud`, `JetFx` (tracers), `JetSounds` (engines with Doppler, afterburner, gun, cockpit
  tones)
- `gametest/` — 31 automated tests (9 for the F-14: take-off, level turn, no self-stall, parking, rearming,
  packing up, the cannon, a Sidewinder kill, hitbox damage)

The jet is flown on the pilot's own client, like a boat: the client runs `FlightModel` every tick and sends the
result (`JetControl`); the server checks it against the terrain and runs the weapons, and every other player sees
it smoothed between updates.

Firing is driven by a packet from the client rather than `Item#use`, because holding an item the vanilla way slows
the player to 20% speed — no way to run an automatic weapon. The server re-checks the rate of fire and the magazine
on every shot, so a spammed packet still cannot fire faster than the weapon allows.
