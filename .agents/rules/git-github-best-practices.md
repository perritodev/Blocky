# 🏛️ The Supreme Git & GitHub Master Ruleset
### Global Directive for Autonomous Agent Version Control & Repository Governance

This document establishes the highest-standard protocol for all Git version control operations, GitHub remote management, and codebase governance across every language, framework, and project ecosystem. Adhere strictly to these rules without exception.

---

## 1. 🔒 Absolute Security & Secrets Protection (Zero-Leakage Guarantee)

The agent must treat credential and secret leakage as a fatal security violation.

### 1.1 Prohibited File Extensions & Patterns
The following files and patterns must **NEVER** be tracked, staged, or committed under any circumstance:
- **Private Keys & Certificates:** `*.pem`, `*.key`, `*.jks`, `*.keystore`, `*.p12`, `*.pfx`, `id_rsa*`, `id_ed25519*`, `*.cer`, `*.crt`.
- **Environment & Secret Files:** `.env`, `.env.*` (e.g., `.env.local`, `.env.production`), `keystore.properties`, `local.properties`, `credentials.json`, `service-account*.json`, `google-services.json` (if containing private keys), `token.txt`, `secrets.yaml`.
- **Authentication Tokens & API Keys:** AWS credentials (`~/.aws/`), GCP tokens, GitHub PATs (`ghp_*`), OpenAI/Anthropic/Gemini API keys, Stripe/PayPal secret keys.

### 1.2 Pre-Staging Verification Protocol
- **No Blanket Staging:** Never execute `git add .` or `git add -A` blindly.
- **Inspect Untracked Files:** Always run `git status -u` prior to staging to verify every individual file.
- **Explicit Target Staging:** Explicitly stage modified files by path (e.g., `git add app/src/MainActivity.kt package.json`).
- **Exclusion Check:** If a sensitive file is detected, verify it is listed in `.gitignore` or `.git/info/exclude` *before* proceeding with any Git operation.

---

## 2. 🌿 Continuous Repository Synchronization & Target Branch Governance

### 2.1 Continuous Comparison & Workspace Sync
- **Active Diff Comparison:** For every code change or feature development cycle, the agent must compare modified files against the repository state using `git status -u`, `git diff`, or `git diff --staged` before proceeding.
- **Mandatory Repository Updates:** Keep the repository synchronized with application development progress. Ensure all valid changes are properly tracked, staged by path, and committed in clean atomic units.

### 2.2 Target Destination Inquiry Protocol (Main vs. Branch)
- **Mandatory User Prompting:** Whenever application changes are ready to be committed and pushed, the agent **MUST ALWAYS ask the user** whether the changes should be applied directly to **production `main`** or to a dedicated **feature/fix branch**.
- **Branch Strategy Execution:**
  - If the user designates a **branch** (e.g., `feature/<name>`, `fix/<name>`): create/checkout the branch, commit changes, and push with upstream tracking (`git push -u origin <branch-name>`).
  - If the user designates **production `main`**: verify that code compiles cleanly and passes all local checks before updating and pushing to `main`.

### 2.3 Main Branch Inviolability & Production Stability
- Unfinished, experimental, or broken builds must **never** be pushed to `main`.
- The `main` / `master` / `release` branch must always reflect **stable, passing, production-ready code**.

### 2.4 Semantic Branch Naming Convention
All branches must follow standardized kebab-case prefixes:
| Prefix | Purpose | Example |
| :--- | :--- | :--- |
| `feature/` | New user capabilities, features, or UI workflows | `feature/call-screening-filters` |
| `fix/` | Bug fixes, crashes, memory leaks, or error patches | `fix/null-pointer-on-contact-lookup` |
| `refactor/` | Code refactoring without behavior modification | `refactor/database-repository-layer` |
| `perf/` | Performance optimizations and load-time improvements | `perf/r8-shrinking-optimization` |
| `docs/` | Documentation, guides, README, and API specs | `docs/sideloading-guide-update` |
| `chore/` | Build scripts, dependencies, CI/CD, or tool configs | `chore/bump-compose-bom-2026` |
| `test/` | Adding, fixing, or updating unit/integration tests | `test/add-dao-room-unit-tests` |

### 2.5 Branch Synchronization
- Before creating a new branch or merging, fetch all remotes: `git fetch --all --prune`.
- Keep feature branches updated against `origin/main` using clean rebasing: `git pull --rebase origin main`.

---

## 3. 📝 Strict Conventional Commits Standard (v1.0.0)

Every commit must be atomic, buildable, and adhere to the [Conventional Commits](https://www.conventionalcommits.org/) specification.

### 3.1 Structure
```text
<type>(<optional scope>): <imperative summary in lowercase>

[optional body: detailed explanation of the problem, solution, and trade-offs]

[optional footer(s): Closes #123, Refs #456, BREAKING CHANGE: ...]
```

### 3.2 Commit Types
- `feat`: A new feature exposed to the end user.
- `fix`: A bug fix for the user or system.
- `refactor`: Internal code restructurings that neither fix bugs nor add features.
- `perf`: Code changes that measurably improve CPU, memory, or rendering performance.
- `docs`: Documentation updates only (README, markdown, docstrings).
- `style`: Formatting, whitespace, linting fixes (no functional logic changes).
- `test`: Adding missing tests or correcting existing tests.
- `chore`: Maintenance tasks, Gradle/npm dependency updates, build tooling.
- `build`: Changes affecting the build system (Gradle, Vite, Webpack, CMake).
- `ci`: Changes to CI/CD workflows and configuration scripts.

### 3.3 Atomic Commit Discipline
- **One Logical Unit:** A commit must represent exactly one self-contained logical change.
- **Never Mix:** Do not combine unrelated refactors, dependency updates, and feature additions into a single monolithic commit.
- **Zero Broken Commits:** Every commit must compile and pass tests (`./gradlew test`, `npm test`, `pytest`, etc.).

---

## 4. 🚀 Remote Operations, Push Etiquette & GitHub CLI (`gh`)

### 4.1 Upstream Branch Tracking
- When pushing a new branch for the first time, always set the upstream tracking branch:
  ```bash
  git push -u origin <branch-name>
  ```

### 4.2 Force-Push Restrictions
- **Shared Branches (`main`, `master`, `develop`):** `git push --force` or `-f` is **STRICTLY PROHIBITED**.
- **Personal Feature Branches:** If branch history was rewritten (rebase/squash), use `--force-with-lease` exclusively:
  ```bash
  git push --force-with-lease origin <feature-branch>
  ```

### 4.3 GitHub CLI (`gh`) Integration
- Leverage `gh` for standard GitHub automation:
  - **Pull Request Creation:** `gh pr create --title "..." --body "..."`
  - **Release Publication:** `gh release create <tag> <assets...> --title "..." --notes "..."`
  - **Issue Management:** `gh issue list`, `gh issue view <id>`

---

## 5. 📦 Automated Release Governance, Versioning & Asset Distribution

### 5.1 Mandatory Version Increment & Continuous Release Synchronization
- **Continuous Version Tracking:** For every compile/release milestone, the agent **MUST** increment the project version code and semantic version name (`versionCode`, `versionName = "vMAJOR.MINOR.PATCH"`) in the build configuration (e.g. `app/build.gradle.kts`).
- **Release Build Compilation:** Compile production release distribution artifacts (`./gradlew assembleRelease`) signed with official keys.
- **Continuous GitHub Release Publishing:** Every production milestone must publish/update the official GitHub Release using `gh release create <tag> <assets...> --title "..." --notes "..."` or `gh release upload <tag> <assets...> --clobber`.

### 5.2 Semantic Versioning (SemVer 2.0.0)
Tag all release milestones with standard `vMAJOR.MINOR.PATCH` format:
- `MAJOR` (v2.0.0): Incompatible API or structural breaking changes.
- `MINOR` (v1.1.0): Backward-compatible new functionality.
- `PATCH` (v1.0.1): Backward-compatible bug fixes and security patches.

### 5.3 Release Asset Protocol
- Ensure all distribution artifacts (`.apk`, `.aab`, `.zip`, `.exe`, `.tar.gz`) are compiled in **Release mode** and properly code-signed with official keys before uploading.
- Use `--clobber` when replacing existing release assets:
  ```bash
  gh release upload v1.0.1 "app/build/outputs/apk/release/Blocky.apk" --clobber
  ```

---

## 6. 🛡️ Conflict Resolution, Merge Safety & History Preservation

### 6.1 Merge & Rebase Conflict Protocol
1. **Never Discard Blindly:** When conflicts arise, thoroughly analyze both incoming and local changes.
2. **Post-Resolution Build Verification:** Run the full build suite and linter immediately after resolving conflicts *before* finalizing the commit:
   ```bash
   # Android
   ./gradlew assembleDebug test
   # Node.js
   npm test && npm run build
   ```
3. **Preserve History:** Never delete `.git` logs, reset without backup branches, or execute destructive commands without explicit user alignment.

---

## 7. 🧹 Workspace Hygiene & Universal Exclusion Policy

### 7.1 Universal Build & Cache Exclusions
The agent must verify that transient build caches and OS artifacts are excluded in `.gitignore` or `.git/info/exclude`:
- **Android / JVM:** `.gradle/`, `build/`, `app/build/`, `*.apk`, `*.aab`, `local.properties`, `keystore.properties`, `*.jks`.
- **Node / Web:** `node_modules/`, `.next/`, `dist/`, `out/`, `build/`, `.npm/`, `.cache/`.
- **Python:** `__pycache__/`, `.venv/`, `venv/`, `*.pyc`, `.pytest_cache/`, `*.egg-info/`.
- **Operating System & IDEs:** `.DS_Store`, `Thumbs.db`, `.idea/`, `.vscode/`, `*.iml`.
