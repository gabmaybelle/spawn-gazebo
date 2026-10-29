#!/usr/bin/env node
// Starts a Paper server with the plugin on a brand-new world and checks the gazebo is at the spawn
// and stays there: the plugin's log line, the floor under the middle, the spore blossom and the
// lightning rod above it, the chest in the railing and the bed inside it; then that TNT, fire and a
// piston leave it alone (while TNT still works away from it), and that /spawngazebo build raises a
// second gazebo and refuses to build on top of one. No window opens; the server is stopped at the
// end. The Paper server jar is downloaded once into the folder.
//
//   node tools/paper-server-test.js "<server folder>" --plugin paper/build/libs/spawn-gazebo-paper-<v>.jar --eula
//       [--paper 26.3] [--seed <n>] [--port <n>] [--no-chest] [--no-protection] [--keep-world] [--timeout <seconds>]
//
// --eula says you accept Minecraft's server EULA (https://aka.ms/MinecraftEULA); without it the
// script stops before starting the server. The world is deleted first unless --keep-world is given,
// since the gazebo is only built in a world that has never been played; with --keep-world the test
// checks instead that the plugin leaves the world alone. --no-chest sets the plugin's starterChest
// off and expects no chest; --no-protection sets protectGazebo off, so the protection checks are
// expected to fail (the proof that they test something).
'use strict';
const fs = require('fs'), path = require('path'), cp = require('child_process'), crypto = require('crypto');

const args = process.argv.slice(2);
const folder = args[0] && !args[0].startsWith('--') ? path.resolve(args[0]) : null;
const opt = (name, fallback) => { const i = args.indexOf(name); return i >= 0 && i + 1 < args.length ? args[i + 1] : fallback; };
const flag = (name) => args.includes(name);
const plugin = opt('--plugin', null);
if (!folder || !plugin) {
	console.error('usage: node tools/paper-server-test.js "<server folder>" --plugin <jar> --eula [--paper 26.3] [--seed <n>] [--port <n>] [--no-chest] [--no-protection] [--keep-world] [--timeout <s>]');
	process.exit(2);
}
if (!flag('--eula')) {
	console.error('The server only runs once the Minecraft server EULA (https://aka.ms/MinecraftEULA) is accepted: pass --eula to say you do.');
	process.exit(2);
}
const paperVersion = opt('--paper', '26.3');
const port = +opt('--port', '25599');
const seed = opt('--seed', '');
const timeoutMs = +opt('--timeout', '600') * 1000;
const javaBin = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', 'java') : 'java';
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function download(url, to, sha256) {
	const res = await fetch(url, { headers: { 'User-Agent': 'spawn-gazebo-server-test (https://github.com/gabmaybelle/spawn-gazebo)' } });
	if (!res.ok) throw new Error(`${url}: ${res.status}`);
	const data = Buffer.from(await res.arrayBuffer());
	if (sha256 && crypto.createHash('sha256').update(data).digest('hex') !== sha256) throw new Error(`${url}: checksum mismatch`);
	fs.writeFileSync(to, data);
}

/** The newest Paper build of this version, downloaded once as paper-<version>-<build>.jar. */
async function serverJar() {
	const existing = fs.readdirSync(folder).filter((f) => f.startsWith(`paper-${paperVersion}-`) && f.endsWith('.jar')).sort();
	if (existing.length) return path.join(folder, existing[existing.length - 1]);
	const res = await fetch(`https://fill.papermc.io/v3/projects/paper/versions/${paperVersion}/builds/latest`,
		{ headers: { 'User-Agent': 'spawn-gazebo-server-test (https://github.com/gabmaybelle/spawn-gazebo)' } });
	if (!res.ok) throw new Error(`Paper build list: ${res.status}`);
	const build = await res.json();
	const dl = build.downloads['server:default'];
	console.log(`downloading Paper ${paperVersion} build ${build.id} (${dl.name})`);
	const to = path.join(folder, dl.name);
	await download(dl.url, to, dl.checksums && dl.checksums.sha256);
	return to;
}

function prepare() {
	fs.mkdirSync(path.join(folder, 'plugins', 'SpawnGazebo'), { recursive: true });
	fs.writeFileSync(path.join(folder, 'eula.txt'), 'eula=true\n');
	fs.writeFileSync(path.join(folder, 'server.properties'), [
		'online-mode=false', `server-port=${port}`, 'query.port=' + port, 'enable-rcon=false', 'white-list=false',
		'spawn-protection=0', 'view-distance=6', 'simulation-distance=4', 'max-players=2', 'motd=Spawn Gazebo test',
		`level-seed=${seed}`, 'level-name=world', 'sync-chunk-writes=false', ''].join('\n'));
	// Only this plugin, and only the jar under test.
	for (const f of fs.readdirSync(path.join(folder, 'plugins'))) {
		if (f.endsWith('.jar')) fs.rmSync(path.join(folder, 'plugins', f));
	}
	fs.copyFileSync(plugin, path.join(folder, 'plugins', path.basename(plugin)));
	// The plugin's settings for this run: its defaults, with the switches the flags turn off.
	const config = path.join(folder, 'plugins', 'SpawnGazebo', 'config.yml');
	if (flag('--no-chest') || flag('--no-protection')) {
		fs.writeFileSync(config, `starterChest: ${!flag('--no-chest')}\nprotectGazebo: ${!flag('--no-protection')}\n`);
	} else {
		fs.rmSync(config, { force: true });
	}
	if (!flag('--keep-world')) {
		for (const w of ['world', 'world_nether', 'world_the_end']) fs.rmSync(path.join(folder, w), { recursive: true, force: true });
	}
}

/** Runs the server, feeding it console commands and reading their replies. */
class Server {
	constructor(jar) {
		this.lines = [];
		this.waiters = [];
		this.proc = cp.spawn(javaBin, ['-Xmx2G', '-Dcom.mojang.eula.agree=true', '-jar', jar, 'nogui'],
			{ cwd: folder, stdio: ['pipe', 'pipe', 'pipe'], windowsHide: true });
		let buffer = '';
		const onData = (chunk) => {
			buffer += chunk.toString();
			let i;
			while ((i = buffer.indexOf('\n')) >= 0) {
				const line = buffer.slice(0, i).replace(/\r$/, '');
				buffer = buffer.slice(i + 1);
				this.lines.push(line);
				for (const w of [...this.waiters]) if (w.test(line)) { this.waiters.splice(this.waiters.indexOf(w), 1); w.resolve(line); }
			}
		};
		this.proc.stdout.on('data', onData);
		this.proc.stderr.on('data', onData);
		this.exited = new Promise((resolve) => this.proc.on('exit', (code) => { this.exitCode = code; resolve(code); }));
	}

	/** The next line matching the pattern (or one already seen since `from`), within the time given. */
	waitFor(pattern, ms, from = 0) {
		const found = this.lines.slice(from).find((l) => pattern.test(l));
		if (found) return Promise.resolve(found);
		return new Promise((resolve, reject) => {
			const w = { test: (l) => pattern.test(l), resolve: (l) => { clearTimeout(timer); resolve(l); } };
			const timer = setTimeout(() => { this.waiters.splice(this.waiters.indexOf(w), 1); reject(new Error(`timed out waiting for ${pattern}`)); }, ms);
			this.waiters.push(w);
		});
	}

	/**
	 * Sends a console command and returns what the server said to it. The console prints a vanilla
	 * command's reply as "System chat: ..." (Test passed, Test failed, an error); the plugin's own
	 * messages are plain lines, so a pattern for them can be given.
	 */
	async command(text, reply = /System chat: |Unknown or incomplete command|Incorrect argument/) {
		const from = this.lines.length;
		this.proc.stdin.write(text + '\n');
		const line = await this.waitFor(reply, 15000, from);
		return line.replace(/^.*?\]: /, '').replace(/\x1b\[[0-9;]*m/g, '');
	}

	async stop() {
		if (this.exitCode !== undefined) return;
		try { this.proc.stdin.write('stop\n'); } catch (e) { /* already gone */ }
		const t = setTimeout(() => this.proc.kill(), 60000);
		await this.exited;
		clearTimeout(t);
	}
}

async function main() {
	fs.mkdirSync(folder, { recursive: true });
	const jar = await serverJar();
	prepare();
	console.log(`starting Paper (${path.basename(jar)}) in ${folder} with ${path.basename(plugin)}`);
	const server = new Server(jar);
	const failures = [];
	const check = (name, ok, detail) => { console.log(`${ok ? 'ok  ' : 'FAIL'} ${name}${detail ? ': ' + detail : ''}`); if (!ok) failures.push(name); };
	const passed = async (name, cmd) => { const reply = await server.command(cmd); check(name, /Test passed/.test(reply), reply); };
	const builtLine = (n) => server.lines.filter((l) => /Spawn Gazebo: built the gazebo/.test(l))[n];
	const where = (line) => { const m = /at (-?\d+) (-?\d+) (-?\d+)(?:, chest at (-?\d+) (-?\d+) (-?\d+))?/.exec(line); return { cx: +m[1], ground: +m[2] - 3, cz: +m[3], chest: m[4] === undefined ? null : { x: +m[4], y: +m[5], z: +m[6] } }; };
	/** The gazebo's own checks: the floor under the middle, the spore blossom and the lightning rod over it. */
	const standing = async (label, g) => {
		await passed(`${label}: a floor under the middle`, `execute unless block ${g.cx} ${g.ground + 2} ${g.cz} minecraft:air`);
		await passed(`${label}: the spore blossom over the middle`, `execute if block ${g.cx} ${g.ground + 8} ${g.cz} minecraft:spore_blossom`);
		await passed(`${label}: the lightning rod on the dome`, `execute if block ${g.cx} ${g.ground + 14} ${g.cz} minecraft:lightning_rod`);
	};
	try {
		await Promise.race([server.waitFor(/Done \(.*\)! For help/, timeoutMs), server.exited.then(() => { throw new Error('the server stopped before it was ready'); })]);
		const gazeboLines = server.lines.filter((l) => /Spawn Gazebo/.test(l)).map((l) => l.replace(/^.*?\]: /, '')).join(' | ');
		if (flag('--keep-world')) {
			const left = server.lines.find((l) => /Spawn Gazebo: .* was looked at before, leaving it as it is/.test(l));
			check('the plugin left the existing world alone', !!left && !builtLine(0), gazeboLines || 'no Spawn Gazebo line in the log');
			return;
		}
		const built = builtLine(0);
		check('the plugin built the gazebo', !!built, built ? built.replace(/^.*?\]: /, '') : gazeboLines || 'no Spawn Gazebo line in the log');
		if (!built) return;
		const g = where(built);
		const { cx, ground, cz } = g;
		// The spawn chunks can unload once the server is up; keep the area loaded while it is looked at.
		await server.command(`forceload add ${cx - 80} ${cz - 80} ${cx + 80} ${cz + 80}`);
		await standing('new world', g);
		if (flag('--no-chest')) {
			check('no chest (starterChest is off)', g.chest === null, g.chest === null ? '' : 'a chest was placed');
		} else if (g.chest === null) {
			check('the chest in the railing', false, 'the plugin found no chest');
		} else {
			const { x, y, z } = g.chest;
			await passed('the chest in the railing', `execute if block ${x} ${y} ${z} minecraft:chest`);
			await passed('a bed in the chest', `execute if data block ${x} ${y} ${z} Items[{id:"minecraft:white_bed"}]`);
			await passed('torches in the chest', `execute if data block ${x} ${y} ${z} Items[{id:"minecraft:torch",count:16}]`);
		}

		// Protection. A copy of the gazebo, high in the air, to compare against afterwards. Nothing
		// may change on its own meanwhile: the glow berries grow and snow settles on the dome.
		const stillness = await server.command('gamerule random_tick_speed 0');
		check('random ticks off for the comparison', !/Unknown|Incorrect|Expected/.test(stillness), stillness);
		await server.command('weather clear 100000');
		// The whole gazebo is cloned, ground layer included and on a barrier platform, so that
		// lanterns and plants keep what they stand on and nothing in the ground layer falls; only the
		// layers above the ground are compared, since the ground layer's corners are natural terrain.
		const copy = { x: cx + 40, y: 250, z: cz + 40 };
		const region = `${cx - 7} ${ground} ${cz - 7} ${cx + 7} ${ground + 14} ${cz + 7}`;
		const above = `${cx - 7} ${ground + 1} ${cz - 7} ${cx + 7} ${ground + 14} ${cz + 7}`;
		await server.command(`fill ${copy.x} ${copy.y - 1} ${copy.z} ${copy.x + 14} ${copy.y - 1} ${copy.z + 14} minecraft:barrier`);
		await server.command(`clone ${region} ${copy.x} ${copy.y} ${copy.z}`);
		/** Whether the gazebo still matches its copy; on a mismatch, says which layers differ. */
		const unchanged = async (name) => {
			const reply = await server.command(`execute if blocks ${above} ${copy.x} ${copy.y + 1} ${copy.z} masked`);
			const ok = /Test passed/.test(reply);
			let detail = reply;
			if (!ok) {
				const layers = [];
				for (let y = 1; y <= 14; y++) {
					const r = await server.command(`execute if blocks ${cx - 7} ${ground + y} ${cz - 7} ${cx + 7} ${ground + y} ${cz + 7} ${copy.x} ${copy.y + y} ${copy.z} masked`);
					if (!/Test passed/.test(r)) layers.push(y);
				}
				detail += `; layers above the ground that differ: ${layers.join(', ')}`;
			}
			check(name, ok, detail);
		};
		await unchanged('the copy matches the gazebo');
		// Fire first, on every open block of the gazebo's inside, left to burn for a while, then put
		// out: on its own, so the proof run shows fire alone burning the benches with the protection
		// off. Since 26.x fire only spreads near a player unless this rule says everywhere; and a
		// plank well outside the protected radius, with fire beside it, shows the fire is really burning.
		const spread = await server.command('gamerule fire_spread_radius_around_player -1');
		check('fire spreads without a player nearby', !/Unknown|Incorrect|Expected/.test(spread), spread);
		// Nine planks with a fire on each: one plank alone has even odds of catching in this time.
		await server.command(`fill ${cx + 29} ${ground + 30} ${cz - 1} ${cx + 31} ${ground + 30} ${cz + 1} minecraft:oak_planks`);
		await server.command(`fill ${cx + 29} ${ground + 31} ${cz - 1} ${cx + 31} ${ground + 31} ${cz + 1} minecraft:fire`);
		await server.command(`fill ${cx - 6} ${ground + 3} ${cz - 6} ${cx + 6} ${ground + 6} ${cz + 6} minecraft:fire keep`);
		await sleep(15000);
		await server.command(`fill ${region} minecraft:air replace minecraft:fire`);
		let burnt = 0;
		for (let dx = 29; dx <= 31; dx++) {
			for (let dz = -1; dz <= 1; dz++) {
				if (/Test passed/.test(await server.command(`execute unless block ${cx + dx} ${ground + 30} ${cz + dz} minecraft:oak_planks`))) burnt++;
			}
		}
		check('fire still burns outside the protected area', burnt > 0, `${burnt} of 9 planks burnt`);
		await server.command(`fill ${cx + 28} ${ground + 29} ${cz - 2} ${cx + 32} ${ground + 32} ${cz + 2} minecraft:air`);
		await unchanged('fire burned nothing of the gazebo');
		// TNT in the middle of the gazebo, and TNT on a dirt block well outside the protected radius.
		await server.command(`setblock ${cx + 30} ${ground + 20} ${cz} minecraft:dirt`);
		await server.command(`summon minecraft:tnt ${cx} ${ground + 4} ${cz}`, /Summoned|System chat: /);
		await server.command(`summon minecraft:tnt ${cx + 30} ${ground + 21} ${cz}`, /Summoned|System chat: /);
		await sleep(6000);
		await standing('after TNT inside', g);
		await passed('TNT still blows up outside the protected area', `execute if block ${cx + 30} ${ground + 20} ${cz} minecraft:air`);
		await unchanged('TNT changed nothing of the gazebo');
		// A piston just outside the gazebo's box, pushing into it.
		await server.command(`setblock ${cx + 8} ${ground + 3} ${cz} minecraft:piston[facing=west]`);
		await server.command(`setblock ${cx + 9} ${ground + 3} ${cz} minecraft:redstone_block`);
		await sleep(1500);
		await passed('a piston cannot push into the gazebo', `execute unless block ${cx + 7} ${ground + 3} ${cz} minecraft:piston_head`);
		await server.command(`fill ${cx + 8} ${ground + 3} ${cz} ${cx + 9} ${ground + 3} ${cz} minecraft:air`);

		// The build command: a second gazebo 60 blocks away, then a refusal on top of it.
		const askReply = await server.command(`execute positioned ${cx + 60} ${ground} ${cz + 60} run spawngazebo build`, /This builds a gazebo|There's already a gazebo|Usage|Unknown or incomplete command|System chat: /);
		check('/spawngazebo build says what it will do', /This builds a gazebo centred here/.test(askReply), askReply);
		const confirmReply = await server.command(`execute positioned ${cx + 60} ${ground} ${cz + 60} run spawngazebo build confirm`, /Built the gazebo|Nothing to confirm|There's already a gazebo|Couldn't build|System chat: /);
		check('/spawngazebo build confirm builds it', /Built the gazebo; the world spawn is now in it/.test(confirmReply), confirmReply);
		const second = builtLine(1);
		check('the plugin logged the second gazebo', !!second, second ? second.replace(/^.*?\]: /, '') : 'no second build line');
		if (second) {
			const h = where(second);
			check('the second gazebo is where it was asked for', h.cx === cx + 60 && h.cz === cz + 60, `${h.cx} ${h.cz}`);
			await standing('the second gazebo', h);
			await standing('the first gazebo, still standing', g);
			const again = await server.command(`execute positioned ${h.cx + 3} ${h.ground} ${h.cz} run spawngazebo build`, /This builds a gazebo|There's already a gazebo|System chat: /);
			check('building on top of a gazebo is refused', /There's already a gazebo here/.test(again), again);
		}
		const unconfirmed = await server.command('spawngazebo build confirm', /Built the gazebo|Nothing to confirm|There's already a gazebo|System chat: /);
		check('confirm without a build does nothing', /Nothing to confirm/.test(unconfirmed), unconfirmed);

		await server.command('forceload remove all');
		const errors = server.lines.filter((l) => /^\[[\d:]+ (ERROR|WARN)\]/.test(l) && /SpawnGazebo|Spawn Gazebo|spawngazebo/i.test(l));
		check('no errors or warnings from the plugin', errors.length === 0, errors.join(' | '));
	} catch (e) {
		check('the test ran to the end', false, e.message);
	} finally {
		await server.stop();
		fs.writeFileSync(path.join(folder, 'test-console.log'), server.lines.join('\n'));
		console.log(failures.length ? `FAILED: ${failures.join(', ')}` : 'passed: every check');
		process.exit(failures.length ? 1 : 0);
	}
}

main().catch((e) => { console.error(e); process.exit(1); });
