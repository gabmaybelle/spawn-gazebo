// Renders the gazebo with Minecraft's own block textures, the way the game draws blocks in the
// inventory (top face lit, south face in half-shadow, east face darker), on a transparent
// background. Used for the mod icon and the picture in the README.
//
//   node tools/render-gazebo.js                      the icon: stone + oak, 512 px, to assets/spawn_gazebo/icon.png
//   node tools/render-gazebo.js --docs               three gazebos side by side, to docs/gazebo.png
//   node tools/render-gazebo.js --wood spruce --palette sand --size 1024 --out some.png
//   --jar <client jar>  where to read the textures from (default: the Modrinth app's 26.3 client jar)
//
// The templates come from generate-spawn.js (run generate-data.js first). Blocks are drawn as
// boxes and crossed planes, close enough to the game's models for a picture this size.
const fs = require('fs'), path = require('path'), zlib = require('zlib');
const nbt = require('./nbt.js');

const args = process.argv.slice(2);
const opt = (n, d) => { const i = args.indexOf('--' + n); return i >= 0 ? args[i + 1] : d; };
const flag = (n) => args.includes('--' + n);
const ROOT = path.join(__dirname, '..');
const STRUCTURES = path.join(ROOT, 'src', 'main', 'resources', 'resourcepacks', 'spawn_gazebo_data', 'data', 'spawn_gazebo', 'structure', 'spawn');
const JAR = opt('jar', path.join(process.env.APPDATA || '', 'ModrinthApp', 'meta', 'versions', '26.3-0.19.5', '26.3-0.19.5.jar'));

// ---- A reader for the entries of a zip (the client jar) ----
function openZip(file) {
	const b = fs.readFileSync(file);
	let eocd = b.length - 22;
	while (eocd >= 0 && b.readUInt32LE(eocd) !== 0x06054b50) eocd--;
	if (eocd < 0) throw new Error('not a zip: ' + file);
	const count = b.readUInt16LE(eocd + 10);
	let o = b.readUInt32LE(eocd + 16);
	const entries = new Map();
	for (let i = 0; i < count; i++) {
		if (b.readUInt32LE(o) !== 0x02014b50) throw new Error('bad central directory');
		const method = b.readUInt16LE(o + 10), csize = b.readUInt32LE(o + 20), usize = b.readUInt32LE(o + 24);
		const nlen = b.readUInt16LE(o + 28), elen = b.readUInt16LE(o + 30), clen = b.readUInt16LE(o + 32);
		const name = b.toString('utf8', o + 46, o + 46 + nlen);
		entries.set(name, { method, csize, usize, offset: b.readUInt32LE(o + 42) });
		o += 46 + nlen + elen + clen;
	}
	return {
		has: (name) => entries.has(name),
		read(name) {
			const e = entries.get(name);
			if (!e) throw new Error('not in jar: ' + name);
			const h = e.offset;
			const start = h + 30 + b.readUInt16LE(h + 26) + b.readUInt16LE(h + 28);
			const data = b.subarray(start, start + e.csize);
			return e.method === 0 ? Buffer.from(data) : zlib.inflateRawSync(data);
		},
	};
}

// ---- PNG in (any colour type, 1-8 bits, no interlace) and out (RGBA) ----
function decodePng(buf) {
	let o = 8, w, h, depth, type, plte, trns;
	const idat = [];
	while (o < buf.length) {
		const len = buf.readUInt32BE(o), kind = buf.toString('ascii', o + 4, o + 8), d = buf.subarray(o + 8, o + 8 + len);
		if (kind === 'IHDR') { w = d.readUInt32BE(0); h = d.readUInt32BE(4); depth = d[8]; type = d[9]; if (d[12]) throw new Error('interlaced png'); }
		else if (kind === 'PLTE') plte = d;
		else if (kind === 'tRNS') trns = d;
		else if (kind === 'IDAT') idat.push(d);
		o += 12 + len;
	}
	const raw = zlib.inflateSync(Buffer.concat(idat));
	const channels = { 0: 1, 2: 3, 3: 1, 4: 2, 6: 4 }[type];
	const bpp = Math.max(1, (channels * depth) >> 3), stride = Math.ceil(w * channels * depth / 8);
	const lines = Buffer.alloc(stride * h);
	let prev = Buffer.alloc(stride);
	for (let y = 0, p = 0; y < h; y++) {
		const f = raw[p++], line = lines.subarray(y * stride, (y + 1) * stride);
		raw.copy(line, 0, p, p + stride); p += stride;
		for (let i = 0; i < stride; i++) {
			const a = i >= bpp ? line[i - bpp] : 0, b = prev[i], c = i >= bpp ? prev[i - bpp] : 0;
			if (f === 1) line[i] += a; else if (f === 2) line[i] += b; else if (f === 3) line[i] += (a + b) >> 1;
			else if (f === 4) { const pp = a + b - c, pa = Math.abs(pp - a), pb = Math.abs(pp - b), pc = Math.abs(pp - c); line[i] += pa <= pb && pa <= pc ? a : pb <= pc ? b : c; }
		}
		prev = line;
	}
	const out = new Uint8Array(w * h * 4);
	const sample = (line, i) => depth === 8 ? line[i] : depth === 16 ? line[i * 2] : (line[(i * depth) >> 3] >> (8 - depth - ((i * depth) & 7))) & ((1 << depth) - 1);
	for (let y = 0; y < h; y++) {
		const line = lines.subarray(y * stride);
		for (let x = 0; x < w; x++) {
			const q = (y * w + x) * 4;
			if (type === 3) {
				const i = sample(line, x);
				out[q] = plte[i * 3]; out[q + 1] = plte[i * 3 + 1]; out[q + 2] = plte[i * 3 + 2]; out[q + 3] = trns && i < trns.length ? trns[i] : 255;
			} else if (type === 0 || type === 4) {
				const raw = sample(line, x * channels), g = raw * (255 / ((1 << depth) - 1));
				out[q] = out[q + 1] = out[q + 2] = g;
				// A greyscale image can name one shade as transparent (tRNS).
				out[q + 3] = type === 4 ? sample(line, x * channels + 1) : trns && trns.readUInt16BE(0) === raw ? 0 : 255;
			} else {
				for (let c = 0; c < channels; c++) out[q + c] = sample(line, x * channels + c);
				if (channels === 3) out[q + 3] = trns && trns.readUInt16BE(0) === out[q] && trns.readUInt16BE(2) === out[q + 1] && trns.readUInt16BE(4) === out[q + 2] ? 0 : 255;
			}
		}
	}
	return { w, h, data: out };
}

const CRC = []; for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; CRC[n] = c >>> 0; }
function encodePng(w, h, rgba) {
	const crc = (b) => { let x = 0xffffffff; for (const v of b) x = CRC[(x ^ v) & 255] ^ (x >>> 8); return (x ^ 0xffffffff) >>> 0; };
	const chunk = (t, d) => { const l = Buffer.alloc(4); l.writeUInt32BE(d.length); const td = Buffer.concat([Buffer.from(t), d]); const c = Buffer.alloc(4); c.writeUInt32BE(crc(td)); return Buffer.concat([l, td, c]); };
	const rows = Buffer.alloc((w * 4 + 1) * h);
	for (let y = 0; y < h; y++) { rows[y * (w * 4 + 1)] = 0; Buffer.from(rgba.buffer, rgba.byteOffset + y * w * 4, w * 4).copy(rows, y * (w * 4 + 1) + 1); }
	const ih = Buffer.alloc(13); ih.writeUInt32BE(w, 0); ih.writeUInt32BE(h, 4); ih[8] = 8; ih[9] = 6;
	return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ih), chunk('IDAT', zlib.deflateSync(rows, { level: 9 })), chunk('IEND', Buffer.alloc(0))]);
}

// ---- Textures ----
const jar = openZip(JAR);
const textures = new Map();
const GRASS = [0x91, 0xbd, 0x59], FOLIAGE = [0x77, 0xab, 0x2f];
const TINTS = { grass_block_top: GRASS, grass_block_side_overlay: GRASS, vine: FOLIAGE, pink_petals_stem: FOLIAGE };
const MISSING = { w: 16, h: 16, data: new Uint8Array(16 * 16 * 4).map((_, i) => (i & 3) === 3 ? 255 : ((i >> 2) % 16 < 8) !== (Math.floor((i >> 2) / 16) < 8) ? (i & 3) === 1 ? 0 : 255 : 0) };
const warned = new Set();
function texture(name) {
	if (textures.has(name)) return textures.get(name);
	const entry = name.includes('/') ? `assets/minecraft/textures/${name}.png` : `assets/minecraft/textures/block/${name}.png`;
	let t;
	if (jar.has(entry)) {
		t = decodePng(jar.read(entry));
		// Animated textures are strips of frames; the first frame will do.
		if (t.h > t.w) t = { w: t.w, h: t.w, data: t.data.subarray(0, t.w * t.w * 4) };
		const tint = TINTS[name.split('/').pop()];
		if (tint) for (let i = 0; i < t.data.length; i += 4) { t.data[i] = t.data[i] * tint[0] / 255; t.data[i + 1] = t.data[i + 1] * tint[1] / 255; t.data[i + 2] = t.data[i + 2] * tint[2] / 255; }
	} else {
		if (!warned.has(name)) { warned.add(name); console.warn('no texture ' + name); }
		t = MISSING;
	}
	textures.set(name, t);
	return t;
}

// ---- Block models: boxes and crossed planes, in sixteenths of a block ----
// A face is { tex, uv: [u0, v0, u1, v1] } (uv in texture pixels, optional); faces: up, down, north, south, east, west.
const sameAll = (tex) => ({ up: { tex }, down: { tex }, north: { tex }, south: { tex }, east: { tex }, west: { tex } });
const topSide = (top, side, bottom = top) => ({ up: { tex: top }, down: { tex: bottom }, north: { tex: side }, south: { tex: side }, east: { tex: side }, west: { tex: side } });
const box = (x0, y0, z0, x1, y1, z1, faces) => ({ kind: 'box', x0, y0, z0, x1, y1, z1, faces: typeof faces === 'string' ? sameAll(faces) : faces });
const cross = (tex, a = 0, b = 16, y0 = 0, y1 = 16) => ({ kind: 'cross', tex, a, b, y0, y1 });
const flat = (tex, y, a = 0, b = 16) => ({ kind: 'flat', tex, y, a, b });
const wallQuad = (tex, side) => ({ kind: 'wall', tex, side });
const cube = (faces) => box(0, 0, 0, 16, 16, 16, faces);
const WOODS = ['oak', 'spruce', 'birch', 'jungle', 'acacia', 'dark_oak', 'mangrove', 'cherry', 'pale_oak', 'bamboo', 'poplar'];

// What a stair, slab or wall is made of, by the part of its name before _stairs / _slab / _wall.
function baseFaces(base) {
	base = base.replace(/^waxed_/, '');
	if (base === 'smooth_sandstone') return sameAll('sandstone_top');
	if (base === 'sandstone') return topSide('sandstone_top', 'sandstone', 'sandstone_bottom');
	if (base === 'cut_sandstone' || base === 'chiseled_sandstone') return topSide('sandstone_top', base, 'sandstone_bottom');
	if (WOODS.includes(base)) return sameAll(base + '_planks');
	if (/_brick$/.test(base)) return sameAll(base + 's');
	return sameAll(base);
}

function stairs(p, faces) {
	const top = p.half === 'top';
	const parts = [top ? box(0, 8, 0, 16, 16, 16, faces) : box(0, 0, 0, 16, 8, 16, faces)];
	const [sy0, sy1] = top ? [0, 8] : [8, 16];
	// The raised step: the half of the footprint on the facing side; for a corner, a quarter
	// (outer) or three quarters (inner), the odd quarter on the right-hand side.
	const halves = { north: [0, 0, 16, 8], south: [0, 8, 16, 16], west: [0, 0, 8, 16], east: [8, 0, 16, 16] };
	const right = { north: 'east', east: 'south', south: 'west', west: 'north' }, left = { north: 'west', east: 'north', south: 'east', west: 'south' };
	const quarter = (f, s) => { const [x0, z0, x1, z1] = halves[f], [a0, b0, a1, b1] = halves[s]; return [Math.max(x0, a0), Math.max(z0, b0), Math.min(x1, a1), Math.min(z1, b1)]; };
	const shape = p.shape || 'straight', f = p.facing;
	let rects;
	if (shape === 'straight') rects = [halves[f]];
	else if (shape === 'outer_right') rects = [quarter(f, right[f])];
	else if (shape === 'outer_left') rects = [quarter(f, left[f])];
	else if (shape === 'inner_right') rects = [halves[f], quarter({ north: 'south', south: 'north', east: 'west', west: 'east' }[f], right[f])];
	else rects = [halves[f], quarter({ north: 'south', south: 'north', east: 'west', west: 'east' }[f], left[f])];
	for (const [x0, z0, x1, z1] of rects) parts.push(box(x0, sy0, z0, x1, sy1, z1, faces));
	return parts;
}

function model(id, p) {
	const name = id.replace('minecraft:', '');
	let m;
	if ((m = /^(.*)_stairs$/.exec(name))) return stairs(p, baseFaces(m[1]));
	if ((m = /^(.*)_slab$/.exec(name))) return [p.type === 'double' ? cube(baseFaces(m[1])) : p.type === 'top' ? box(0, 8, 0, 16, 16, 16, baseFaces(m[1])) : box(0, 0, 0, 16, 8, 16, baseFaces(m[1]))];
	if ((m = /^(.*)_wall$/.exec(name))) {
		const faces = baseFaces(m[1]), parts = [box(4, 0, 4, 12, 16, 12, faces)];
		for (const [side, b] of [['north', [5, 0, 0, 11, 14, 4]], ['south', [5, 0, 12, 11, 14, 16]], ['west', [0, 0, 5, 4, 14, 11]], ['east', [12, 0, 5, 16, 14, 11]]]) {
			if (p[side] && p[side] !== 'none') parts.push(box(...b, faces));
		}
		return parts;
	}
	if ((m = /^stripped_(.*)_log$/.exec(name))) return [cube(topSide(`stripped_${m[1]}_log_top`, `stripped_${m[1]}_log`))];
	if (name === 'stripped_bamboo_block') return [cube(topSide('stripped_bamboo_block_top', 'stripped_bamboo_block'))];
	if ((m = /^potted_(.*)$/.exec(name))) {
		const pot = box(5, 0, 5, 11, 6, 11, { up: { tex: 'dirt' }, north: { tex: 'flower_pot', uv: [5, 10, 11, 16] }, south: { tex: 'flower_pot', uv: [5, 10, 11, 16] }, east: { tex: 'flower_pot', uv: [5, 10, 11, 16] }, west: { tex: 'flower_pot', uv: [5, 10, 11, 16] } });
		if (m[1] === 'cactus') return [pot, box(6, 6, 6, 10, 16, 10, topSide('cactus_top', 'cactus_side'))];
		if (m[1].endsWith('azalea_bush')) return [pot, box(4, 6, 4, 12, 14, 12, topSide(`potted_${m[1]}_top`, `potted_${m[1]}_side`))];
		return [pot, cross(m[1], 4, 12, 6, 14)];
	}
	switch (name) {
		case 'grass_block': return [cube(topSide('grass_block_top', 'grass_block_side', 'dirt')), cube({ north: { tex: 'grass_block_side_overlay' }, south: { tex: 'grass_block_side_overlay' }, east: { tex: 'grass_block_side_overlay' }, west: { tex: 'grass_block_side_overlay' } })];
		case 'dirt_path': return [box(0, 0, 0, 16, 15, 16, topSide('dirt_path_top', 'dirt_path_side', 'dirt'))];
		case 'sand': case 'dirt': case 'glowstone': case 'moss_block': case 'cobblestone': case 'mossy_cobblestone': case 'stone_bricks': case 'mossy_stone_bricks':
		case 'tuff_bricks': case 'chiseled_tuff_bricks': case 'polished_tuff': case 'azalea_leaves': case 'flowering_azalea_leaves':
			return [cube(sameAll(name))];
		case 'chiseled_tuff': return [cube(topSide('chiseled_tuff_top', 'chiseled_tuff'))];
		case 'sandstone': case 'cut_sandstone': case 'chiseled_sandstone': case 'smooth_sandstone': return [cube(baseFaces(name))];
		case 'waxed_oxidized_cut_copper': case 'waxed_weathered_cut_copper': case 'waxed_exposed_cut_copper': case 'waxed_cut_copper': return [cube(baseFaces(name))];
		case 'moss_carpet': return [box(0, 0, 0, 16, 1, 16, 'moss_block')];
		case 'chest': return [box(1, 0, 1, 15, 10, 15, { up: { tex: 'entity/chest/normal', uv: [28, 19, 42, 33] }, north: { tex: 'entity/chest/normal', uv: [14, 33, 28, 43] }, south: { tex: 'entity/chest/normal', uv: [42, 33, 56, 43] }, east: { tex: 'entity/chest/normal', uv: [28, 33, 42, 43] }, west: { tex: 'entity/chest/normal', uv: [0, 33, 14, 43] } }),
			box(1, 10, 1, 15, 14, 15, { up: { tex: 'entity/chest/normal', uv: [28, 0, 42, 14] }, north: { tex: 'entity/chest/normal', uv: [14, 14, 28, 19] }, south: { tex: 'entity/chest/normal', uv: [42, 14, 56, 19] }, east: { tex: 'entity/chest/normal', uv: [28, 14, 42, 19] }, west: { tex: 'entity/chest/normal', uv: [0, 14, 14, 19] } })];
		case 'decorated_pot': return [box(1, 0, 1, 15, 16, 15, 'entity/decorated_pot/decorated_pot_side')];
		case 'lantern': { const y = p.hanging === 'true' ? 1 : 0; return [box(5, y, 5, 11, y + 7, 11, { up: { tex: 'lantern', uv: [0, 9, 6, 15] }, north: { tex: 'lantern', uv: [0, 2, 6, 9] }, south: { tex: 'lantern', uv: [0, 2, 6, 9] }, east: { tex: 'lantern', uv: [0, 2, 6, 9] }, west: { tex: 'lantern', uv: [0, 2, 6, 9] } }), box(6, y + 7, 6, 10, y + 9, 10, { up: { tex: 'lantern', uv: [1, 10, 5, 14] }, north: { tex: 'lantern', uv: [1, 0, 5, 2] }, south: { tex: 'lantern', uv: [1, 0, 5, 2] }, east: { tex: 'lantern', uv: [1, 0, 5, 2] }, west: { tex: 'lantern', uv: [1, 0, 5, 2] } })]; }
		case 'iron_chain': return [{ kind: 'cross', tex: 'iron_chain', a: 6.5, b: 9.5, y0: 0, y1: 16, uv: [0, 0, 3, 16], uv2: [3, 0, 6, 16] }];
		case 'lightning_rod': return [box(7, 0, 7, 9, 12, 9, { north: { tex: 'lightning_rod', uv: [0, 4, 2, 16] }, south: { tex: 'lightning_rod', uv: [0, 4, 2, 16] }, east: { tex: 'lightning_rod', uv: [0, 4, 2, 16] }, west: { tex: 'lightning_rod', uv: [0, 4, 2, 16] } }), box(6, 12, 6, 10, 16, 10, { up: { tex: 'lightning_rod', uv: [0, 0, 4, 4] }, north: { tex: 'lightning_rod', uv: [0, 0, 4, 4] }, south: { tex: 'lightning_rod', uv: [0, 0, 4, 4] }, east: { tex: 'lightning_rod', uv: [0, 0, 4, 4] }, west: { tex: 'lightning_rod', uv: [0, 0, 4, 4] } })];
		case 'spore_blossom': return [flat('spore_blossom', 15.5, 0, 16)];
		case 'cave_vines': return [cross(p.berries === 'true' ? 'cave_vines_lit' : 'cave_vines')];
		case 'vine': return ['north', 'south', 'east', 'west'].filter((s) => p[s] === 'true').map((s) => wallQuad('vine', s));
		case 'pink_petals': return [flat('pink_petals_stem', 0.2), flat('pink_petals', 0.3)];
		case 'flowering_azalea': case 'azalea': return [cross(name + '_side'), flat(name + '_top', 15.9)];
		case 'pink_tulip': case 'allium': case 'dead_bush': case 'cactus_flower': return [cross(name)];
		default:
			if (!warned.has(id)) { warned.add(id); console.warn('no model for ' + id + ', drawing a cube'); }
			return [cube(sameAll(name))];
	}
}

// ---- Projection and drawing ----
// Viewed from the south-east and above: x runs to the lower right, z to the lower left, y up.
const COS = Math.sqrt(3) / 2, SIN = 0.5;
const SHADE = { up: 1, south: 0.8, east: 0.6, cross: 0.8, wall: 0.8, flat: 1 };

function render(blocks, size, pad) {
	// Quads: four 3D corners (block units), four uv corners, texture, shade.
	const quads = [];
	const quad = (pts, uvs, tex, shade, depth) => quads.push({ pts, uvs, tex, shade, depth });
	for (const { x, y, z, id, props, nbtData } of blocks) {
		for (const el of model(id, props)) {
			const X = (v) => x + v / 16, Y = (v) => y + v / 16, Z = (v) => z + v / 16;
			if (el.kind === 'box') {
				const { x0, y0, z0, x1, y1, z1, faces } = el;
				const centre = (x0 + x1) / 32 + (y0 + y1) / 32 + (z0 + z1) / 32;
				const uvOf = (f, d) => f.uv ? [f.uv[0], f.uv[1], f.uv[2], f.uv[3]] : d;
				if (faces.up) { const uv = uvOf(faces.up, [x0, z0, x1, z1]); quad([[X(x0), Y(y1), Z(z0)], [X(x1), Y(y1), Z(z0)], [X(x1), Y(y1), Z(z1)], [X(x0), Y(y1), Z(z1)]], [[uv[0], uv[1]], [uv[2], uv[1]], [uv[2], uv[3]], [uv[0], uv[3]]], faces.up.tex, SHADE.up, x + y + z + centre + (y1 - y0) / 32); }
				if (faces.south) { const uv = uvOf(faces.south, [x0, 16 - y1, x1, 16 - y0]); quad([[X(x0), Y(y1), Z(z1)], [X(x1), Y(y1), Z(z1)], [X(x1), Y(y0), Z(z1)], [X(x0), Y(y0), Z(z1)]], [[uv[0], uv[1]], [uv[2], uv[1]], [uv[2], uv[3]], [uv[0], uv[3]]], faces.south.tex, SHADE.south, x + y + z + centre + (z1 - z0) / 32); }
				if (faces.east) { const uv = uvOf(faces.east, [16 - z1, 16 - y1, 16 - z0, 16 - y0]); quad([[X(x1), Y(y1), Z(z1)], [X(x1), Y(y1), Z(z0)], [X(x1), Y(y0), Z(z0)], [X(x1), Y(y0), Z(z1)]], [[uv[0], uv[1]], [uv[2], uv[1]], [uv[2], uv[3]], [uv[0], uv[3]]], faces.east.tex, SHADE.east, x + y + z + centre + (x1 - x0) / 32); }
			} else if (el.kind === 'cross') {
				const { a, b, y0, y1, tex } = el, uv = el.uv || [0, 0, 16, 16], uv2 = el.uv2 || uv;
				const d = x + y + z + 1.5;
				quad([[X(a), Y(y1), Z(a)], [X(b), Y(y1), Z(b)], [X(b), Y(y0), Z(b)], [X(a), Y(y0), Z(a)]], [[uv[0], uv[1]], [uv[2], uv[1]], [uv[2], uv[3]], [uv[0], uv[3]]], tex, SHADE.cross, d);
				quad([[X(b), Y(y1), Z(a)], [X(a), Y(y1), Z(b)], [X(a), Y(y0), Z(b)], [X(b), Y(y0), Z(a)]], [[uv2[0], uv2[1]], [uv2[2], uv2[1]], [uv2[2], uv2[3]], [uv2[0], uv2[3]]], tex, SHADE.cross, d);
			} else if (el.kind === 'flat') {
				const { a, b, tex } = el;
				quad([[X(a), Y(el.y), Z(a)], [X(b), Y(el.y), Z(a)], [X(b), Y(el.y), Z(b)], [X(a), Y(el.y), Z(b)]], [[0, 0], [16, 0], [16, 16], [0, 16]], tex, SHADE.flat, x + y + z + 1.5 + el.y / 16);
			} else if (el.kind === 'wall') {
				const s = el.side, e = 0.05;
				const pts = s === 'east' ? [[X(16 - e), Y(16), Z(0)], [X(16 - e), Y(16), Z(16)], [X(16 - e), Y(0), Z(16)], [X(16 - e), Y(0), Z(0)]]
					: s === 'west' ? [[X(e), Y(16), Z(16)], [X(e), Y(16), Z(0)], [X(e), Y(0), Z(0)], [X(e), Y(0), Z(16)]]
					: s === 'south' ? [[X(0), Y(16), Z(16 - e)], [X(16), Y(16), Z(16 - e)], [X(16), Y(0), Z(16 - e)], [X(0), Y(0), Z(16 - e)]]
					: [[X(16), Y(16), Z(e)], [X(0), Y(16), Z(e)], [X(0), Y(0), Z(e)], [X(16), Y(0), Z(e)]];
				quad(pts, [[0, 0], [16, 0], [16, 16], [0, 16]], el.tex, SHADE.wall, x + y + z + 1.5 + (s === 'east' || s === 'south' ? 0.5 : -0.5));
			}
		}
	}
	// Painter's order: what's further from the viewer first.
	quads.sort((p, q) => p.depth - q.depth);
	// Fit: project every corner at unit scale to find the bounds, then scale into the canvas.
	const proj = ([x, y, z]) => [(x - z) * COS, (x + z) * SIN - y];
	let minX = Infinity, maxX = -Infinity, minY = Infinity, maxY = -Infinity;
	for (const q of quads) for (const p of q.pts) { const [sx, sy] = proj(p); minX = Math.min(minX, sx); maxX = Math.max(maxX, sx); minY = Math.min(minY, sy); maxY = Math.max(maxY, sy); }
	const SS = 2; // drawn at twice the size and averaged down, so texture edges don't shimmer
	const W = size * SS, scale = (size - 2 * pad) * SS / Math.max(maxX - minX, maxY - minY);
	const ox = (W - (maxX - minX) * scale) / 2 - minX * scale, oy = (W - (maxY - minY) * scale) / 2 - minY * scale;
	const canvas = new Float32Array(W * W * 4);
	for (const q of quads) {
		const t = texture(q.tex);
		const c = q.pts.map((p) => { const [sx, sy] = proj(p); return [sx * scale + ox, sy * scale + oy]; });
		// A parallelogram: c0 + s*(c1-c0) + u*(c3-c0). Solve for (s, u) per pixel.
		const ax = c[1][0] - c[0][0], ay = c[1][1] - c[0][1], bx = c[3][0] - c[0][0], by = c[3][1] - c[0][1];
		const det = ax * by - ay * bx;
		if (Math.abs(det) < 1e-6) continue;
		const x0 = Math.max(0, Math.floor(Math.min(...c.map((p) => p[0])))), x1 = Math.min(W - 1, Math.ceil(Math.max(...c.map((p) => p[0]))));
		const y0 = Math.max(0, Math.floor(Math.min(...c.map((p) => p[1])))), y1 = Math.min(W - 1, Math.ceil(Math.max(...c.map((p) => p[1]))));
		const [u0, v0] = q.uvs[0], du = q.uvs[1][0] - u0, dv = q.uvs[1][1] - v0, eu = q.uvs[3][0] - u0, ev = q.uvs[3][1] - v0;
		for (let py = y0; py <= y1; py++) {
			for (let px = x0; px <= x1; px++) {
				const dx = px + 0.5 - c[0][0], dy = py + 0.5 - c[0][1];
				const s = (dx * by - dy * bx) / det, u = (ax * dy - ay * dx) / det;
				if (s < 0 || s >= 1 || u < 0 || u >= 1) continue;
				const tu = Math.min(t.w - 1, Math.max(0, Math.floor(u0 + s * du + u * eu))), tv = Math.min(t.h - 1, Math.max(0, Math.floor(v0 + s * dv + u * ev)));
				const ti = (tv * t.w + tu) * 4, a = t.data[ti + 3] / 255;
				if (a <= 0) continue;
				const o = (py * W + px) * 4;
				for (let k = 0; k < 3; k++) canvas[o + k] = canvas[o + k] * (1 - a) + t.data[ti + k] * q.shade * a;
				canvas[o + 3] = canvas[o + 3] * (1 - a) + 255 * a;
			}
		}
	}
	// Average down to the final size.
	const out = new Uint8Array(size * size * 4);
	for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) {
		let r = 0, g = 0, b = 0, a = 0;
		for (let j = 0; j < SS; j++) for (let i = 0; i < SS; i++) { const o = ((y * SS + j) * W + x * SS + i) * 4, w = canvas[o + 3] / 255; r += canvas[o] * w; g += canvas[o + 1] * w; b += canvas[o + 2] * w; a += w; }
		const o = (y * size + x) * 4;
		if (a > 0) { out[o] = r / a; out[o + 1] = g / a; out[o + 2] = b / a; out[o + 3] = Math.round(255 * a / (SS * SS)); }
	}
	return out;
}

// ---- The structure, with a lawn under it ----
function loadBlocks(palette, wood) {
	const file = path.join(STRUCTURES, `gazebo_${palette}_${wood}.nbt`);
	const t = nbt.plain(nbt.read(fs.readFileSync(file)));
	const states = t.palette.map((p) => ({ id: p.id, props: p.properties || {} }));
	const blocks = [], filled = new Set();
	for (const b of t.blocks) {
		const [x, y, z] = b.pos, s = states[b.state];
		if (s.id === 'minecraft:air') continue;
		blocks.push({ x, y, z, id: s.id, props: s.props, nbtData: b.nbt });
		filled.add(`${x},${y},${z}`);
	}
	// The template leaves the ground outside the paving to the world; here it's grass (or sand) over dirt.
	const ground = palette === 'sand' ? 'minecraft:sand' : 'minecraft:grass_block';
	for (let x = 0; x < t.size[0]; x++) for (let z = 0; z < t.size[2]; z++) {
		if (!filled.has(`${x},0,${z}`)) blocks.push({ x, y: 0, z, id: ground, props: { snowy: 'false' } });
		blocks.push({ x, y: -1, z, id: palette === 'sand' ? 'minecraft:sandstone' : 'minecraft:dirt', props: {} });
	}
	return blocks;
}

function writePng(file, w, h, rgba) {
	fs.mkdirSync(path.dirname(file), { recursive: true });
	fs.writeFileSync(file, encodePng(w, h, rgba));
	console.log('wrote', file);
}

module.exports = { render, loadBlocks, writePng, texture, decodePng };

if (require.main !== module) {
	// Loaded by another script for its functions; nothing to draw.
} else if (flag('docs')) {
	// Three gazebos in a row: the common one, a spruce one, and the desert one.
	const size = +opt('size', 700), gap = 20;
	const sets = [['stone', 'oak'], ['stone', 'spruce'], ['sand', 'jungle']];
	const w = size * sets.length + gap * (sets.length - 1), out = new Uint8Array(w * size * 4);
	sets.forEach(([palette, wood], i) => {
		const img = render(loadBlocks(palette, wood), size, 24);
		for (let y = 0; y < size; y++) out.set(img.subarray(y * size * 4, (y + 1) * size * 4), (y * w + i * (size + gap)) * 4);
	});
	writePng(opt('out', path.join(ROOT, 'docs', 'gazebo.png')), w, size, out);
} else {
	const size = +opt('size', 512);
	const img = render(loadBlocks(opt('palette', 'stone'), opt('wood', 'oak')), size, Math.round(size * 0.04));
	writePng(opt('out', path.join(ROOT, 'src', 'main', 'resources', 'assets', 'spawn_gazebo', 'icon.png')), size, size, img);
}
