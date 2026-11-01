# Git workflow

## The basic model

Git moves changes through three places:

```text
Working folder  --git add-->  Staging area  --git commit-->  Local history  --git push-->  GitHub
```

Staging lets you choose exactly what goes into each commit. Nothing reaches GitHub until you push.

## Initialise a repository

```bash
git init -b main
```

This creates the hidden `.git` folder, which holds the whole history. It only needs doing once per project.

## Stage and commit

```bash
git status
git add src/main/java/com/opstrack/service/IncidentService.java
git diff --staged
git commit -m "Validate incident severity on update"
git log --oneline
```

- `git status` shows what changed and what is staged.
- `git diff --staged` shows exactly what the commit will contain. Read it every time.
- A good commit message says what the change does, in the imperative: "Add rate limiting", not "changes".

To unstage a file without losing your edits, run `git restore --staged <file>`.

## Branch and merge

`main` should always work, so make each change on its own branch:

```bash
git switch -c feature/add-incident-search
git add .
git commit -m "Add incident search"
git switch main
git merge feature/add-incident-search
git branch -d feature/add-incident-search
```

`git switch -c` creates a branch and moves to it. `git branch -d` deletes a branch only if it is already merged, so it is safe.

## Pull requests

A pull request (PR) asks to merge a branch on GitHub, where it can be reviewed and CI can test it first.

```bash
git switch -c feature/add-incident-search
git add .
git commit -m "Add incident search"
git push -u origin feature/add-incident-search
```

Then:

1. Open the repository on GitHub and choose **Compare & pull request**.
2. Describe what changed and why.
3. Wait for the CI check to pass. If it fails, read the log, fix the problem and push again. The PR updates by itself.
4. Merge the PR.
5. Update your local copy:

```bash
git switch main
git pull
git branch -d feature/add-incident-search
```

## Resolve a merge conflict

A conflict happens when two branches change the same lines, and Git cannot tell which version you want.

```text
$ git merge feature/change-title
Auto-merging notes.txt
CONFLICT (content): Merge conflict in notes.txt
Automatic merge failed; fix conflicts and then commit the result.
```

Git marks the conflict in the file:

```text
<<<<<<< HEAD
the version on your current branch
=======
the version on the branch being merged
>>>>>>> feature/change-title
```

To resolve it:

1. Run `git status` to list the conflicted files.
2. Edit each file so it contains the correct final text, and delete all three marker lines.
3. Mark it as resolved and finish the merge:

```bash
git add notes.txt
git commit --no-edit
```

To abandon the merge and return to where you started, run `git merge --abort`.

If tests exist, run them before committing a resolved conflict. A merge can leave text that looks right but does not work.

## Check for secrets before publishing

A pushed secret must be treated as exposed, even if you delete it straight away, because it remains in the history.

```bash
git ls-files
git grep --cached -nIE "AKIA[0-9A-Z]{16}|BEGIN [A-Z ]*PRIVATE KEY|ghp_[A-Za-z0-9]{20,}|(password|secret|api[-_]?key)[\"' ]*[:=]"
git check-ignore -v .env
```

- `git ls-files` lists every file Git is tracking.
- `git grep --cached` searches what is staged. Review each match; only placeholders, variable references and test values are acceptable.
- `git check-ignore -v .env` confirms `.env` is ignored.

## Line endings and Windows

- `.gitattributes` keeps `mvnw` on Linux line endings, so it runs on Linux.
- Windows has no executable permission, so mark `mvnw` as executable in Git, or Linux CI fails with `Permission denied`:

```bash
git update-index --chmod=+x mvnw
git ls-files -s mvnw
```

The second command should print a line starting with `100755`.
