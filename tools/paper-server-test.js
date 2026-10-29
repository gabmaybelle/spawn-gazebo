#!/usr/bin/env node
// Starts a Paper server with the plugin on a brand-new world and checks the gazebo is at the spawn:
// the plugin's log line, the floor under the middle, the spore blossom and the lightning rod above
// it, the chest in the railing and the bed inside it. No window opens; the server is stopped at the
// end. The Paper server jar is downloaded once into the folder.
//
//   node tools/paper-server-test.js "<server folder>" --plugin paper/build/libs/spawn-gazebo-paper-<v>.jar --eula
//       [--paper 26.3] [--seed <n>] [--port <n>] [--no-chest] [--keep-world] [--timeout <seconds>]
//
// --eula says you accept Minecraft's server EULA (https://aka.ms/MinecraftEULA); without it the
// script stops before starting the server. The world is deleted first unless --keep-world is given,
// since the gazebo is only built in a world that has never been played; with --keep-world the test
// checks instead that the plugin leaves the world alone. --no-chest says the plugin's starterChest
// setting is off, so no chest is expected.
'use strict';
const fs = require('fs'), path = require('path'), cp = require('child_process'), crypto = require('crypto');

const args = process.argv.slice(2);
const folder = args[0] && !args[0].startsWith('--') ? path.resolve(args[0]) : null;
const opt = (name, fallback) => { const i = args.indexOf(name); return i >= 0 && i + 1 < args.length ? args[i + 1] : fallback; };
const flag = (name) => args.includes(name);
const plugin = opt('--plugin', null);
if (!folder || !plugin) {
	console.error('usage: node tools/paper-server-test.js "<server folder>" --plugin <jar> --eula [--paper 26.3] [--seed <n>] [--port <n>] [--keep-world] [--timeout <s>]');
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
	fs.mkdirSync(path.join(folder, 'plugins'), { recursive: true });
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

	/** Sends a console command and returns what the server said to it (Test passed / failed, or an error line). */
	async command(text) {
		const from = this.lines.length;
		this.proc.stdin.write(text + '\n');
		// The console prints a command's reply as "System chat: ..." (Test passed, Test failed, an error).
		return this.waitFor(/System chat: |Unknown or incomplete command|Incorrect argument/, 15000, from);
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
	try {
		const deadline = Date.now() + timeoutMs;
		await Promise.race([server.waitFor(/Done \(.*\)! For help/, timeoutMs), server.exited.then(() => { throw new Error('the server stopped before it was ready'); })]);
		const built = server.lines.find((l) => /Spawn Gazebo: built the gazebo/.test(l));
		const gazeboLines = server.lines.filter((l) => /Spawn Gazebo/.test(l)).map((l) => l.replace(/^.*?\]: /, '')).join(' | ');
		if (flag('--keep-world')) {
			const left = server.lines.find((l) => /Spawn Gazebo: .* was looked at before, leaving it as it is/.test(l));
			check('the plugin left the existing world alone', !!left && !built, gazeboLines || 'no Spawn Gazebo line in the log');
			return;
		}
		check('the plugin built the gazebo', !!built, built ? built.replace(/^.*?\]: /, '') : gazeboLines || 'no Spawn Gazebo line in the log');
		if (!built) return;
		const m = /at (-?\d+) (-?\d+) (-?\d+)(?:, chest at (-?\d+) (-?\d+) (-?\d+))?/.exec(built);
		const [cx, cy, cz] = [+m[1], +m[2], +m[3]];
		const ground = cy - 3;
		const passed = async (name, cmd) => { const reply = await server.command(cmd); check(name, /Test passed/.test(reply), reply.replace(/^.*?\]: /, '').replace(/\x1b\[[0-9;]*m/g, '')); };
		// The spawn chunks can unload once the server is up; keep the gazebo loaded while it is looked at.
		await server.command(`forceload add ${cx - 8} ${cz - 8} ${cx + 8} ${cz + 8}`);
		await passed('a floor under the middle', `execute unless block ${cx} ${ground + 2} ${cz} minecraft:air`);
		await passed('the spore blossom over the middle', `execute if block ${cx} ${ground + 8} ${cz} minecraft:spore_blossom`);
		await passed('the lightning rod on the dome', `execute if block ${cx} ${ground + 14} ${cz} minecraft:lightning_rod`);
		if (flag('--no-chest')) {
			check('no chest (starterChest is off)', m[4] === undefined, m[4] === undefined ? '' : 'a chest was placed');
		} else if (m[4] === undefined) {
			check('the chest in the railing', false, 'the plugin found no chest');
		} else {
			const [chx, chy, chz] = [+m[4], +m[5], +m[6]];
			await passed('the chest in the railing', `execute if block ${chx} ${chy} ${chz} minecraft:chest`);
			await passed('a bed in the chest', `execute if data block ${chx} ${chy} ${chz} Items[{id:"minecraft:white_bed"}]`);
			await passed('torches in the chest', `execute if data block ${chx} ${chy} ${chz} Items[{id:"minecraft:torch",count:16}]`);
		}
		await server.command('forceload remove all');
		const errors = server.lines.filter((l) => /^\[[\d:]+ (ERROR|WARN)\]/.test(l) && /SpawnGazebo|Spawn Gazebo|spawngazebo/i.test(l));
		check('no errors or warnings from the plugin', errors.length === 0, errors.join(' | '));
		if (Date.now() > deadline) throw new Error('timed out');
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
