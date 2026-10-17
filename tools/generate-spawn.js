// Writes the spawn gazebo: a little round stone pavilion that SpawnGazebo.java builds at the world
// spawn of every new world. A round stepped plinth, eight stripped-log pillars on stone bases
// carrying a ring of arches and a carved band, and a weathered copper dome stepping up to a
// lightning rod; inside, a coffered ceiling rises to the spore blossom over a glowstone floor,
// with lanterns under the eaves and by the steps. One file per wood (the mod picks the wood
// growing around spawn) and per palette (grey mossy stone with azaleas creeping over the dome, or
// sandstone in dry biomes).
// Run by generate-data.js (which clears the pack first).
const fs = require('fs'), path = require('path');
const { Structure, stairs, fence } = require('./structure.js');
const DATA = path.join(__dirname, '..', 'common', 'src', 'main', 'resources', 'resourcepacks', 'spawn_gazebo_data', 'data');
const out = (rel, buf) => {
	const f = path.join(DATA, ...rel.split('/'));
	fs.mkdirSync(path.dirname(f), { recursive: true });
	fs.writeFileSync(f, buf);
};

// Every overworld wood with a fence and stairs. SpawnGazebo.WOODS lists the same names.
const WOODS = ['oak', 'spruce', 'birch', 'jungle', 'acacia', 'dark_oak', 'mangrove', 'cherry', 'pale_oak', 'bamboo', 'poplar'];

const PALETTES = {
	// Grey stone with moss creeping in, a green copper dome with azaleas growing over it, grass and
	// pink flowers round the edge.
	stone: {
		apron: 'minecraft:cobblestone', apronWorn: 'minecraft:mossy_cobblestone',
		plinth: 'minecraft:stone_bricks', plinthWorn: 'minecraft:mossy_stone_bricks', plinthAccent: 'minecraft:tuff_bricks',
		stair: 'minecraft:stone_brick', stairWorn: 'minecraft:mossy_stone_brick',
		ceiling: 'minecraft:tuff_bricks', ceilingCentre: 'minecraft:chiseled_tuff_bricks', fill: 'minecraft:stone_bricks',
		floorBorder: 'minecraft:tuff_bricks', floorSide: 'minecraft:chiseled_tuff', floorCorner: 'minecraft:polished_tuff',
		cap: 'minecraft:stone_brick_slab', rail: 'minecraft:tuff_brick_wall', footing: 'minecraft:stone_brick_wall',
		dome: ['waxed_oxidized', 'waxed_oxidized', 'waxed_weathered'],
		leaves: ['minecraft:azalea_leaves[distance=7,persistent=true,waterlogged=false]', 'minecraft:flowering_azalea_leaves[distance=7,persistent=true,waterlogged=false]', 'minecraft:moss_block'],
		ground: 'minecraft:grass_block[snowy=false]', path: 'minecraft:dirt_path',
		moss: 'minecraft:moss_block', carpet: 'minecraft:moss_carpet',
		corners: 'minecraft:flowering_azalea', sides: 'minecraft:pink_petals[facing=north,flower_amount=3]',
		front: 'minecraft:pink_tulip', back: 'minecraft:allium',
	},
	// Sandstone for deserts and badlands: a browner copper dome, no moss, dead bushes and cactus
	// flowers instead of flowers.
	sand: {
		apron: 'minecraft:sandstone', apronWorn: 'minecraft:cut_sandstone',
		plinth: 'minecraft:smooth_sandstone', plinthWorn: 'minecraft:sandstone', plinthAccent: 'minecraft:chiseled_sandstone',
		stair: 'minecraft:smooth_sandstone', stairWorn: 'minecraft:sandstone',
		ceiling: 'minecraft:cut_sandstone', ceilingCentre: 'minecraft:chiseled_sandstone', fill: 'minecraft:sandstone',
		floorBorder: 'minecraft:cut_sandstone', floorSide: 'minecraft:chiseled_sandstone', floorCorner: 'minecraft:smooth_sandstone',
		cap: 'minecraft:smooth_sandstone_slab', rail: 'minecraft:sandstone_wall', footing: 'minecraft:sandstone_wall',
		dome: ['waxed_weathered', 'waxed_weathered', 'waxed_exposed'], leaves: null,
		ground: 'minecraft:sand', path: 'minecraft:sand',
		moss: null,
		corners: 'minecraft:dead_bush', sides: 'minecraft:cactus_flower', front: 'minecraft:dead_bush', back: 'minecraft:cactus_flower',
	},
};

// A fixed little random so every wood gets the same worn blocks in the same places.
function rng(seed) {
	let s = seed >>> 0;
	return () => { s = (s * 1664525 + 1013904223) >>> 0; return s / 4294967296; };
}

const wall = (base, up = false) => `${base}[east=none,north=none,south=none,up=${up},waterlogged=false,west=none]`;
const lantern = (hanging) => `minecraft:lantern[hanging=${hanging},waterlogged=false]`;

// Template: 15 x 15 x 15, the gazebo centred on (7, y, 7). y=0 is the ground surface; the paving
// replaces the top layer of ground and the plinth stands two blocks proud of it. The entrance faces
// south (+z); the mod rotates the whole thing at random. Everything is laid out in rings round the
// middle: a cell is in the disc of "radius" r when dx² + dz² <= r, so 2, 5, 10, 20, 30 and 42
// give the usual 3, 5, 7, 9, 11 and 13 block circles.
function gazebo(wood, p) {
	const g = new Structure([15, 15, 15]);
	const set = (x, y, z, s, nbt) => g.set(x, y, z, s, nbt);
	const rand = rng(20261011);
	const pick = (list) => list[Math.floor(rand() * list.length)];
	const worn = (chance, a, b) => (p.apronWorn !== p.apron || p.plinthWorn !== p.plinth) && rand() < chance ? b : a;
	const w = `minecraft:${wood}`;
	// Bamboo has no log; its stripped block stands in.
	const log = `minecraft:${wood === 'bamboo' ? 'stripped_bamboo_block' : `stripped_${wood}_log`}[axis=y]`;
	const C = 7;
	const d2 = (dx, dz) => dx * dx + dz * dz;
	// Every cell of the template with its offset from the middle, in a fixed order.
	const cells = (fn) => { for (let dz = -7; dz <= 7; dz++) for (let dx = -7; dx <= 7; dx++) fn(dx, dz, C + dx, C + dz); };
	const inRing = (dx, dz, outer, inner = -1) => d2(dx, dz) <= outer && d2(dx, dz) > inner;
	// A stair on a ring facing the middle: along the axis the cell is furthest out on, and an outer
	// corner (keeping the quarter that points to the middle) where it's as far out on both.
	const facingIn = (dx, dz) => Math.abs(dz) > Math.abs(dx) ? (dz > 0 ? 'north' : 'south') : (dx > 0 ? 'west' : 'east');
	const shapeIn = (dx, dz) => Math.abs(dx) !== Math.abs(dz) ? 'straight' : (dx > 0) === (dz > 0) ? 'outer_right' : 'outer_left';
	const ringStair = (base, dx, dz, half) => `${base}_stairs[facing=${facingIn(dx, dz)},half=${half},shape=${shapeIn(dx, dz)},waterlogged=false]`;

	// Paving at ground level: a 13-circle of cobbles, mossier towards the edge, showing as a ring a
	// block wide round the foot of the plinth.
	cells((dx, dz, x, z) => { if (inRing(dx, dz, 42)) set(x, 0, z, worn(inRing(dx, dz, 42, 30) ? 0.45 : 0.2, p.apron, p.apronWorn)); });
	// The plinth, an 11-circle two blocks high with the odd mossy or accent block. Inside the
	// colonnade its top is the floor: rings of chiseled and polished stone round a glowstone under
	// the spore blossom.
	cells((dx, dz, x, z) => {
		if (!inRing(dx, dz, 30)) return;
		for (const y of [1, 2]) {
			const r = rand();
			set(x, y, z, r < 0.12 ? p.plinthWorn : r < 0.2 ? p.plinthAccent : p.plinth);
		}
		if (inRing(dx, dz, 20)) set(x, 2, z, dx === 0 && dz === 0 ? 'minecraft:glowstone' : inRing(dx, dz, 2) ? p.floorSide : inRing(dx, dz, 5) ? p.floorCorner : p.floorBorder);
	});
	// Two steps up at the entrance, three wide, and a short path out from them.
	for (const x of [6, 7, 8]) {
		set(x, 1, 13, stairs(p.stair, 'north'));
		set(x, 2, 12, stairs(p.stair, 'north'));
		set(x, 0, 14, p.path);
	}

	// The colonnade stands on the rim of the plinth: eight pillars of the gazebo's wood on stone
	// bases with stone capitals, two either side of each of the four openings, which are three
	// wide; between each pair of openings a narrower gap on the diagonal. The south opening is the
	// entrance; the others have a low wall, and the chest sits in the north one. Over every
	// opening the capitals carry an arch (upside-down stairs springing from the pillars, a slab
	// over the middle), and over that runs a band of stone with a carved block above each pillar.
	const pillar = (dx, dz) => Math.abs(dx) === 5 && Math.abs(dz) === 2 || Math.abs(dx) === 2 && Math.abs(dz) === 5;
	const entrance = (dx, dz) => dz === 5 && Math.abs(dx) <= 1;
	const chest = (dx, dz) => dx === 0 && dz === -5;
	cells((dx, dz, x, z) => {
		if (!inRing(dx, dz, 30, 20)) return;
		if (pillar(dx, dz)) {
			set(x, 2, z, p.plinthAccent);
			for (let y = 3; y <= 5; y++) set(x, y, z, log);
			set(x, 6, z, p.plinthAccent);
		} else if (entrance(dx, dz)) {
			// Open to the floor; the upper step is set here.
		} else if (chest(dx, dz)) {
			// The starter kit, in the railing opposite the entrance. SpawnGazebo.java knows this spot.
			set(x, 3, z, 'minecraft:chest[facing=south,type=single,waterlogged=false]', { LootTable: 'spawn_gazebo:chests/spawn_kit' });
		} else {
			set(x, 3, z, wall(p.rail));
		}
		// The arch: over the cell next to a pillar the stair's solid half is on the pillar's side,
		// over the middle of a wide opening a slab, and over a diagonal gap each stair leans
		// towards the pillar it's nearest.
		if (!pillar(dx, dz)) {
			const beside = [['east', 1, 0], ['west', -1, 0], ['south', 0, 1], ['north', 0, -1]].find(([, ex, ez]) => pillar(dx + ex, dz + ez));
			const towards = beside ? beside[0] : Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? 'east' : 'west') : (dz > 0 ? 'south' : 'north');
			set(x, 6, z, dx === 0 || dz === 0 ? `${p.cap}[type=top,waterlogged=false]` : `${p.stair}_stairs[facing=${towards},half=top,shape=straight,waterlogged=false]`);
		}
		set(x, 7, z, pillar(dx, dz) ? p.plinthAccent : p.plinth);
	});

	// Pots, for a builder's first garden. Flower pots stand on the railing in the middle of the
	// east and west openings and on the paving by the steps, with saplings of woods that don't
	// grow everywhere (never the gazebo's own) and a cactus; take a plant out with an empty hand.
	// Inside, a decorated pot in each corner holds mangrove propagules, bamboo or seeds (break it
	// to get them).
	const potted = ['jungle', 'spruce'].filter(x => x !== wood).map(x => `minecraft:potted_${x}_sapling`)
		.concat('minecraft:potted_cactus', 'minecraft:potted_azalea_bush', 'minecraft:potted_flowering_azalea_bush');
	const [railL, railR, stepL, stepR] = potted;
	set(C - 5, 4, C, railL);
	set(C + 5, 4, C, railR);
	set(C - 3, 1, C + 5, stepL);
	set(C + 3, 1, C + 5, stepR);
	// 26.3 keeps a pot's sherds by side; a side left out is plain brick.
	const pot = (front, id, count) => ({ sherds: { front: { id: `minecraft:${front}_pottery_sherd` } }, item: { id: `minecraft:${id}`, count } });
	const decorated = (x, z, facing, contents) => set(x, 3, z, `minecraft:decorated_pot[cracked=false,facing=${facing},waterlogged=false]`, contents);
	decorated(C - 3, C - 3, 'south', pot('plenty', 'mangrove_propagule', 2));
	decorated(C + 3, C - 3, 'south', pot('heart', 'bamboo', 4));
	// Two different seeds by the entrance, picked by wood so worlds differ.
	const seeds = [['torchflower_seeds', 2], ['pitcher_pod', 1], ['pumpkin_seeds', 4], ['melon_seeds', 4], ['beetroot_seeds', 4], ['cocoa_beans', 3]];
	const first = WOODS.indexOf(wood) % seeds.length;
	decorated(C - 3, C + 3, 'east', pot('sheaf', ...seeds[first]));
	decorated(C + 3, C + 3, 'west', pot('snort', ...seeds[(first + 3) % seeds.length]));

	// Two benches facing each other across the middle, backs to the east and west railings.
	for (const z of [C - 1, C, C + 1]) {
		set(C - 4, 3, z, stairs(w, 'west'));
		set(C + 4, 3, z, stairs(w, 'east'));
	}

	// The dome, in copper that has gone green. It stands on the band with a vertical course over
	// the eaves, then each course of stairs is a circle smaller and a block higher, and the top
	// flattens off in slabs to the lightning rod, so the profile curves rather than coming to a
	// point. Under its edge a ring of upside-down stairs makes the eaves, a block out past the
	// band. Inside it's hollow in steps, each ring of brackets a circle smaller than the one below,
	// up to the ceiling the spore blossom hangs from, so from the floor you look up into a coffered
	// dome. The rest is filled with stone where nothing can see it.
	const copper = () => `minecraft:${pick(p.dome)}_cut_copper`;
	const slab = (base) => `${base}_slab[type=bottom,waterlogged=false]`;
	const leafy = () => p.leaves && rand() < 0.1 ? pick(p.leaves) : null;
	cells((dx, dz, x, z) => {
		if (inRing(dx, dz, 42, 30)) set(x, 7, z, ringStair(worn(0.2, p.stair, p.stairWorn), dx, dz, 'top'));
		if (inRing(dx, dz, 20, 10)) set(x, 7, z, ringStair(p.stair, dx, dz, 'top'));
		if (inRing(dx, dz, 10, 5)) set(x, 8, z, ringStair(p.stair, dx, dz, 'top'));
		if (inRing(dx, dz, 30, 10)) set(x, 8, z, p.fill);
		if (inRing(dx, dz, 30, 5)) set(x, 9, z, p.fill);
		if (inRing(dx, dz, 5)) set(x, 9, z, dx === 0 && dz === 0 ? p.ceilingCentre : p.ceiling);
		if (inRing(dx, dz, 20)) set(x, 10, z, p.fill);
		if (inRing(dx, dz, 10)) set(x, 11, z, p.fill);
		if (inRing(dx, dz, 2)) set(x, 12, z, p.fill);
		if (inRing(dx, dz, 42, 30)) set(x, 8, z, leafy() || copper());
		for (const [y, outer, inner] of [[9, 42, 30], [10, 30, 20], [11, 20, 10]]) {
			if (inRing(dx, dz, outer, inner)) set(x, y, z, leafy() || ringStair(copper(), dx, dz, 'bottom'));
		}
		if (inRing(dx, dz, 10, 5)) set(x, 12, z, slab(copper()));
		if (inRing(dx, dz, 5, 2)) set(x, 12, z, copper());
		if (inRing(dx, dz, 2, 0)) set(x, 13, z, slab(copper()));
		if (dx === 0 && dz === 0) set(x, 13, z, copper());
	});
	set(C, 14, C, 'minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]');

	// From the ceiling: the spore blossom in the middle and a sprig of glow berries in two opposite
	// corners. With the glowstone in the floor that's all the light the inside needs.
	set(C, 8, C, 'minecraft:spore_blossom');
	for (const [x, z] of [[C - 1, C - 1], [C + 1, C + 1]]) set(x, 8, z, 'minecraft:cave_vines[age=25,berries=true]');
	// A lantern on an iron chain (26.3's name for the chain) under each diagonal of the eaves, and one on a post either side of the
	// steps, lighting the ground round about.
	for (const [x, z] of [[C - 4, C - 4], [C + 4, C - 4], [C - 4, C + 4], [C + 4, C + 4]]) {
		set(x, 6, z, 'minecraft:iron_chain[axis=y,waterlogged=false]');
		set(x, 5, z, lantern(true));
	}
	for (const x of [C - 2, C + 2]) {
		set(x, 1, 13, wall(p.footing, true));
		set(x, 2, 13, lantern(false));
	}

	// Moss and mossy stone worked into the ground round the paving, thick by it and thinning out,
	// and vines creeping up the plinth here and there, so the gazebo looks like it has sat there a
	// while. The path, the pots by the steps and the planting are left alone.
	if (p.moss) cells((dx, dz, x, z) => {
		if (inRing(dx, dz, 42) || (Math.abs(dx) <= 1 && dz >= 6)) return;
		if (rand() > (inRing(dx, dz, 56) ? 0.7 : 0.3)) return;
		const r = rand();
		if (r < 0.55) {
			set(x, 0, z, p.moss);
			if (rand() < 0.4) set(x, 1, z, p.carpet);
		} else set(x, 0, z, r < 0.8 ? p.plinthWorn : p.apronWorn);
	});
	if (p.moss) for (const [dx, dz, side] of [[-6, 1, 'east'], [6, -1, 'west'], [1, -6, 'south'], [-3, -5, 'east'], [5, 3, 'north']]) {
		set(C + dx, 1, C + dz, `minecraft:vine[${['east', 'north', 'south', 'up', 'west'].map(d => `${d}=${d === side}`).join(',')}]`);
	}

	// Planting round the outside, on ground the template lays itself so it always takes.
	const plant = (x, z, s) => { set(x, 0, z, p.ground); set(x, 1, z, s); };
	for (const [x, z] of [[2, 2], [2, 12], [12, 2], [12, 12]]) plant(x, z, p.corners);
	for (const [x, z] of [[0, C], [14, C], [C, 0]]) plant(x, z, p.sides);
	for (const x of [3, 11]) plant(x, 0, p.front);
	for (const x of [3, 11]) plant(x, 14, p.back);
	return g.toNbt();
}

for (const [palette, p] of Object.entries(PALETTES)) {
	for (const wood of WOODS) out(`spawn_gazebo/structure/spawn/gazebo_${palette}_${wood}.nbt`, gazebo(wood, p));
}
