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

import soundness.*
import dysasymptotics.linearSize
import pyrocosm.Tool

// The per-project configuration: a `.pyrocosm/flare/config.tel` file in the invocation's working
// directory or the nearest ancestor holding one — resolved upwards exactly like `.git` (and exactly
// as flame resolves its own `.pyrocosm/flame/config.tel`), so `flare` can be launched from anywhere
// inside a project. `.pyrocosm` is the project's shared directory, one subdirectory per tool.
//
// The file defines the project's own rules and the profiles that check them:
//
//   tel 1.0
//
//   rule cast                       # a named detection, from predicates over the parse tree
//     invokes asInstanceOf
//     message  Casts bypass the type checker; prefer a pattern match
//     severity error
//
//   model haiku                     # a model target for `flare assess`
//     model      claude-haiku-4-5
//     credential anthropic          # a name in Pyrocosm's credential cascade
//     batch      20                 # candidates per request
//
//   rule plumbing                   # an assessment rule: a model judges each candidate
//     assess    def                 # over method definitions
//     model     haiku
//     rubric    doc/standards/plumbing.md
//     criterion S1 +2 single expression
//     vocabulary .tt .s .show
//     band      definite 10
//     gate      ladder 2
//     signal    local +2
//
//   profile default                 # what `flare` checks; `flare <name>` when there are several
//     source src/**/*.scala         # globs relative to the project root (the `.pyrocosm` parent)
//     exclude src/gen/**
//     language experimental.modularity
//     notes flare                   # the git notes namespace `flare metrics` writes to
//     style consequent              # the formatting standard, with its refinements beneath
//       header etc/header.txt
//       columns 100
//       umbrella soundness
//       module-root lib
//       interpolator m
//       omit L4
//       strict F4
//       severity warn
//       gate Unsafe
//         prefix unsafe
//     enforce cast                  # a rule this profile checks, optionally at its own severity
//       severity warn
//     assess plumbing               # an assessment rule `flare assess` runs for this profile
//
// The plugin has no TEL parser, so a profile is rendered as `-P:flare:` options (`flare options`)
// and read back through the plugin's own `Config.parse` — one parser, one meaning, for both the
// command and the plugin.
object Workspace:
  case class Predicate(kind: Text, target: Text)

  case class Rule
    ( name: Text, predicates: List[Predicate], message: Optional[Text], severity: Optional[Text] )

  case class Gate(token: Text, prefix: Optional[Text])

  case class Style
    ( name:          Text,
      header:        Optional[Text],
      columns:       Optional[Int],
      umbrella:      Optional[Text],
      moduleRoot:    Optional[Text],
      interpolators: List[Text],
      omitted:       List[Text],
      strict:        List[Text],
      severity:      Optional[Text],
      gates:         List[Gate] )

  case class Enforcement(rule: Text, severity: Optional[Text])

  // One yes/no criterion the model answers per candidate, and its weight in the score.
  case class Criterion(id: Text, weight: Int, text: Text)

  // A named threshold on the total: `definite 10`, `likely 5`, `weak 0`; below the lowest,
  // `legitimate`.
  case class Band(name: Text, threshold: Int)

  // A shape that admits a definition as a candidate, with its bound where one applies:
  // `conversion`, `ladder 2`, `round-trip`, `adapter 3`, `verb 3`.
  case class Admission(kind: Text, bound: Optional[Int])

  // Points the driver adds for what it can see itself: `local +2`, `calls 1 +2`, `calls 4+ -1`,
  // `duplicate +3`, `one-liner +1`.
  case class Signal(kind: Text, value: Optional[Text], points: Int)

  // A model target: which provider and model, the credential's name in the Pyrocosm cascade,
  // and how requests are shaped.
  case class Model
    ( name:       Text,
      provider:   Text,
      model:      Text,
      credential: Text,
      batch:      Int,
      limit:      Int,
      mode:       Text )

  // An assessment rule: what a model judges, against which rubric and criteria, over which
  // definitions. Unlike a `Rule`, it never reaches the plugin.
  case class Assessment
    ( name:          Text,
      unit:          Text,
      model:         Text,
      rubric:        Text,
      criteria:      List[Criterion],
      vocabulary:    List[Text],
      bands:         List[Band],
      lines:         Int,
      kinds:         List[Text],
      excludeNames:  List[Text],
      excludeBodies: List[Text],
      noUsing:       Boolean,
      admissions:    List[Admission],
      signals:       List[Signal],
      predicates:    List[Predicate] ):

    def weight(id: Text): Int = criteria.filter(_.id == id).head.let(_.weight).or(0)

    // The band a total falls in.
    def band(total: Int): Text =
      bands.filter(total >= _.threshold).head.let(_.name).or(t"legitimate")

  case class Profile
    ( name:      Text,
      sources:   List[Text],
      excludes:  List[Text],
      languages: List[Text],
      notes:     Optional[Text],
      style:     Optional[Style],
      enforced:  List[Enforcement],
      assessed:  List[Text] )

  case class Config
    ( file:        Text,
      root:        Text,
      rules:       List[Rule],
      profiles:    List[Profile],
      models:      List[Model]      = Nil,
      assessments: List[Assessment] = Nil ):

    def profile(name: Text): Optional[Profile] = profiles.filter(_.name == name).head
    def assessment(name: Text): Optional[Assessment] = assessments.filter(_.name == name).head
    def model(name: Text): Optional[Model] = models.filter(_.name == name).head

    // A relative path in the file resolves against the project root.
    def absolute(entry: Text): Text = if entry.starts(t"/") then entry else t"$root/$entry"

    // The `-P:flare:` options equivalent to a profile, without the `-P:flare:` prefix, in the
    // plugin's own syntax: `;` separates a list's items (the compiler splits `-P` on commas
    // before the plugin sees them) and `:` separates a value's fields.
    def pluginOptions(profile: Profile): List[Text] =
      val style: Optional[Style] = profile.style

      def option(keyword: Text, value: Optional[Text]): List[Text] =
        value.lay(Nil: List[Text]): (text: Text) => List(t"$keyword=$text")

      def listed(keyword: Text, values: List[Text]): List[Text] =
        if values.nil then Nil else List(t"$keyword=${values.join(t";")}")

      val errors: List[Text] =
        if style.let(_.severity) == t"error" then List(t"errors") else Nil

      val header: List[Text] = option(t"header", style.let(_.header).let(absolute(_)))
      val columns: List[Text] = option(t"columns", style.let(_.columns).let(_.show))
      val umbrella: List[Text] = option(t"umbrella", style.let(_.umbrella))
      val moduleRoot: List[Text] = option(t"moduleRoot", style.let(_.moduleRoot))
      val language: List[Text] = listed(t"language", profile.languages)
      val interpolators: List[Text] = listed(t"interpolators", style.let(_.interpolators).or(Nil))
      val strict: List[Text] = listed(t"strict", style.let(_.strict).or(Nil))
      val omitted: List[Text] = listed(t"omit", style.let(_.omitted).or(Nil))

      val gates: List[Text] =
        style.let(_.gates).or(Nil).map: (gate: Gate) =>
          val token: Text = gate.token
          gate.prefix.lay(t"gate=$token"): (prefix: Text) => t"gate=$token:$prefix"

      val definitions: List[Text] =
        rules.bind[List[Text], Text, List[Text]]: (rule: Rule) =>
          val name: Text = rule.name

          val predicates: List[Text] =
            rule.predicates.map: (p: Predicate) => t"rule=$name:${p.kind}:${p.target}"

          val message: List[Text] =
            option(t"rule", rule.message.let { (m: Text) => t"$name:message:$m" })

          val severity: List[Text] =
            option(t"rule", rule.severity.let { (level: Text) => t"$name:severity:$level" })

          predicates + message + severity

      val enforcements: List[Text] =
        profile.enforced.map: (e: Enforcement) =>
          val rule: Text = e.rule
          e.severity.lay(t"enforce=$rule"): (level: Text) => t"enforce=$rule:$level"

      errors + header + columns + umbrella + moduleRoot + language + interpolators + strict +
        omitted + gates + definitions + enforcements

    // The plugin's configuration for a profile, and the errors its options raise — read back
    // through the plugin's own parser, so the command and the plugin can never disagree.
    def pluginConfig(profile: Profile, census: Boolean = false): Plugin =
      val extra: List[Text] = if census then List(t"census") else Nil
      val options: List[Text] = pluginOptions(profile) + extra
      val parsed = flare.Config.parse(options.map(_.s).stdlib)
      val config: flare.Config = parsed(0)
      val messages: scala.List[Text] = parsed(1).map: (error: String) => Text(error)
      Plugin(config, List.from(messages))

  // A profile as the plugin sees it: its configuration, and what the options failed to say.
  case class Plugin(config: flare.Config, errors: List[Text])

  // What loading the configuration found: no file at all, a file that could not be read or
  // did not say what it must, or a configuration.
  enum Outcome:
    case Missing
    case Invalid(file: Text, errors: List[Text])
    case Loaded(config: Config)

  // Two separately-scoped `safely` regions (the filesystem read, then the TEL parse), as flame's
  // reader does: one region's tactic would be captured by both the path reader and the TEL
  // aggregator, which separation checking rejects. Flare's own parse, rather than `Tool`'s: a
  // file that fails to parse is reported (`Outcome.Invalid`), where `Tool` would treat it as
  // absent.
  private def parse(file: Path on Linux): Optional[Tel] =
    safely(file.read[Data]).let: data => safely(data.read[Tel])

  // The nearest `.pyrocosm/flare/config.tel` at or above `directory` is what `Tool` finds, exactly
  // as it does for every Pyrocosm tool.
  def load(directory: Text): Outcome =
    val project: Tool.Project = Flare.project(directory)

    project.file.lay(Outcome.Missing): file =>
      val unreadable = Outcome.Invalid(file.encode, List(t"the file could not be read as TEL"))
      parse(file).lay(unreadable): tel => read(file.encode, project.root.encode, tel)

  // The atoms of every child compound with this keyword, one per occurrence.
  private def atoms(node: Tel, keyword: Text): List[Text] = Tool.atoms(node, keyword)

  // A compound's atoms joined by single spaces: a `message` or a `matches` pattern may hold
  // several words.
  private def phrase(node: Tel): Text = node.atomTexts.to[List].filter(_ != t"").join(t" ")

  private def int(node: Tel, keyword: Text): Optional[Int] =
    atoms(node, keyword).head.let: (text: Text) => safely(text.as[Int])

  private def read(file: Text, root: Text, tel: Tel): Outcome =
    var errors: List[Text] = Nil
    def fail(message: Text): Unit = errors = errors + List(message)

    // A signed integer atom: `+2`, `-3`, `10`.
    def signed(text: Text): Optional[Int] =
      safely((if text.starts(t"+") then text.skip(1) else text).as[Int])

    val models: List[Model] =
      tel.fields(t"model").to[List].map: (node: Tel) =>
        val name = node.primaryAtom
        if name == t"" then fail(t"a model has no name")

        val provider = atoms(node, t"provider").head.or(t"anthropic")

        if provider != t"anthropic"
        then fail(t"model `$name`: `$provider` is not a provider; only `anthropic` is")

        val model = atoms(node, t"model").head.or(t"")
        if model == t"" then fail(t"model `$name` names no model")

        val mode = atoms(node, t"mode").head.or(t"batches")

        if mode != t"batches" && mode != t"realtime"
        then fail(t"model `$name`: `$mode` is not a mode; expected `batches` or `realtime`")

        Model
          ( name, provider, model, atoms(node, t"credential").head.or(provider),
            int(node, t"batch").or(20), int(node, t"limit").or(4096), mode )

    models.map(_.name).each: (name: Text) =>
      if models.filter(_.name == name).size > 1
      then fail(t"model `$name` is defined more than once")

    val assessments: List[Assessment] =
      tel.fields(t"rule").to[List].filter(_.field(t"assess").present).map: (node: Tel) =>
        val name = node.primaryAtom
        val kinds: List[Text] = List.from(Predicates.kinds.map(_.tt))

        val known: List[Text] =
          List(t"assess", t"model", t"rubric", t"criterion", t"vocabulary", t"band", t"lines",
               t"kind", t"exclude-name", t"exclude-body", t"no-using", t"gate", t"signal")

        node.childCompounds.to[List].map(_.keyword).each: (keyword: Text) =>
          if !known.has(keyword) && !kinds.has(keyword) then
            fail(t"rule `$name`: `$keyword` is not an assessment keyword")

        val unit = atoms(node, t"assess").head.or(t"def")

        if unit != t"def"
        then fail(t"rule `$name`: `assess $unit` is not supported; only `assess def` is")

        val model = atoms(node, t"model").head.or(t"")

        if model == t"" then fail(t"rule `$name` names no model")
        else if models.filter(_.name == model).nil
        then fail(t"rule `$name` uses model `$model`, which is not defined")

        val rubric = atoms(node, t"rubric").head.or(t"")
        if rubric == t"" then fail(t"rule `$name` names no rubric")

        val criteria: List[Criterion] =
          node.fields(t"criterion").to[List].bind: (c: Tel) =>
            c.atomTexts.to[List].filter(_ != t"") match
              case id :: weight :: rest =>
                val score = signed(weight)
                score.lay(Nil: List[Criterion]): (w: Int) => List(Criterion(id, w, rest.join(t" ")))
                . also { if score.absent then fail(t"rule `$name`: criterion `$id` has no weight") }

              case _ =>
                fail(t"rule `$name`: a criterion needs an id and a weight")
                Nil

        criteria.map(_.id).each: (id: Text) =>
          if criteria.filter(_.id == id).size > 1
          then fail(t"rule `$name`: criterion `$id` is defined more than once")

        if criteria.nil then fail(t"rule `$name` has no criteria")

        val bands: List[Band] =
          node.fields(t"band").to[List].bind: (b: Tel) =>
            b.atomTexts.to[List].filter(_ != t"") match
              case band :: threshold :: _ =>
                signed(threshold).lay(Nil: List[Band]): (n: Int) => List(Band(band, n))

              case _ => Nil

        bands.stdlib.sliding(2).foreach:
          case scala.Seq(higher, lower) =>
            if higher.threshold <= lower.threshold then fail(t"rule `$name`: bands must descend")

          case _ => ()

        val admissions: List[Admission] =
          node.fields(t"gate").to[List].map: (g: Tel) =>
            g.atomTexts.to[List].filter(_ != t"") match
              case kind :: bound :: _ => Admission(kind, safely(bound.as[Int]))
              case kind :: _          => Admission(kind, Unset)
              case _                  => Admission(t"", Unset)

        val signals: List[Signal] =
          node.fields(t"signal").to[List].bind: (s: Tel) =>
            s.atomTexts.to[List].filter(_ != t"") match
              case kind :: points :: Nil =>
                signed(points).lay(Nil: List[Signal]): (p: Int) => List(Signal(kind, Unset, p))

              case kind :: value :: points :: Nil =>
                signed(points).lay(Nil: List[Signal]): (p: Int) => List(Signal(kind, value, p))

              case _ =>
                fail(t"rule `$name`: a signal needs a kind and its points")
                Nil

        val predicates: List[Predicate] =
          kinds.bind[List[Predicate], Predicate, List[Predicate]]: kind =>
            node.fields(kind).to[List].map: (child: Tel) => Predicate(kind, phrase(child))

        def listed(keyword: Text): List[Text] =
          node.fields(keyword).to[List].bind: (v: Tel) => v.atomTexts.to[List].filter(_ != t"")

        Assessment
          ( name, unit, model, rubric, criteria, listed(t"vocabulary"), bands,
            int(node, t"lines").or(12), listed(t"kind"), listed(t"exclude-name"),
            listed(t"exclude-body"), node.field(t"no-using").present, admissions, signals,
            predicates )

    val rules: List[Rule] =
      tel.fields(t"rule").to[List].filter(_.field(t"assess").absent).map: (node: Tel) =>
        val name = node.primaryAtom

        val kinds: List[Text] = List.from(Predicates.kinds.map(_.tt))

        node.childCompounds.to[List].map(_.keyword).each: (keyword: Text) =>
          if keyword != t"message" && keyword != t"severity" && !kinds.has(keyword) then
            val expected = kinds.join(t", ")
            fail(t"rule `$name`: `$keyword` is not a predicate; expected one of $expected")

        val predicates: List[Predicate] =
          kinds.bind[List[Predicate], Predicate, List[Predicate]]: kind =>
            node.fields(kind).to[List].map: (child: Tel) =>
              val predicate = Predicate(kind, phrase(child))

              val problems = Predicates.validate(flare.Predicate(kind.s, predicate.target.s))
              problems.foreach: (error: String) => fail(t"rule `$name`: ${error.tt}")

              predicate

        if predicates.nil then fail(t"rule `$name` has no predicates")

        Rule
          ( name, predicates,
            node.field(t"message").let(phrase(_)),
            atoms(node, t"severity").head )

    (rules.map(_.name) + assessments.map(_.name)).each: (name: Text) =>
      if (rules.filter(_.name == name).size + assessments.filter(_.name == name).size) > 1 then
        fail(t"rule `$name` is defined more than once")

    val profiles: List[Profile] =
      tel.fields(t"profile").to[List].map: (node: Tel) =>
        val name = node.primaryAtom
        if name == t"" then fail(t"a profile has no name")
        if reserved.has(name) then fail(t"`$name` is a subcommand, so it cannot name a profile")

        val sources = atoms(node, t"source")
        if sources.nil then fail(t"profile `$name` names no sources")

        val style: Optional[Style] =
          node.field(t"style").let: (styleNode: Tel) =>
            val styleName = styleNode.primaryAtom

            if styleName != t"consequent"
            then fail(t"profile `$name`: `$styleName` is not a style; only `consequent` is")

            Style
              ( styleName,
                header        = atoms(styleNode, t"header").head,
                columns       = int(styleNode, t"columns"),
                umbrella      = atoms(styleNode, t"umbrella").head,
                moduleRoot    = atoms(styleNode, t"module-root").head,
                interpolators = atoms(styleNode, t"interpolator"),
                omitted       = atoms(styleNode, t"omit"),
                strict        = atoms(styleNode, t"strict"),
                severity      = atoms(styleNode, t"severity").head,
                gates         =
                  styleNode.fields(t"gate").to[List].map: (gate: Tel) =>
                    Gate(gate.primaryAtom, gate.field(t"prefix").let(_.primaryAtom)) )

        val enforced: List[Enforcement] =
          node.fields(t"enforce").to[List].map: (e: Tel) =>
            val rule = e.primaryAtom

            if rules.filter(_.name == rule).nil
            then fail(t"profile `$name` enforces `$rule`, which is not defined")

            Enforcement(rule, atoms(e, t"severity").head)

        val assessed: List[Text] = atoms(node, t"assess")

        assessed.each: (rule: Text) =>
          if assessments.filter(_.name == rule).nil
          then fail(t"profile `$name` assesses `$rule`, which is not an assessment rule")

        Profile
          ( name, sources, atoms(node, t"exclude"), atoms(node, t"language"),
            atoms(node, t"notes").head, style, enforced, assessed )

    profiles.map(_.name).each: (name: Text) =>
      if profiles.filter(_.name == name).size > 1
      then fail(t"profile `$name` is defined more than once")

    if profiles.nil then fail(t"no profile is defined")

    if errors.nil then Outcome.Loaded(Config(file, root, rules, profiles, models, assessments))
    else Outcome.Invalid(file, errors)

  // Subcommand names, which a profile may not take: `flare <word>` must be unambiguous.
  val reserved: Set[Text] =
    Set
      ( t"check", t"metrics", t"options", t"rules", t"assess", t"serve", t"about", t"install",
        t"quit" )
