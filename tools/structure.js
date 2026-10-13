// Builds structure (.nbt) files block by block. Used by the café and rest-spot generators.
const { write, list, T } = require('./nbt.js');
const DATA_VERSION = 5023; // Minecraft 26.3

// 'minecraft:oak_stairs[facing=east,half=bottom]' -> { id, properties }
function parse(state) {
	const m = /^([^\[]+)(?:\[(.*)\])?$/.exec(state);
	const properties = m[2] ? Object.fromEntries(m[2].split(',').map(kv => kv.split('='))) : undefined;
	return properties ? { id: m[1], properties } : { id: m[1] };
}

class Structure {
	constructor(size) {
		this.size = size;
		this.blocks = new Map(); // "x,y,z" -> { state, nbt }
	}

	set(x, y, z, state, nbt) {
		this.blocks.set(`${x},${y},${z}`, { state, nbt });
	}

	/** The gzipped structure file. Positions never set are left as they are in the world. */
	toNbt() {
		const palette = [], index = new Map(), blocks = [];
		for (const [key, { state, nbt }] of this.blocks) {
			if (!index.has(state)) { index.set(state, palette.length); palette.push(parse(state)); }
			const entry = { pos: list(T.INT, key.split(',').map(Number)), state: index.get(state) };
			if (nbt) entry.nbt = nbt;
			blocks.push(entry);
		}
		return write({
			size: list(T.INT, this.size), entities: list(T.COMPOUND, []), blocks: list(T.COMPOUND, blocks),
			palette: list(T.COMPOUND, palette), DataVersion: DATA_VERSION,
		});
	}
}

const stairs = (base, facing, shape = 'straight') => `${base}_stairs[facing=${facing},half=bottom,shape=${shape},waterlogged=false]`;
const fence = (base, sides = []) => `${base}_fence[${['east', 'north', 'south', 'west'].map(d => `${d}=${sides.includes(d)}`).join(',')},waterlogged=false]`;

module.exports = { Structure, parse, stairs, fence };
