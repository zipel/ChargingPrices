// Starts the whole app for local development with one command: `npm start` in the repository folder.
// Runs the Spring Boot back end and the Vite dev server side by side, opens the browser when both are
// ready, and stops both on Ctrl+C. Written for macOS, Linux and Windows (tested on macOS).
// Add `-- --no-open` to skip opening the browser.

import { spawn, spawnSync } from 'node:child_process'
import { existsSync } from 'node:fs'
import net from 'node:net'
import { dirname, join } from 'node:path'
import readline from 'node:readline'
import { fileURLToPath } from 'node:url'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const WINDOWS = process.platform === 'win32'
const WEB_URL = 'http://localhost:5173'
const HEALTH_URL = 'http://localhost:8080/actuator/health'
const OPEN_BROWSER = !process.argv.includes('--no-open')

const COLOURS = { api: '\x1b[36m', web: '\x1b[35m', start: '\x1b[32m' }
const RESET = '\x1b[0m'

function log(name, line) {
  console.log(`${COLOURS[name] ?? ''}[${name}]${RESET} ${line}`)
}

/** True when something already listens on the port, e.g. an earlier run that is still going. */
function portInUse(port) {
  return new Promise((resolve) => {
    const socket = net.connect({ port, host: '127.0.0.1' })
    socket.once('connect', () => {
      socket.destroy()
      resolve(true)
    })
    socket.once('error', () => resolve(false))
  })
}

for (const port of [8080, 5173]) {
  if (await portInUse(port)) {
    log('start', `Port ${port} is already in use. Is the app already running? Stop it first (see README), then try again.`)
    process.exit(1)
  }
}

// First run: the web app needs its dependencies
if (!existsSync(join(ROOT, 'web', 'node_modules'))) {
  log('start', 'Installing the web app’s dependencies (first run only)…')
  const install = spawnSync('npm', ['install'], { cwd: join(ROOT, 'web'), stdio: 'inherit', shell: WINDOWS })
  if (install.status !== 0) process.exit(install.status ?? 1)
}

const children = []
let stopping = false

/**
 * Starts a child process and prefixes its output. On macOS and Linux each child gets its own process group
 * (detached), so stopping it also stops what it started, such as the app's JVM under Gradle.
 */
function start(name, command, args, cwd) {
  // Windows can only run .bat and .cmd files through a shell
  const child = spawn(command, args, { cwd, shell: WINDOWS, detached: !WINDOWS, stdio: ['ignore', 'pipe', 'pipe'] })
  for (const stream of [child.stdout, child.stderr]) {
    readline.createInterface({ input: stream }).on('line', (line) => log(name, line))
  }
  child.on('exit', (code) => {
    if (!stopping) {
      log('start', `The ${name} process stopped unexpectedly (exit code ${code}). Stopping the rest.`)
      stop(1)
    }
  })
  children.push(child)
  return child
}

/**
 * Whether the child, or anything in its process group, is still running (npm and Vite, or gradlew).
 * The app's own JVM runs under the Gradle daemon, which stops it when gradlew is interrupted.
 */
function alive(child) {
  if (WINDOWS || child.pid === undefined) return child.exitCode === null && child.signalCode === null
  try {
    process.kill(-child.pid, 0)
    return true
  } catch {
    return false
  }
}

function kill(child, signal) {
  if (!alive(child)) return
  try {
    if (WINDOWS) {
      spawnSync('taskkill', ['/pid', String(child.pid), '/T', '/F'])
    } else {
      process.kill(-child.pid, signal)
    }
  } catch {
    // Already gone
  }
}

function stop(exitCode = 0) {
  if (stopping) return
  stopping = true
  log('start', 'Stopping…')
  children.forEach((child) => kill(child, 'SIGINT'))
  // Anything still running after 10 seconds is stopped hard
  const timer = setTimeout(() => {
    children.forEach((child) => kill(child, 'SIGKILL'))
    process.exit(exitCode)
  }, 10_000)
  const waitForAll = setInterval(() => {
    if (children.every((child) => !alive(child))) {
      clearInterval(waitForAll)
      clearTimeout(timer)
      log('start', 'Stopped. The back end can take a few more seconds to finish shutting down.')
      process.exit(exitCode)
    }
  }, 200)
}

process.on('SIGINT', () => stop(0))
process.on('SIGTERM', () => stop(0))

log('start', 'Starting the back end (port 8080) and the web app (port 5173)…')
start('api', WINDOWS ? 'gradlew.bat' : './gradlew', ['bootRun', '--console=plain'], join(ROOT, 'backend'))
start('web', 'npm', ['run', 'dev'], join(ROOT, 'web'))

// Wait until the back end answers, then point the user at the app
for (let i = 0; i < 180 && !stopping; i++) {
  try {
    await fetch(HEALTH_URL)
    log('start', `Ready: open ${WEB_URL} (prices keep loading for about a minute). Press Ctrl+C to stop.`)
    if (OPEN_BROWSER) {
      const opener = WINDOWS ? ['cmd', ['/c', 'start', '', WEB_URL]] : [process.platform === 'darwin' ? 'open' : 'xdg-open', [WEB_URL]]
      spawn(opener[0], opener[1], { stdio: 'ignore', detached: true }).unref()
    }
    break
  } catch {
    await new Promise((resolve) => setTimeout(resolve, 1000))
  }
}
