# Project AI Instructions

Follow `~/ai-instructions/global.md` when available; the project rules below
take precedence. Routine work uses proportionate planning and preserves existing
changelog history. Release indexing is optional.

## Working directory and Git

- Work on `dev`. Inspect the current path, branch, status, and worktree list
  before editing; preserve all existing user changes.
- Use the main checkout on `dev` when available. If another checkout must stay
  on a different branch, use a persistent sibling worktree, such as
  `/home/tom/github.com/androsaver-dev`. Do not create ongoing worktrees in `/tmp`.
- Locate existing work before creating or restoring a worktree. Never move or
  remove a dirty worktree without preserving its changes and checking the target.
- State the actual working directory and branch when they differ from the
  user's current checkout. Link changed files using their actual absolute paths.
- Never commit unless requested. Never update `main` or `master`, merge a
  release, push, or publish without authorization for that action.
- Keep `AGENTS.md` version-controlled as the single project instruction source.
  `.ai/` is optional local session context, not a second instruction source.
  Worktrees should use the instructions on their own branch; do not maintain
  manually duplicated rule files. Never edit compiled global client instructions.

## Proportionate workflow

- Default to inspect → bounded change → focused verification → diff review →
  concise report. The main agent may implement routine changes directly.
- Routine documentation, branch cleanup, and straightforward subtree imports
  do not require OpenSpec artifacts, PLAN.md, research documents, or subagents.
  File count alone does not make a mechanical upstream import complex.
- Use formal planning for substantial design or behavioral changes. Delegate
  only when independent work benefits enough to justify briefing and review.
  Do not invoke another model just to satisfy inherited orchestration rituals.
- Use an existing OpenSpec workflow when relevant and available. If it is
  missing, report that once when material; do not install or initialize it
  automatically or block routine maintenance on it.
- Read existing `.ai` handoffs and TODOs when relevant. Treat them as historical
  context until verified against Git and the filesystem. Missing `.ai` files
  are not blockers and do not require recreating an entire tracking system.
- During long work, keep one existing session/handoff note compact and current.
  Avoid duplicate task ledgers.
- Question stale or conflicting instructions. Use current evidence to resolve
  routine choices and explain consequential departures briefly. Ask only when
  unresolved ambiguity affects scope, data preservation, or authorization.
  Do not bypass higher-priority rules or security and Git protections.

## psysuals subtree

- Confirm the configured upstream repository, intended branch, last recorded
  subtree split, and upstream commit before importing; never assume cached refs
  are current. Fetch without updating protected local branches.
- A reference-source import and an Android Kotlin backport are separate scopes.
  Do not infer a native port, version bump, build, or publication from a request
  to update the subtree.
- Use the established squash-subtree workflow when commits are authorized,
  preserving `git-subtree-dir` and `git-subtree-split` provenance.
- Without commit authorization, prepare and verify the exact file update, then
  report it as an uncommitted import with provenance still pending. Record the
  previous and target upstream SHAs in the changelog; do not claim a completed
  subtree merge or make an automatic commit. Before later committing, integrate
  it through the subtree workflow rather than an ordinary file-only commit.
- Verify the complete imported inventory, contents, and executable modes against
  the upstream tree, including added/deleted files. Preserve unrelated changes.
- Run the available Python unit and headless smoke checks. Report skips and
  distinguish CPU checks from real GL rendering and Android/device validation.

## Cleanup

- Before branch deletion, refresh remote state and check ancestry against the
  intended integration branch, worktree use, and open pull requests.
- Run merged-branch deletion from the integration worktree; checking against
  `dev` and deleting from `master` can give different results. Use safe deletion;
  investigate a rejection instead of immediately forcing it.
- A general cleanup request covers clearly obsolete, fully merged topic branches.
  Preserve release branches/tags, active worktrees, and stashes with uncertain
  contents. Unmerged branch deletion or stash disposal needs specific approval.
- Do not remove old submodule configuration solely because another branch uses
  a subtree; first check whether retained branches/checkouts still need it.

## Documentation and verification

- Keep changelog edits narrowly tied to the task. Preserve the existing format
  and historical entries; do not migrate or archive history during routine work.
  Changelog-format migration is a separate, explicitly requested task.
- Run the shared changelog checker where applicable. If a pre-existing format
  fails it, report the mismatch instead of expanding scope to rewrite history.
- Do not upgrade dependencies, refresh lockfiles, or install test dependencies
  automatically. Use the existing package manager/environment and report gaps.
- Do not run local Java/Gradle builds. Use existing GitHub workflows for Android
  builds/tests when authorized; inspect their triggers before pushing because
  a push may also publish artifacts.
- For instruction-only changes, review the diff and check consistency; application
  tests are unnecessary. Report files changed, checks actually run, limits, and
  whether work is uncommitted, committed, pushed, or published.

## Release work

- A release requires explicit authorization; routine maintenance is not a release.
- Verify current version ownership and workflow targets before acting. Currently
  `app/build.gradle` owns versionCode/versionName; CI builds `dev` and `master`.
  Check these facts again rather than relying on old handoffs.
- Before an authorized release, state the concrete version, branch targets,
  publication effects, and verification sequence. One explicit instruction to
  execute that stated release sequence authorizes its listed commits, pushes,
  merges and publication; do not ask again at every step. A request to prepare
  a release or update a subtree does not authorize shipping it. Inspect CI push
  triggers before acting. Never assume the release branch is named `main`.
- Run QMD indexing only when a configured index is relevant to the requested
  work. It is not a mandatory release prerequisite.
