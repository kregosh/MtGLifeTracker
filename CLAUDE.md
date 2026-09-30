# MtG Life Tracker — Claude Code notes

## Git workflow

- **Never commit directly to `main`.**
- Create a feature branch for every task: `git checkout -b feature/<short-description>`
- Commit all work on the feature branch, then open a PR against `main`.
- Push the branch and open the PR with `mcp__github__create_pull_request`.

## Credentials

- `app/google-services.json` is gitignored. Credentials are injected in CI via the `GOOGLE_SERVICES_JSON` GitHub Actions secret (raw JSON, not base64).
- Never hardcode Firebase URLs or API keys in source files.
