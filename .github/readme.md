# Flare

**Flare** is a linter for Scala 3, in the same family as [fume](https://github.com/propensive/fume)
and [flame](https://github.com/propensive/flame). It checks sources against **Consequent Style**
— a syntax and formatting standard whose rules are *derived from principles about how code is
read* rather than applied by a reformatter — and against a project's own rules, defined in a
few lines of TEL. It runs as a command, `flare`, and as a compiler plugin, and it keeps a census
of a project's rules in git notes so that the counts can be followed commit by commit.

- [The standard](/doc/consequent-style.md)
- [Rule reference](/doc/rules/)
- [Releasing](/doc/releasing.md)

## Installing

```sh
curl -fsSL https://propensive.dev/flare | sh
```

installs the `flare` executable for your platform (macOS or Linux, x64 or arm64) into
`~/.local/bin` (or `$FLARE_INSTALL_DIR`), verifying it against the digest published with the
release. Each [GitHub release](https://github.com/propensive/flare/releases) also carries the
per-platform executables, the `flare` polyglot bootstrap script, and the library jars. The
first run fetches flare's externalized dependencies; later runs start instantly. `flare install`
adds tab-completions and a manpage to your shell.

## Configuration

Flare reads `.pyrocosm/flare/config.tel` from the working directory or the nearest ancestor
holding one, so it can be run from anywhere inside a project. `.pyrocosm` is the project's
shared directory, one subdirectory per tool. The file defines the project's **rules** and the
**profiles** that check them:

```
tel 1.0

# ── Rules ────────────────────────────────────────────────────────────────────────────────────
# A rule is a named detection over the parse tree. Each child line is a predicate; the rule
# matches wherever any predicate does. A rule is checked only where a profile enforces it, but
# `flare metrics` counts every rule's matches regardless, so a rule may exist purely to be
# measured.

rule cast
  invokes asInstanceOf
  invokes isInstanceOf
  message  Casts bypass the type checker; prefer a pattern match

rule unsafe
  invokes unsafely
  annotates unsafeAssumePure
  invokes unsafe*                 # every unsafe-prefixed call, counted under this rule
  severity error                  # when enforced; warn otherwise

rule imperative
  uses var
  uses while
  uses throw
  uses catch-all

rule compound                     # multi-word names in the umbrella package, as a census
  exports /^[A-Z].*[a-z0-9][A-Z]/ # a `/regex/` target, found anywhere in the name
  exclude-name JsonPointer Media* # names the rule leaves alone: not reported, not counted

# ── Profiles ─────────────────────────────────────────────────────────────────────────────────
# What `flare` checks. With one profile, `flare` runs it; with several, `flare <name>`.

profile default
  source lib/*/src/**/*.scala     # globs relative to the project root; `**` spans directories
  source src/**/*.scala
  exclude lib/*/res/**
  language experimental.modularity          # -language features, as the build passes them
  language experimental.captureChecking
  notes flare                               # the git notes namespace (this is the default)

  style consequent                # the formatting standard, with its refinements beneath
    header etc/header.txt         # the licence header every file must begin with, verbatim
    columns 100
    umbrella soundness            # the umbrella re-export package (enables L4, L5 and L6)
    module-root lib               # the segment below which each module has its own directory
    interpolator m                # interpolators whose interior whitespace is insignificant
    omit L4                       # leave a rule out (or a whole principle: `omit S`)
    strict F4                     # report a rule as an error
    severity warn                 # the default severity of the style's rules: warn | error
    gate Unsafe                   # S1: a `using` parameter of type Unsafe must pair with a
      prefix unsafe               # name beginning `unsafe`, and vice versa; repeatable

  enforce cast                    # a rule this profile checks
  enforce unsafe
    severity warn                 # overriding the rule's own severity, for this profile
```

### Predicates

Every predicate names one shape on the untyped parse tree (or, for `matches`, on the text), so
no rule needs the type checker. Names are matched on their simple (last) segment and may use
`*` as a wildcard; a target written between slashes, `/[a-z][A-Z]/`, is instead a regular
expression, found anywhere in the name.

| Keyword | Matches |
| --- | --- |
| `invokes <name>` | a method reference in call or selection position: `x.name(…)`, `name(…)`, infix `a name b`, a bare `x.name` |
| `references <name>` | any identifier or selection naming `<name>`, including type positions and imports |
| `annotates <Name>` | an annotation `@Name` on a definition or expression |
| `instantiates <Type>` | `new Type(…)`, or `Type(…)` where `Type` is capitalised |
| `extends <Type>` | a class, trait, object or enum with `Type` among its parents |
| `derives <Type>` | a `derives Type` clause |
| `imports <path>` | an import of `path` or anything beneath it |
| `defines <name>` | a `def`, `val`, `var`, `given`, `type`, class, trait, object or enum named `<name>` |
| `overrides <name>` | an `override` definition named `<name>` |
| `exports <name>` | a name an export surface puts into the umbrella package (the rename, for `x as y`), outside its `package <name>:` blocks; only with `umbrella` set |
| `uses <construct>` | a construct: `var`, `null`, `throw`, `return`, `while`, `try`, `catch-all`, `implicit`, `lazy`, `abstract-type`, `wildcard-import`, `by-name`, `default-argument`, `symbolic-name` |
| `matches <regex>` | a regular expression over each source line |

A rule may also carry a `message` (shown in place of the default) and a `severity` (`warn`,
the default, or `error`). A profile's `enforce` may override the severity. An `exclude-name`
line lists names — globs or `/regex/`es, any number to a line, the line repeatable — that the
rule leaves alone: a match on one of them, the name an `invokes`, `defines`, `exports` (or any
other name predicate) matched, is neither reported nor counted. It is how a census rule records
the exceptions a project has ruled on, in one reviewable place.

## Running

| Command | What it does |
| --- | --- |
| `flare` / `flare <profile>` / `flare check [profile] [paths…]` | checks the profile's sources; paths restrict the check to files beneath them |
| `flare metrics [profile]` | counts every rule and gate across the profile's sources and records the census in git notes |
| `flare metrics [profile] --show <commit\|tree>` | prints the census recorded for a commit or input tree |
| `flare options [profile]` | prints the `-P:flare:` options equivalent to the profile, for the compiler plugin |
| `flare rules [profile]` | lists the style's rules and the project's, with their severities |
| `flare install` | installs tab-completions and the manpage |
| `flare about` | shows flare's version, its daemon, and the configuration files it found |
| `flare quit` | stops the background daemon |
| `flare --version` | prints flare's version |

`check` reports each finding with a highlighted excerpt, then a table of the rules that fired.
While it runs in a terminal, it shows a board — Pyrocosm's `Board`, the same one fume shows a
test run on — whose content panel scrolls the findings as each file is checked and whose status
panel gauges how many files have been parsed and then checked; the report is printed once the
board closes. `metrics` shows the same board for parsing and counting, listing each file's
counts as they arrive. With `--terse`, or under GitHub Actions, or when the output is not a
terminal, it prints one `path:line:column: level: [rule] message` line per finding instead,
with no board. It exits non-zero when any error-severity rule fired
(status 1) or a source failed to parse (status 5).

The compiler is invoked on the profile's sources and stopped after the last phase any enabled
rule needs — the parser, for every rule so far — so a check costs a parse and needs no
classpath.

### The census and git notes

`flare metrics` counts, per file, the matches of every rule the configuration defines (enforced
or not) and, for every gate, the definitions it gates and those that claim it without being
gated (`<prefix>-gate`, `<prefix>-ungated`). None of this is a violation; the question it
answers is how much code sits behind a project's escape hatches and whether that is growing.

The result is recorded in git notes under `refs/notes/flare` (or the profile's `notes`
namespace) in two parts: the census itself, a TEL document attached to the **git tree object of
exactly the input files**, so identical inputs across commits share one measurement and are
never measured twice; and a pointer note appended to the HEAD commit naming the profile, the
tree and the time, which is what a walk of the history follows to plot a series. `--dry-run`
prints the census without writing; `--force` replaces an existing tree note. Notes are shared
like any ref:

```sh
git push origin refs/notes/flare
git fetch origin refs/notes/flare:refs/notes/flare
```

The input tree is a dangling object that `git gc` may prune; the notes survive, and `--show`
reads them through the notes ref itself.

## The compiler plugin

The same rules run inside the compiler, as `dev.propensive:flare-plugin`. The plugin has no
TEL parser — it is loaded into the compiler that builds Soundness, so it depends on nothing but
the compiler — and takes its configuration as `-P:flare:` options, which `flare options` prints
from the project's profile:

```scala
def scalacPluginMvnDeps = Seq(mvn"dev.propensive:flare-plugin:$version")
def scalacOptions = super.scalacOptions() ++ os.proc("flare", "options").call().out.lines()
```

Violations are reported as warnings, or errors where the profile says so. The option syntax is
the plugin's own: `;` separates a list's items (the compiler splits `-P` on commas before the
plugin sees them) and `:` separates a value's fields — `header=<file>`, `columns=<n>`,
`umbrella=<pkg>`, `moduleRoot=<segment>`, `language=a;b`, `interpolators=m;j`,
`gate=Unsafe:unsafe`, `strict=F4;S`, `omit=L4`, `rule=cast:invokes:asInstanceOf`,
`rule=cast:message:<text>`, `rule=cast:severity:error`, `rule=cast:exclude-name:a;b*`,
`enforce=cast` or `enforce=cast:error`, and `errors`.

## Modules

`plugin` and `client` are released to GitHub Releases under `dev.propensive` (as
`flare-plugin` and `flare-client`), using the same publishing settings as Soundness.

- **`plugin`** — the compiler plugin and the lint engine behind it: Consequent Style's rules,
  the predicate engine for project rules, and the census. Plain Scala against the compiler
  alone, with no Soundness dependency.
- **`client`** — the command's library: the configuration reader, the compiler front end,
  the [Pyrocosm](https://github.com/propensive/pyrocosm) report, and the git notes.
- **`launcher`** — the invocation point, alone in its own module: just
  `@main def flare = externalize(runClient())`. It depends on `client`/`plugin` as **published
  coordinates** (resolved from `~/.ivy2/local`), so Burdock's `externalize` records their jar
  hashes and the repackager turns them into on-demand downloads rather than inlining them.
- **`test`** — a [Probably](https://github.com/propensive/probably) test suite.

## Building

```sh
make run ARGS='rules'        # publish libs locally, build & run the command (no release needed)
make test-plain              # compile and run the test suite
make test                    # the same, through fume
make flare                   # assemble, repackage with Burdock, emit the `flare` executable
make install                 # copy it to ~/.local/bin
# A release is cut by tagging, not by make: `git tag -s X.Y.Z && git push --tags`.
# See doc/releasing.md.
```

The build compiles with the [proscala](https://github.com/propensive/proscala) fork of the Scala
compiler (the toolchain Soundness itself is built with), downloaded on demand from its GitHub
release and cached under `~/.cache/soundness/proscala`. Soundness and Pyrocosm are resolved as
per-component jars from `~/.ivy2/local`, installed from their GitHub Releases by `make
sync-deps` at the versions pinned in `etc/refs` (or by `make publishLocal` in a checkout).

Flare checks itself: `.pyrocosm/flare/config.tel` applies Consequent Style to its own sources.

## License

Flare is made available under the [Apache 2.0 License](/LICENSE).
