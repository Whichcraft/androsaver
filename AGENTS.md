# AndroSaver AI Instructions

Follow `~/ai-instructions/global.md` when available; these project rules take
precedence. Keep this file tracked and avoid duplicate instruction files.

## Workspace and scope

- Work on `dev` in the primary checkout `/home/tom/github.com/androsaver`.
  Verify path, branch, status and worktrees before editing; preserve user changes.
  Use a persistent worktree only when separate concurrent work requires one.
- Default to inspect, bounded change, focused verification and diff review.
  Routine work needs no formal planning documents or delegation. Use configured
  OpenSpec and existing task/handoff files only when useful; verify old notes.
- Question stale rules against current evidence. Explain consequential choices;
  ask only when scope, data preservation or authorization remains unclear.
- Never update `main`/`master` without explicit permission. Commit/push only when
  requested. One authorized release sequence covers its stated steps; preparation
  alone does not authorize shipping. Inspect CI triggers: pushing `dev` publishes
  dev artifacts; pushing `master` publishes stable artifacts.

## Subtree and cleanup

- `psysuals/` is reference source; importing it does not authorize an Android port.
  Fetch the intended upstream and use the existing squash-subtree workflow when
  commits are authorized. Preserve `git-subtree-dir`/`git-subtree-split` metadata.
  Without commit authorization, report an uncommitted import with provenance pending.
- Preserve Mycelium's existing name and registry slot (zero-based index 16,
  between Lattice and Magnetar), including both settings arrays.
- Record previous/target SHAs and verify the complete imported tree against upstream,
  including file inventory, contents and executable modes. Preserve unrelated edits.
- Before deleting merged topic branches, check remote state, open PRs, worktree use
  and ancestry against the integration branch; delete from that branch's worktree.
  Preserve release refs, stashes and unmerged work unless specifically authorized.
  Check retained branches before removing old submodule metadata.

## Verification and delivery

- Do not run local Java/Gradle builds. Android verification uses GitHub Actions.
  Python imports use available unit/headless smoke checks; report skipped checks
  and distinguish CPU, real GL, emulator and physical-device results.
- Do not upgrade dependencies or refresh lockfiles automatically. Documentation
  needs diff/consistency checks, not application tests.
- Preserve changelog history and format. Run
  `~/ai-instructions/bin/check-changelog --legacy .` for this repository; use the
  strict default in projects using the shared format. Never migrate history as
  a side effect of routine work.
- Before publishing a new app commit, increase source `versionCode`/`versionName`
  in `app/build.gradle` and verify the exact commit via CI. QMD is not a mandatory
  release step. Report changed files, checks, limits and commit/push/publication state.
