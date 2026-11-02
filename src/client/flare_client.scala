                                                                                                  /*
┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓
┃                                                                                                  ┃
┃                                                                                                  ┃
┃                                                                                                  ┃
┃                                            F L A I R                                             ┃
┃                                                                                                  ┃
┃                                       a linter for Scala 3                                       ┃
┃                                                                                                  ┃
┃                                                                                                  ┃
┃                                                                                                  ┃
┃                                                                                                  ┃
┃   Flare, version 0.2.1.                                                                          ┃
┃   © Copyright 2025-26 Jon Pretty, Propensive OÜ.                                                 ┃
┃                                                                                                  ┃
┃   The primary distribution site is:                                                              ┃
┃                                                                                                  ┃
┃       https://propensive.dev/flare/                                                              ┃
┃                                                                                                  ┃
┃   Licensed under the Apache License, Version 2.0 (the "License"); you may not use this           ┃
┃   file except in compliance with the License. You may obtain a copy of the License at            ┃
┃                                                                                                  ┃
┃       https://www.apache.org/licenses/LICENSE-2.0                                                ┃
┃                                                                                                  ┃
┃   Unless required by applicable law or agreed to in writing, software distributed under          ┃
┃   the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF            ┃
┃   ANY KIND, either express or implied. See the License for the specific language                 ┃
┃   governing permissions and limitations under the License.                                       ┃
┃                                                                                                  ┃
┃                                                                                                  ┃
┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛
                                                                                                  */
package flare

import scala.caps

import soundness.*
import backstops.silentBackstop
import dysasymptotics.linearSize
import interpreters.posixInterpreter
import probates.cancelProbate
import pyrocosm.Boards.show
import pyrocosm.standardRendering.given
import pyrocosm.{ambient, Block, Board, Inline, Invoker, Status as Gauge, Tool}
import threads.platformThreads

// The exit statuses flare can terminate with, declared as objects (a `Status` must be an
// `object`, not a `val` — soundness#1811) so that the precise union of an `execute` block's
// result type documents them: `Status.Admissible` reifies the union, and the manpage's EXIT
// STATUS section is generated from it.
// `UsageError` and `Tool.NoCredential` are Pyrocosm's; a tool's own are numbered from 32, so that
// none collides with the standard ones.
object Violations extends Status(1, t"one or more error-severity violations were reported")
object NoConfig extends Status(3, t"no .pyrocosm/flare/config.tel was found")
object ConfigError extends Status(4, t"the configuration file could not be used")
object ParseFailure extends Status(5, t"a source file could not be parsed")
object NoRepository extends Status(6, t"the project is not in a git repository")
object NotesFailed extends Status(7, t"the git notes could not be written or read")
object ModelFailed extends Status(32, t"the model could not be consulted")
object AssessIncomplete extends Status(33, t"some candidates were not judged")

// Flare's user interface, in one namespace: its subcommands and flags. The object exists so each
// can carry its natural name without a package-level `val` shadowing a Soundness export of the
// same name for the whole `flare` package.
object ui:
  val Check = Subcommand("check", "check a profile's sources against its style and rules")

  val Metrics =
    Subcommand("metrics", "count a profile's rules and gates, and record the census in git notes")

  val Options =
    Subcommand
      ( "options", "print the -P:flare: options equivalent to a profile, for the compiler plugin" )

  val Rules = Subcommand("rules", "list the rules a profile checks")

  val Assess =
    Subcommand
      ( "assess",
        "have a model judge a profile's candidate definitions against an assessment rule, and " +
          "record the verdicts in git notes" )

  val Terse  = Flag[Unit]("terse", false, List('t'), "one plain line per finding, for logs and CI")
  val Force  = Flag[Unit]("force", false, List('f'), "overwrite an existing note")
  val DryRun = Flag[Unit]("dry-run", false, List('n'), "print the census without writing any note")

  val Show =
    Flag[Text]("show", false, List('s'), "print the census recorded for a commit or input tree")

  val Limit =
    Flag[Text]("limit", false, List('l'), "judge at most this many candidates, the likeliest first")

  val Report =
    Flag[Unit]
      ( "report", false, List('r'), "add per-criterion counts and band totals to the ranking" )

  // The port `flare serve` listens on; `--port`, the `flare.port` property, `FLARE_PORT` and
  // `port` in either config file all reach it — and `Tool` reads the same keyword for the
  // front-end a config's `serve` launches.

// Flare as a Pyrocosm tool: `about`, `install`, `quit` and `--version` come from `Tool`, as does
// the search for `.pyrocosm/flare/config.tel`, and the dashboard a config's `serve` launches.
// Capitalised, since a `val flare` would clash with the launcher's `@main def flare`.
val Flare: Tool =
  Tool
    ( t"flare",
      prose = t"Flare checks Scala 3 sources against Consequent Style and a project's own " +
        t"rules, defined in .pyrocosm/flare/config.tel, and records a census of those " +
        t"rules in git notes so that the counts can be followed commit by commit.",
      web   = flare.Dashboard.web )

// Plain output is the default for an agent or a workflow: no terminal reads the colours, and a
// line per finding is what a log wants.
private def terseByDefault(using Environment): Boolean = Invoker.detect.terse

private def width(using Environment): Int = Flare.columns

// One grep-able line per finding.
private def terse(root: Text, findings: List[Report.Finding])(using Stdio): Unit =
  findings.each: (finding: Report.Finding) =>
    val level = if finding.error then t"error" else t"warning"
    val where = t"${Report.relative(root, finding.path)}:${finding.line}:${finding.column}"
    Out.println(t"$where: $level: [${finding.rule}] ${finding.message}")

// The words on the command line that are not flags or their operands.
private def words(arguments: List[Argument]): List[Text] =
  def recur(rest: List[Argument], acc: List[Text]): List[Text] = rest match
    case head :: tail =>
      val text = head()

      if text == t"--show" || text == t"-s" || text == t"--limit" || text == t"-l"
      then recur(tail match { case _ :: rest => rest; case _ => tail }, acc)
      else if text.starts(t"-") then recur(tail, acc)
      else recur(tail, acc + List(text))

    case _ => acc

  recur(arguments, Nil)

// Which profile the words name, and which paths: the first word is a profile if the configuration
// defines one of that name; otherwise every word is a path. With one profile it needs no naming;
// with several, an unnamed profile is a usage error the command reports.
private def select(config: Workspace.Config, words: List[Text])
:   (Optional[Workspace.Profile], List[Text], Optional[Text]) =

  words match
    case head :: tail if config.profile(head).present => (config.profile(head), tail, Unset)
    case _ if config.profiles.size == 1               => (config.profiles.head, words, Unset)

    case _ =>
      val names = config.profiles.map(_.name).join(t", ")
      (Unset, words, t"the configuration defines several profiles ($names); name one")

// The client's command dispatch. The `@main` entry point and burdock's `externalize` wrapper live
// alone in the `launcher` module (`src/launcher/flare_launcher.scala`), which depends on this
// module (and `plugin`) as PUBLISHED artifacts — so `externalize` records their jar hashes and the
// repackager turns them into on-demand `Burdock-Require` downloads instead of inlining them.
//
// The standard subcommands (`about`, `install`, `quit`) and `--version` are handled first, by
// `Tool.standard`; only a present `--version` is consumed, so `flare --terse` and a bare `flare`
// still reach the catch-all check. The `Configurator` it provides goes unused: flare reads its
// configuration through `Workspace`, not through `Setting`s.
// An object rather than a top-level method, so that it can declare its use of the global
// `threads` the completions executive supervises under and the `Watcher` the daemon watches its
// state files with: a top-level definition can carry no `uses` clause.
object Client uses parasite.threads, surveillance.Watcher:
  def run(): Unit =
    // The completions executive supervises under the global `threads`, a capability it declares
    // it uses; `cli` takes a pure executive, so it is vouched pure here, as the invocation's
    // capabilities are in `ambient`.
    given executive: exoskeleton.executives.CompletionsExecutive =
      caps.unsafe.unsafeAssumePure(executives.completionsExecutive)

    cli:
      Flare.standard:
        dispatch()

// The dispatch is a method rather than the body of the `cli` block: that block runs on a
// connection's task and may retain only what is durable, a rule the inline body's closures (a
// `safely`, the served dashboard's task) fail under, where a method's are typed on their own.
private def dispatch()
  ( using Cli, Resident, Monitor, Environment, Interpreter, Configurator )
:   Execution =

  val directory: Text = summon[Cli].workingDirectory.directory()
  val workspace: Workspace.Outcome = Workspace.load(directory)

  // The dashboard offers the profiles of every project the daemon has seen.
  workspace match
    case Workspace.Outcome.Loaded(config) => flare.Dashboard.register(config)
    case _                                => ()

  // Whether the client is a terminal, which is what decides whether a run shows its progress.
  val tty: Boolean = summon[Resident].cliInput == ethereal.Terminus.Terminal

  arguments match
    case ui.Options() :: rest =>
      ambient(options(workspace, words(rest)))

    case ui.Rules() :: rest =>
      ambient(rules(workspace, words(rest)))

    case ui.Metrics() :: rest =>
      val force  = ui.Force().present
      val dryRun = ui.DryRun().present
      val show   = ui.Show().value
      ambient(metrics(workspace, words(rest), force, dryRun, show, tty))

    case ui.Assess() :: rest =>
      val force  = ui.Force().present
      val dryRun = ui.DryRun().present
      val show   = ui.Show().value
      val limit  = ui.Limit().value
      val report = ui.Report().present
      ambient(assess(workspace, directory, words(rest), force, dryRun, show, limit, report))

    case ui.Check() :: rest =>
      val terse = ui.Terse().present
      ambient(check(workspace, directory, words(rest), terse, tty))

    // `flare`, `flare <profile>`, `flare <path>…` and `flare --terse`: a check.
    case rest =>
      val terse = ui.Terse().present
      ambient(check(workspace, directory, words(rest), terse, tty))

// The configuration, or the status to exit with when there is none to use.
private def loaded(workspace: Workspace.Outcome)(using Stdio)
:   Workspace.Config | NoConfig.type | ConfigError.type =

  workspace match
    case Workspace.Outcome.Loaded(config) => config

    case Workspace.Outcome.Missing =>
      Out.println(t"flare: no .pyrocosm/flare/config.tel was found here or above")
      NoConfig

    case Workspace.Outcome.Invalid(file, errors) =>
      Out.println(t"flare: $file could not be used:")
      errors.each: (error: Text) => Out.println(t"  $error")
      ConfigError

private def check
  ( workspace: Workspace.Outcome, directory: Text, words: List[Text], terse: Boolean, tty: Boolean )
  ( using Stdio, Console, Environment, WorkingDirectory, Monitor )
:   Exit | Violations.type | Tool.UsageError.type | NoConfig.type | ConfigError.type |
  ParseFailure.type =

  loaded(workspace) match
    case NoConfig    => NoConfig
    case ConfigError => ConfigError

    case config: Workspace.Config => select(config, words) match
      case (_, _, message: Text) =>
        Out.println(t"flare: $message")
        Tool.UsageError

      case (profile: Workspace.Profile, paths, _) =>
        val plugin = config.pluginConfig(profile)
        val pluginConfig = plugin.config

        if !plugin.errors.nil then
          Out.println(t"flare: profile `${profile.name}` could not be used:")
          plugin.errors.each: (error: Text) => Out.println(t"  $error")
          ConfigError
        else
          val restrict: List[Text] = paths.map: (path: Text) => Sources.resolve(directory, path)
          val files = safely(Sources.expand(config.root, profile, restrict)).or(Nil)
          val plain = terse || terseByDefault

          // The board: findings scroll into its content panel as each file is checked, under
          // a gauge of files parsed and then checked; the report follows once it has closed.
          val board = Board(t"flare: ${profile.name}")
          val sink: Checking.Sink = Checking.Sink(board, config.root)

          def work(): Checking.Outcome = Checking.run(profile, pluginConfig, files, sink)

          // A terminal that cannot be initialised costs the board, not the check.
          val outcome: Checking.Outcome =
            if !(tty && !plain) then work() else
              recover:
                case Terminal.Error() => work()

              . protect(board.show()(work()))

          if board.aborted then Out.println(t"flare: the check was abandoned")

          if plain then flare.terse(config.root, outcome.all)
          else Report.rich(config.root, outcome.all, outcome.texts, files.size, width)

          if !outcome.parseErrors.nil then ParseFailure
          else if outcome.findings.exists(_.error) then Violations
          else Exit.Ok

      case _ =>
        Tool.UsageError

private def options(workspace: Workspace.Outcome, words: List[Text])(using Stdio)
:   Exit | Tool.UsageError.type | NoConfig.type | ConfigError.type =

  loaded(workspace) match
    case NoConfig    => NoConfig
    case ConfigError => ConfigError

    case config: Workspace.Config => select(config, words) match
      case (profile: Workspace.Profile, _, _) =>
        config.pluginOptions(profile).each: (option: Text) => Out.println(t"-P:flare:$option")
        Exit.Ok

      case (_, _, message: Text) =>
        Out.println(t"flare: $message")
        Tool.UsageError

      case _ => Tool.UsageError

private def rules(workspace: Workspace.Outcome, words: List[Text])(using Stdio)
:   Exit | Tool.UsageError.type | NoConfig.type | ConfigError.type =

  loaded(workspace) match
    case NoConfig    => NoConfig
    case ConfigError => ConfigError

    case config: Workspace.Config => select(config, words) match
      case (profile: Workspace.Profile, _, _) =>
        val pluginConfig = config.pluginConfig(profile).config

        List.from(Rules.style).each: (rule: flare.Rule) =>
          val state =
            if pluginConfig.omits(rule.id) then t"omitted"
            else if pluginConfig.strictly(rule.id) || pluginConfig.errors then t"error"
            else t"warn"

          Out.println(t"${rule.id.tt}\t${rule.principle.toString.tt}\t${rule.phase.tt}\t$state")

        config.rules.each: (rule: Workspace.Rule) =>
          val enforcement = profile.enforced.filter(_.rule == rule.name).head

          val state = enforcement.lay(t"measured"): (e: Workspace.Enforcement) =>
            e.severity.or(rule.severity).or(t"warn")

          val predicates =
            rule.predicates.map { (p: Workspace.Predicate) => t"${p.kind} ${p.target}" }.join(t"; ")

          Out.println(t"${rule.name}\tProject\tparser\t$state\t$predicates")

        Exit.Ok

      case (_, _, message: Text) =>
        Out.println(t"flare: $message")
        Tool.UsageError

      case _ => Tool.UsageError

private def metrics
  ( workspace: Workspace.Outcome, words: List[Text], force: Boolean, dryRun: Boolean,
    show: Optional[Text], tty: Boolean )
  ( using Stdio, Console, Environment, WorkingDirectory, Monitor )
:   Exit | Tool.UsageError.type | NoConfig.type | ConfigError.type | ParseFailure.type |
  NoRepository.type | NotesFailed.type =

  loaded(workspace) match
    case NoConfig    => NoConfig
    case ConfigError => ConfigError

    case config: Workspace.Config => select(config, words) match
      case (_, _, message: Text) =>
        Out.println(t"flare: $message")
        Tool.UsageError

      case (profile: Workspace.Profile, _, _) =>
        val namespace = profile.notes.or(t"flare")

        Repository.locate(config.root) match
          case repository: Repository => show match
            case target: Text => showCensus(repository, namespace, profile.name, target)
            case _            => measure(config, profile, repository, namespace, force, dryRun, tty)

          case _ =>
            val root = config.root
            Out.println(t"flare: $root is not inside a git repository, so nothing can be recorded")
            NoRepository

      case _ => Tool.UsageError

private def showCensus(repository: Repository, namespace: Text, profile: Text, target: Text)
  ( using Stdio, WorkingDirectory )
:   Exit | NotesFailed.type =

  repository.resolve(target) match
    case hash: Text =>
      // A commit's pointer note names the tree measured for this profile; a tree is its own key.
      val tree: Optional[Text] =
        if repository.objectType(hash) == t"commit"
        then Census.treeFor(repository, namespace, profile, hash)
        else hash

      tree.let(repository.storedNote(namespace, _)) match
        case body: Text =>
          Out.print(body)
          Exit.Ok

        case _ =>
          Out.println
            ( t"flare: no census is recorded for `$target` (profile `$profile`) under " +
              t"refs/notes/$namespace" )

          NotesFailed

    case _ =>
      Out.println(t"flare: `$target` is not a commit or tree in this repository")
      NotesFailed

private def measure
  ( config: Workspace.Config, profile: Workspace.Profile, repository: Repository, namespace: Text,
    force: Boolean, dryRun: Boolean, tty: Boolean )
  ( using Stdio, Console, Environment, WorkingDirectory, Monitor )
:   Exit | ConfigError.type | ParseFailure.type | NotesFailed.type =

  val plugin = config.pluginConfig(profile, census = true)
  val pluginConfig = plugin.config

  if !plugin.errors.nil then
    Out.println(t"flare: profile `${profile.name}` could not be used:")
    plugin.errors.each: (error: Text) => Out.println(t"  $error")
    ConfigError
  else
    val files = safely(Sources.expand(config.root, profile, Nil)).or(Nil)
    val features = profile.languages.map(_.s).stdlib

    val board = Board(t"flare metrics: ${profile.name}")

    def work(): (flare.Frontend.Result, List[(Text, List[(Text, Int)])]) =
      val parsed: Atomic[Int] = Atomic(0)
      val counted: Atomic[Int] = Atomic(0)

      val result =
        flare.Frontend.run(files, profile.languages, t"parser"): (phase: String) =>
          if phase == "parser" then
            val progress = Gauge.Reckoning(parsed.since(_ + 1).toLong, files.size.toLong)
            board.gauge(progress, t"Parsing")

      val perFile: List[(Text, List[(Text, Int)])] =
        result.units.map: (unit: flare.Frontend.Parsed) =>
          val report =
            Linter.check(pluginConfig, unit.path.s, unit.text.s, unit.tree, unit.source, features)

          val progress = Gauge.Reckoning(counted.since(_ + 1).toLong, result.units.size.toLong)
          board.gauge(progress, t"Counting")

          val relative =
            if unit.path.starts(t"${config.root}/") then unit.path.skip(config.root.length + 1)
            else unit.path

          val counts: List[(Text, Int)] =
            List.from:
              report.census.map: (indicator: String, count: Int) => (Text(indicator), count)

          if !counts.nil then
            val listed = counts.map { (i: Text, n: Int) => t"$i $n" }.join(t", ")
            board.append(Block.Paragraph(Inline.text(t"$relative: $listed")))

          (relative, counts)

      (result, perFile)

    val (result, perFile) =
      if !(tty && !terseByDefault) then work() else
        recover:
          case Terminal.Error() => work()

        . protect(board.show()(work()))

    val totals = Census.totals(perFile)

    if result.diagnostics.exists(_.error) then
      result.diagnostics.filter(_.error).each: (d: flare.Frontend.Diagnostic) =>
        Out.println(t"${d.path}:${d.line}:${d.column}: error: ${d.message}")

      Out.println(t"flare: a source did not parse, so nothing was recorded")
      ParseFailure
    else
      repository.inputTree(files) match
        case tree: Text =>
          Report.census(totals, files.size, tree, width)

          if dryRun then Exit.Ok else
            val head = repository.head()

            val measurement =
              Census.Measurement
                ( profile.name, tree, head.or(t""), repository.dirty(),
                  java.time.Instant.now().nn.toString.tt, files.size, totals,
                  perFile.filter(!_(1).nil) )

            val existing = repository.note(namespace, tree)

            // The tree note is written once per distinct input tree: the same inputs give the
            // same census, so a second run only adds a pointer from a new commit.
            val treeOutcome: Optional[Boolean] =
              if existing.present && !force then Unset
              else repository.addNote(namespace, tree, Census.body(measurement), force)

            val pointerOutcome: Optional[Boolean] = head match
              case commit: Text =>
                val already =
                  repository.note(namespace, commit).lay(false): (pointer: Text) =>
                    pointer.contains(t"tree $tree") && pointer.contains(t"profile ${profile.name}")

                if already then Unset
                else repository.appendNote(namespace, commit, Census.pointer(measurement))

              case _ =>
                Out.println
                  ( t"flare: the repository has no HEAD commit, so no pointer note was written" )

                Unset

            if treeOutcome == false || pointerOutcome == false then
              Out.println(t"flare: the git notes could not be written")
              NotesFailed
            else
              flare.Dashboard.measured()
              val where = t"refs/notes/$namespace"
              val commit = head.lay(t""): (c: Text) => t", commit $c"

              (treeOutcome, pointerOutcome) match
                case (Unset, Unset) =>
                  Out.println
                    ( t"flare: already recorded under $where (tree $tree$commit); use --force " +
                      t"to replace the census" )

                case (Unset, _) =>
                  Out.println
                    ( t"flare: census for tree $tree already recorded under $where; added the " +
                      t"pointer from commit ${head.or(t"")}" )

                case _ =>
                  Out.println(t"flare: recorded under $where (tree $tree$commit)")

              Exit.Ok

        case _ =>
          Out.println(t"flare: the input tree could not be written; is `git` on the PATH?")
          NotesFailed

// `flare assess [<rule>…] [<profile>]`: the rules named, or every rule the profile lists under
// `assess`; each run in turn, its ranking printed worst first and recorded in git notes.
private def assess
  ( workspace: Workspace.Outcome, directory: Text, words: List[Text], force: Boolean,
    dryRun: Boolean, show: Optional[Text], limit: Optional[Text], report: Boolean )
  ( using Stdio, Console, Environment, WorkingDirectory )
:   Exit | Tool.UsageError.type | NoConfig.type | ConfigError.type | ParseFailure.type |
  NoRepository.type | NotesFailed.type | Tool.NoCredential.type | ModelFailed.type |
  AssessIncomplete.type =

  loaded(workspace) match
    case NoConfig    => NoConfig
    case ConfigError => ConfigError

    case config: Workspace.Config =>
      val named: List[Text] = words.filter: (w: Text) => config.assessment(w).present
      val others: List[Text] = words.filter: (w: Text) => config.assessment(w).absent

      select(config, others) match
        case (_, _, message: Text) =>
          Out.println(t"flare: $message")
          Tool.UsageError

        case (profile: Workspace.Profile, _, _) =>
          val chosen: List[Text] = if named.nil then profile.assessed else named

          val rules: List[Workspace.Assessment] =
            chosen
            . bind[List[Workspace.Assessment], Workspace.Assessment, List[Workspace.Assessment]]:
                (name: Text) =>
                  config.assessment(name).lay(Nil: List[Workspace.Assessment])(List(_))

          if rules.nil then
            Out.println
              ( t"flare: profile `${profile.name}` assesses nothing; name a rule, or add " +
                t"`assess <rule>` to the profile" )

            Tool.UsageError
          else
            Repository.locate(config.root) match
              case repository: Repository =>
                val statuses: List[Assessed] =
                  rules.map: (rule: Workspace.Assessment) =>
                    show match
                      case target: Text => showAssessment(repository, rule, target)

                      case _ =>
                        assessOne
                          ( config, profile, rule, repository, directory, force, dryRun, limit,
                            report )

                statuses.filter(_ != Exit.Ok).head.or(Exit.Ok)

              case _ =>
                Out.println
                  ( t"flare: ${config.root} is not inside a git repository, so nothing can be " +
                    t"recorded" )

                NoRepository

        case _ => Tool.UsageError

private def showAssessment(repository: Repository, rule: Workspace.Assessment, target: Text)
  ( using Stdio, WorkingDirectory )
:   Exit | NotesFailed.type =

  repository.resolve(target) match
    case hash: Text =>
      val tree: Optional[Text] =
        if repository.objectType(hash) == t"commit"
        then Recording.treeFor(repository, rule.name, hash)
        else hash

      tree.let(Recording.assessment(repository, rule.name, _)) match
        case assessment: Verdicts.Assessment =>
          Out.println(Assessing.heading(assessment))
          assessment.ranked.each: (r: Verdicts.Ranked) => Out.println(Assessing.line(r))
          Exit.Ok

        case _ =>
          val namespace = Recording.namespace(rule.name)

          Out.println
            ( t"flare: no assessment for rule `${rule.name}` is recorded for `$target` under " +
              t"refs/notes/$namespace" )

          NotesFailed

    case _ =>
      Out.println(t"flare: `$target` is not a commit or tree in this repository")
      NotesFailed

// What assessing one rule can end with.
private type Assessed =
  Exit | ConfigError.type | ParseFailure.type | NotesFailed.type | Tool.NoCredential.type |
    ModelFailed.type | AssessIncomplete.type

private def assessOne
  ( config: Workspace.Config, profile: Workspace.Profile, rule: Workspace.Assessment,
    repository: Repository, directory: Text, force: Boolean, dryRun: Boolean,
    limit: Optional[Text], report: Boolean )
  ( using Stdio, Environment, WorkingDirectory )
:   Assessed =

  config.model(rule.model) match
    case model: Workspace.Model =>
      Rubric.load(config.root, rule.rubric) match
        case rubric: Rubric =>
          // A dry run consults no model, so it needs no credential.
          val key: Optional[Text] =
            if dryRun then t"" else Flare.credential(model.credential, directory)

          key match
            case key: Text =>
              val files = safely(Sources.expand(config.root, profile, Nil)).or(Nil)
              val criteria: List[Text] = rule.criteria.map(_.id)

              val oracle: (Text -> Text) => Oracle.Oracle =
                (digests: Text -> Text) =>
                  Oracle.Anthropic(model, key, rubric, criteria, digests, 30)

              val outcome =
                Assessing.run
                  ( config, profile, rule, model, rubric, repository, files, oracle,
                    limit.let { (n: Text) => safely(n.as[Int]) }, force, dryRun,
                    (message: Text) => Out.println(t"flare: $message") )

              if !outcome.parseErrors.nil then
                outcome.parseErrors.each: (d: flare.Frontend.Diagnostic) =>
                  Out.println(t"${d.path}:${d.line}:${d.column}: error: ${d.message}")

                Out.println(t"flare: a source did not parse, so nothing was recorded")
                ParseFailure
              else
                if dryRun && outcome.scored.nil then
                  outcome.candidates.each: (candidate, digest) =>
                    Out.println(Assessing.line(candidate, digest))

                outcome.scored.each: (s: Assessing.Scored) => Out.println(Assessing.line(s))

                if report then
                  Assessing.bandLines(outcome.assessment).each(Out.println(_))
                  Assessing.criterionLines(rule, outcome.scored).each(Out.println(_))

                Out.println(Assessing.summary(rule, outcome, rubric))

                outcome.recorded match
                  case false =>
                    Out.println(t"flare: the git notes could not be written")
                    NotesFailed

                  case _ =>
                    if !dryRun && outcome.unjudged > 0 then AssessIncomplete else Exit.Ok

            case _ =>
              Out.println
                ( t"flare: no credential `${model.credential}` could be found; declare it in " +
                  t"~/.config/pyrocosm/credentials.tel or set the conventional environment " +
                  t"variable" )

              Tool.NoCredential

        case _ =>
          Out.println
            ( t"flare: the rubric `${rule.rubric}` for rule `${rule.name}` could not be read" )

          ConfigError

    case _ =>
      Out.println(t"flare: rule `${rule.name}` names model `${rule.model}`, which is not defined")
      ConfigError
