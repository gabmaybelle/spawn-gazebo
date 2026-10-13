// Writes the spawn_gazebo_data built-in data pack: the starter chest's loot table, the functions
// that hand the kit to players who arrive after the chest has been claimed, and the gazebo
// structures (generate-spawn.js). Re-run after editing:
//   node tools/generate-data.js
const fs = require('fs'), path = require('path');
const ROOT = path.join(__dirname, '..', 'src', 'main', 'resources', 'resourcepacks', 'spawn_gazebo_data');
const out = (rel, obj) => {
	const f = path.join(ROOT, ...rel.split('/'));
	fs.mkdirSync(path.dirname(f), { recursive: true });
	fs.writeFileSync(f, typeof obj === 'string' ? obj : JSON.stringify(obj, null, 2) + '\n');
};
fs.rmSync(ROOT, { recursive: true, force: true });

out('pack.mcmeta', { pack: { description: 'Spawn Gazebo: the gazebo and its starter chest', min_format: 121, max_format: 121 } });

// The starter kit: what's in the chest in the gazebo's north railing. A bed to sleep in, light,
// a meal and something to plant, with a bundle to carry it.
const KIT = [
	{ id: 'minecraft:white_bed', count: 1 },
	{ id: 'minecraft:torch', count: 16 },
	{ id: 'minecraft:bread', count: 6 },
	{ id: 'minecraft:oak_sapling', count: 4 },
	{ id: 'minecraft:bundle', count: 1 },
];
out('data/spawn_gazebo/loot_table/chests/spawn_kit.json', {
	type: 'minecraft:chest',
	pools: KIT.map(({ id, count }) => ({ rolls: 1, entries: [{ type: 'minecraft:item', name: id, modifier: count > 1 ? [{ type: 'minecraft:set_count', count }] : [] }] })),
});
out('data/spawn_gazebo/function/starter_kit/give.mcfunction', [
	'# A roll of the same loot table the chest was filled from, handed straight to a player. The table is',
	'# the "table" entry of storage spawn_gazebo:spawn (a macro line: run this with that storage).',
	'$loot give @s loot $(table)',
	'',
].join('\n'));
out('data/spawn_gazebo/function/starter_kit.mcfunction', [
	'# Runs once per player, on their first join (advancement spawn_gazebo:starter_kit). SpawnGazebo.java',
	'# sets storage spawn_gazebo:spawn {chest:1b, table:"..."} when it builds the gazebo with its chest. The',
	'# first player gets the chest; everyone after them is handed a roll of the same loot, unless Lootr is',
	'# giving each player their own copy of the chest ({lootr:1b}). Without the chest, or in a world from',
	'# before the mod, nothing is given.',
	'execute if data storage spawn_gazebo:spawn {chest:1b} unless data storage spawn_gazebo:spawn {lootr:1b} if data storage spawn_gazebo:spawn {claimed:1b} run function spawn_gazebo:starter_kit/give with storage spawn_gazebo:spawn',
	'execute if data storage spawn_gazebo:spawn {chest:1b} run data merge storage spawn_gazebo:spawn {claimed:1b}',
	'',
].join('\n'));
out('data/spawn_gazebo/advancement/starter_kit.json', {
	criteria: { joined: { trigger: 'minecraft:tick' } },
	requirements: [['joined']],
	rewards: { function: 'spawn_gazebo:starter_kit' },
});

require('./generate-spawn.js');
console.log('spawn_gazebo_data written to ' + ROOT);
