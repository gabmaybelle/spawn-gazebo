// A small NBT reader and writer for structure files (gzip-compressed, big-endian).
// Values written: plain JS numbers become ints, unless wrapped with the helpers below.
const zlib = require('zlib');

const T = { END: 0, BYTE: 1, SHORT: 2, INT: 3, LONG: 4, FLOAT: 5, DOUBLE: 6, BYTE_ARRAY: 7, STRING: 8, LIST: 9, COMPOUND: 10, INT_ARRAY: 11, LONG_ARRAY: 12 };

// Typed wrappers for writing: byte(1), short(2), float(1.5), double(2.5), long(5n), list(type, [...]).
const tagged = (type, value) => ({ __nbt: type, value });
const byte = (v) => tagged(T.BYTE, v), short = (v) => tagged(T.SHORT, v), float = (v) => tagged(T.FLOAT, v);
const double = (v) => tagged(T.DOUBLE, v), long = (v) => tagged(T.LONG, BigInt(v));
const list = (type, items) => tagged(T.LIST, { type, items });

function read(buffer) {
	const b = zlib.gunzipSync(buffer);
	let o = 0;
	const str = () => { const n = b.readUInt16BE(o); o += 2; const s = b.toString('utf8', o, o + n); o += n; return s; };
	const payload = (t) => {
		switch (t) {
			case T.BYTE: return byte(b.readInt8(o++));
			case T.SHORT: { const v = b.readInt16BE(o); o += 2; return short(v); }
			case T.INT: { const v = b.readInt32BE(o); o += 4; return v; }
			case T.LONG: { const v = b.readBigInt64BE(o); o += 8; return tagged(T.LONG, v); }
			case T.FLOAT: { const v = b.readFloatBE(o); o += 4; return float(v); }
			case T.DOUBLE: { const v = b.readDoubleBE(o); o += 8; return double(v); }
			case T.BYTE_ARRAY: { const n = b.readInt32BE(o); o += 4; const v = [...b.subarray(o, o + n)]; o += n; return tagged(T.BYTE_ARRAY, v); }
			case T.STRING: return str();
			case T.LIST: { const et = b.readInt8(o++); const n = b.readInt32BE(o); o += 4; const items = []; for (let i = 0; i < n; i++) items.push(payload(et)); return list(et, items); }
			case T.COMPOUND: { const c = {}; for (;;) { const ct = b.readInt8(o++); if (ct === T.END) return c; const name = str(); c[name] = payload(ct); } }
			case T.INT_ARRAY: { const n = b.readInt32BE(o); o += 4; const v = []; for (let i = 0; i < n; i++) { v.push(b.readInt32BE(o)); o += 4; } return tagged(T.INT_ARRAY, v); }
			case T.LONG_ARRAY: { const n = b.readInt32BE(o); o += 4; const v = []; for (let i = 0; i < n; i++) { v.push(b.readBigInt64BE(o)); o += 8; } return tagged(T.LONG_ARRAY, v); }
		}
		throw new Error('bad tag ' + t);
	};
	const rootType = b.readInt8(o++); str();
	return payload(rootType);
}

function typeOf(v) {
	if (v && v.__nbt !== undefined) return v.__nbt;
	if (typeof v === 'number') return T.INT;
	if (typeof v === 'string') return T.STRING;
	if (Array.isArray(v)) throw new Error('use list(type, items) for arrays');
	return T.COMPOUND;
}

function write(root) {
	const parts = [];
	const u8 = (n) => parts.push(Buffer.from([n & 255]));
	const i16 = (n) => { const x = Buffer.alloc(2); x.writeInt16BE(n); parts.push(x); };
	const i32 = (n) => { const x = Buffer.alloc(4); x.writeInt32BE(n); parts.push(x); };
	const str = (s) => { const x = Buffer.from(s, 'utf8'); const l = Buffer.alloc(2); l.writeUInt16BE(x.length); parts.push(l, x); };
	const payload = (t, v) => {
		const val = v && v.__nbt !== undefined ? v.value : v;
		switch (t) {
			case T.BYTE: return u8(val);
			case T.SHORT: return i16(val);
			case T.INT: return i32(val);
			case T.LONG: { const x = Buffer.alloc(8); x.writeBigInt64BE(BigInt(val)); return parts.push(x); }
			case T.FLOAT: { const x = Buffer.alloc(4); x.writeFloatBE(val); return parts.push(x); }
			case T.DOUBLE: { const x = Buffer.alloc(8); x.writeDoubleBE(val); return parts.push(x); }
			case T.BYTE_ARRAY: i32(val.length); return parts.push(Buffer.from(val));
			case T.STRING: return str(val);
			case T.LIST: u8(val.items.length ? val.type : 0); i32(val.items.length); for (const item of val.items) payload(val.type, item); return;
			case T.COMPOUND: for (const [k, x] of Object.entries(val)) { const ct = typeOf(x); u8(ct); str(k); payload(ct, x); } return u8(T.END);
			case T.INT_ARRAY: i32(val.length); for (const n of val) i32(n); return;
			case T.LONG_ARRAY: { i32(val.length); for (const n of val) { const x = Buffer.alloc(8); x.writeBigInt64BE(BigInt(n)); parts.push(x); } return; }
		}
		throw new Error('bad tag ' + t);
	};
	u8(T.COMPOUND); str(''); payload(T.COMPOUND, root);
	return zlib.gzipSync(Buffer.concat(parts));
}

// Plain JSON view of a read tree, for inspection.
function plain(v) {
	if (v && v.__nbt !== undefined) {
		if (v.__nbt === T.LIST) return v.value.items.map(plain);
		if (v.__nbt === T.LONG) return v.value.toString() + 'L';
		return v.value;
	}
	if (v && typeof v === 'object') return Object.fromEntries(Object.entries(v).map(([k, x]) => [k, plain(x)]));
	return v;
}

module.exports = { T, read, write, plain, byte, short, float, double, long, list };
