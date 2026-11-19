# Build the invocation-point `launcher` module as a plain (clean, no shell-preamble) assembly JAR.
# `launcher` depends on flare-client (and, through it, flare-plugin) as PUBLISHED coordinates
# resolved from ~/.ivy2/local, so the libraries are published there FIRST — otherwise the launcher
# silently builds against whatever was last published (a release's jars, say, whose bytes then
# externalize to that release's download, and local changes never reach the executable).
# `clean flare.launcher` for the same reason as in `run`: the coordinate is fixed, so Mill's cached
# resolution would not notice the fresh publish.
assembly: publishLocal
	./mill clean flare.launcher
	./mill flare.launcher.assembly

# Releases are cut by tagging, not by make. Tag a commit CI has passed, with `git tag -s
# X.Y.Z && git push --tags`: the tag fires .github/workflows/release.yml, which runs the shared
# release.sh in propensive/.github. That gates on a signed tag, on CI already being green on that
# very commit, and on every pin being a release; publishes the library jars; repackages the
# executables against them; and generates the notes. If anything fails, the release and the tag
# are both deleted, so a retry is `git tag -d X.Y.Z && git tag -s X.Y.Z && git push --tags`. What
# this repository needs beyond the common path is declared in etc/release. This target survives
# only to say so.
release:
	@echo "Releases are triggered by tags, not by make. Once CI has passed on the commit:" >&2
	@echo "" >&2
	@echo "    git tag -s X.Y.Z && git push --tags" >&2
	@echo "" >&2
	@echo "See doc/releasing.md, and propensive/.github." >&2
	@exit 1

# Publish the libraries to the local ~/.ivy2 (the launcher resolves them from there; burdock will
# NOT externalize a locally-published copy unless its bytes match a release asset). This is also
# how a Soundness checkout picks up a local build of the plugin.
publishLocal:
	./mill flare.plugin.publishLocal + flare.records.publishLocal + flare.client.publishLocal

# Repackage the launcher assembly into a self-fetching launcher with Burdock. The
# `burdock.externalize` macro wrapping `flare.flare` (in src/launcher/flare_launcher.scala) has
# already embedded `META-INF/burdock.deps` at compile time; running the repackager rewrites the JAR
# in place so published dependencies become on-demand `Burdock-Require` URLs and unpublished ones
# are inlined from `~/.cache/burdock`.
#
# Four publication homes are consulted: Maven Central (hashes resolved via deps.dev) for the
# third-party dependencies, and — via the `--github` hints — the release assets of the flare,
# Pyrocosm, Soundness and proscala repositories, whose per-jar SHA-256 digests the repackager
# matches against the classpath. Set GITHUB_TOKEN to lift the API rate limit.
flare.jar: assembly
	cp out/flare/launcher/assembly.dest/out.jar flare.jar
	java -cp flare.jar soundness.repackage --github propensive/flare,propensive/pyrocosm,propensive/soundness,propensive/proscala

# Package the repackaged JAR as a native executable for this machine with the pinned `xek` builder
# (fetched into dist/xek and verified against etc/xek.tsv), requiring Java 25 as releases do.
flare: flare.jar xek-fetch
	dist/xek build --java-min 25 --java 25 flare.jar flare

# Fetch the pinned `xek` builder into dist/xek.
xek-fetch:
	./etc/shared xek-fetch.sh

install: flare
	cp flare ${HOME}/.local/bin/

# Run flare locally WITHOUT a release: publish the libraries, assemble the launcher, and run it
# directly (no burdock repackage, so the local library jars are simply bundled). Arguments go in
# ARGS, e.g. `make run ARGS='metrics --dry-run'`.
run: assembly
	java -jar out/flare/launcher/assembly.dest/out.jar $(ARGS)

# Compile and run the test suite with fume, which discovers the suite from the assembly named in
# .pyrocosm/fume/config.tel (relative to this directory). Extra selection terms go in TESTS.
# CI runs the same command (the shared workflow installs the fume pinned in etc/tools).
# `make test-plain` is a fume-less fallback: `flare.runTests` (src/test/flare_test_main.scala)
# drives `Tests.invoke` in-process.
# Temporary files and directories are galilei's `Scratch` loans, which delete what they made when
# their scope ends, however it ends: never the JDK's `createTempFile`/`createTempDirectory`, whose
# leftovers a long-lived JVM (a test run inside fume's daemon) keeps until it exits, and never
# `deleteOnExit`, which waits for that exit too.
check-scratch:
	! grep -rnE --include='*.scala' 'createTemp(File|Directory)|deleteOnExit' src

test:
	./mill flare.test.assembly
	fume run -c out/flare/test/assembly.dest/out.jar $(TESTS)

test-plain:
	./mill flare.test.assembly
	java -cp out/flare/test/assembly.dest/out.jar flare.runTests

# Check every source against Consequent Style and the project's own rules with the RELEASED flare
# (pinned in etc/tools; `make tools` installs it — the tool rule: what a repository runs is a
# release, never the build under test), as configured in
# .pyrocosm/flare/config.tel. The sources check clean, but CI does not run this; PATHS restricts
# the check to files beneath them.
check: check-scratch
	flare check $(PATHS)

# Install the commands pinned in etc/tools (fume) through their releases' installers.
tools:
	./etc/shared tools.sh

# Publish HEAD's libraries as a snapshot — a `snapshot-<hex>` pre-release named by the filtered
# tree of the commit, at version `<next version>-<hex>` — for a dependent repository to pin in
# its etc/refs before the next release. `LOCAL=1` stages and installs without publishing.
# The last line printed is the pin. See snapshot.sh in propensive/.github.
snapshot:
	./etc/shared snapshot.sh flare "$$(./mill show flare.client.publishVersion | tr -d '"')"

# Delete snapshot pre-releases older than DAYS (default 60) days.
snapshot-prune:
	./etc/shared snapshot-prune.sh flare $(DAYS)

dev:
	./mill -w flare.client.compile

.PHONY: check check-scratch xek-fetch tools snapshot snapshot-prune assembly release publishLocal run test test-plain dev install
