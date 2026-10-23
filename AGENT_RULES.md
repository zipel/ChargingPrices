# Agent operating rules

Every rule below has a trigger. If you can't point to the condition that fires it, it doesn't belong here.

This repo is worked on from two machines, a Mac and a Windows laptop, sometimes with more than one Claude Code session at a time. Commands are given for both where they differ.

## Rules

1. **Before editing infra code, read live state.** Trigger: about to edit a GitHub Actions workflow (`.github/workflows/`), `docker-compose.yml`, a Dockerfile or deploy config. Action: check what is actually running first. Look at recent and in-flight CI runs (`gh run list --limit 5`), local containers (`docker compose ps`) and, once a demo is online, the version it reports. The file on disk is not the system.

2. **Before starting work on a shared branch, pull.** Trigger: starting a session, or about to commit on `main` or any branch that the other machine or another session may have pushed to. Action: `git fetch && git pull --ff-only` first. If it fails because the histories have diverged, stop and ask Dimos. Never force-push or rebase a pushed branch without asking. A stale base is how two machines race and one push gets rejected.

3. **Bound every operation that can hang.** Trigger: running something that can block on network, disk or a worker pool (Gradle build, test run with Testcontainers, `npm ci`, migration, deploy). Action: set an explicit timeout on the tool call, and run anything expected to take more than about 2 minutes in the background. GNU `timeout` isn't installed on macOS or Windows by default, so don't rely on it. In CI, every GitHub Actions job gets `timeout-minutes`, because the default is 360 minutes. Unbounded operations are how CI jobs wedge for 6 hours.

4. **Before starting the app or a build, clear what the last run left behind.** Trigger: a port is already in use, or a previous run may have been cancelled mid-flight (interrupted session, closed terminal, cancelled CI run). Action: find the process holding the port and stop that process only. The defaults are 8080 for the API, 5173 for Vite and 5432 for PostGIS.
   - macOS: `lsof -nP -iTCP:8080 -sTCP:LISTEN`, then `kill <pid>`
   - Windows (PowerShell): `Get-NetTCPConnection -LocalPort 8080 -State Listen`, then `Stop-Process -Id <pid>`
   - Then `./gradlew --stop` (`gradlew.bat --stop` on Windows) and `docker compose down`.

   Never kill by a broad pattern such as `pkill -f java` or `pkill -f node`. This is a personal laptop, and VS Code's own Java and TypeScript tooling runs on those processes. If the process isn't one this project started, ask Dimos before stopping it.

5. **When the same failure class hits twice, the third occurrence must be impossible in code.** Trigger: "this is the second time X broke us." Action: add the guard (a test, a check in the build, a CI step, a config value) in the same commit as the fix. If the guard can't live in code, add it to `CLAUDE.md` with its trigger. A postmortem note is not a safeguard.

6. **Escalate the observation layer when the aggregated view is ambiguous.** Trigger: a CI log says "failed" or "timed out" without naming the step or the cause. Action: read the failed step's full log (`gh run view <run-id> --log-failed`), then run that step's exact command locally with the same Java and Node versions as the workflow (for example `./gradlew build --info`, or `npm ci && npm run build`). If it passes locally and fails in CI, compare versions and environment, or rerun with `gh run rerun <run-id> --debug`. Fixing the wrong step burns CI runs.

7. **Separate the tactical fix from the strategic one.** Trigger: you've found both "what unblocks `main`, CI or the demo in 20 minutes" and "what makes this class of problem go away." Action: ship the tactical fix now. Record the strategic one as a named next step (a GitHub issue or a roadmap line), not an inline rewrite. Mixing the two is how a quick unblock turns into a broken `main`.

8. **If verification takes more than two minutes, don't wait in the foreground and don't hand it to Dimos.** Trigger: you've pushed a fix whose outcome only shows later (a CI run, a deploy, a scheduled feed fetch). Action: start `gh run watch <run-id> --exit-status` in the background so you're woken when it ends, or use a scheduled wake-up where the session supports it. Write down a concrete checklist for the verification step. Don't ask Dimos "did it work?"

9. **Write a handoff before a session boundary.** Trigger: context running low, switching between the Mac and the Windows laptop, or pausing in the middle of a change. Action: commit the current state (a WIP commit on a feature branch is fine) and push it. Put what's done, what's in flight and the next check in the commit message, the PR description or a GitHub issue. Claude Code's memory and anything else under `~/.claude` stay on one machine and don't sync. "I'll remember" doesn't survive the boundary.

10. **No concurrent edits to the same shared state.** Trigger: another session is working in the same checkout, pushing to the same branch, or running migrations against the same database. Action: take separate work, use a separate git worktree and branch, or wait. Two sessions editing one working copy, or two Flyway runs against one database, leave a state neither of them understands.

11. **Stop background processes when their job is done.** Trigger: the task that needed a long-running process (`./gradlew bootRun`, `npm run dev`, `docker compose up`, `gh run watch`, a polling loop, a `tail -f`) is finished. That includes when the approach was abandoned. Action: stop the process and verify it's gone (macOS: `pgrep -fl <name>`, Windows: `Get-Process`), and check that its port is free and its log has stopped growing. A forgotten dev server holds the port for the next session.

12. **Two failed quoting attempts on the same payload: switch mechanism.** Trigger: two tool calls in a row have failed on the same command or payload because of quote or escape mangling, e.g. PowerShell called from Git Bash, `curl -d` with a JSON body, or `python -c` / `node -e` with Danish letters (æ, ø, å) in the string. Rewording the command doesn't reset the counter, because it's the same payload until the mechanism changes. Action: stop adjusting quotes. Write the script or payload to a file with the `Write` tool and run the file by path. A third quoting variant is almost always the wrong turn.

## Where rules live

This file holds the general operating rules for any agent in this repo. Lessons about one part of the code (a feed's quirks, a flaky Testcontainers setup, a MapLibre workaround) go in `CLAUDE.md` or in a comment at the top of the affected file. Moving them here strips the context that makes them actionable.

## Delete criteria

A rule here gets deleted the moment any of these is true:

- It describes behaviour a competent agent already shows by default.
- It restates model-level training ("think carefully", "be honest", "don't make things up").
- Its trigger is "always" or "whenever appropriate", i.e. it has no trigger.
- A fresh agent reading it cold would not act differently on the next turn.

If you're tempted to add a rule and it fails any of these, the rule is narration. Cut it.
