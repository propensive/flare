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

// A gate: the type whose presence as a `using` parameter marks a definition as
// bypassing a guarantee the compiler would otherwise enforce (Soundness's
// `vacuous.Unsafe`, say), paired with the prefix such a definition's name must
// carry. S1 checks each gate a project declares; a project with none has
// nothing for it to check.
final case class Gate(token: String, prefix: String):
  // The token's simple name: `Unsafe` and `vacuous.Unsafe` are the same gate.
  def simpleToken: String = token.split("\\.").nn.last.nn

// One predicate of a project rule: a shape on the parse tree (`invokes`,
// `uses`, `matches`, …) and the name, construct or pattern it looks for. The
// vocabulary is `Predicates.kinds`.
final case class Predicate(kind: String, target: String)

// A project rule: a named detection assembled from predicates. Its `id` is its
// name, so it sits alongside the style's rules in every report. `severity` is
// the severity it carries when enforced and not overridden. `excluded` holds
// the names, as globs or `/regex/`es, that the rule deliberately leaves alone:
// a match on such a name is neither reported nor counted.
final case class Definition
  ( name:       String,
    predicates: List[Predicate] = Nil,
    message:    Option[String]  = None,
    severity:   String          = "warn",
    excluded:   List[String]    = Nil ):

  private lazy val exclusions: List[String => Boolean] = excluded.map(Predicates.names)

  // Is a matched name one this rule leaves alone?
  def excludes(name: String): Boolean = exclusions.exists(_(name))

object Config:
  // Does a `strict`/`omit` entry select this rule? An entry names either a rule
  // — `S1`, which also covers its sub-rules `S1.1` and `S1.2` — or a principle
  // letter, `S`, which covers every rule derived from it. Neither form matches
  // a longer identifier that merely begins with it, so `S1` does not select
  // `S12`. A project rule is selected only by its exact name.
  def selects(prefix: String, rule: String): Boolean =
    if prefix.forall(_.isLetter) && prefix.headOption.exists(_.isUpper)
    then rule.startsWith(prefix) && rule.drop(prefix.length).headOption.exists(_.isDigit)
    else rule == prefix || rule.startsWith(prefix+".")

  // Parse the plugin's `-P:flare:<option>` arguments. An option the plugin
  // does not recognise, or one whose value does not parse, is reported rather
  // than ignored: a silently-dropped `columns=120` would enforce a limit the
  // project did not ask for.
  //
  // The options are what `flare options` prints from a project's
  // `.pyrocosm/flare/config.tel`; the plugin has no TEL parser, so the command
  // is the bridge between the two.
  def parse(options: List[String]): (Config, List[String]) =
    var config = Config()
    val errors = List.newBuilder[String]

    def int(key: String, value: String)(update: Int => Unit): Unit =
      value.toIntOption match
        case Some(n) if n >= 0 => update(n)

        case _ =>
          errors += s"the `$key` option requires a non-negative integer, not `$value`"

    // A list-valued option's items. Both `,` and `;` separate, because the
    // compiler splits a `-P:plugin:key=a,b` argument on the comma before the
    // plugin ever sees it — the `b` arrives as a bare `-P:b` and is rejected
    // as an unknown option. A list passed through `-P` must therefore use `;`;
    // the comma is still accepted for a `Config` built directly.
    def items(value: String): List[String] =
      value.split("[,;]").nn.toList.map(_.nn.trim.nn).filter(_.nonEmpty)

    // A rule name's definition, created on first mention so that the
    // predicates of one rule may arrive as several options in any order.
    def define(name: String)(update: Definition => Definition): Unit =
      val existing = config.definition(name).getOrElse(Definition(name))
      val updated  = update(existing)
      config = config.copy(rules = config.rules.filterNot(_.name == name) :+ updated)

    options.foreach: option =>
      val (key, value) = option.indexOf('=') match
        case -1    => (option, "")
        case index => (option.substring(0, index).nn, option.substring(index + 1).nn)

      key match
        case "errors"        => config = config.copy(errors = true)
        case "columns"       => int(key, value): n => config = config.copy(columns = n)
        case "umbrella"      => config = config.copy(umbrella = Some(value).filter(_.nonEmpty))
        case "moduleRoot"    => config = config.copy(moduleRoot = value)
        case "language"      => config = config.copy(language = Some(items(value)))
        case "interpolators" => config = config.copy(interpolators = items(value).to(Set))
        case "strict"        => config = config.copy(strict = config.strict ++ items(value))
        case "omit"          => config = config.copy(omitted = config.omitted ++ items(value))
        case "census"        => config = config.copy(census = true)

        case "header" =>
          if value.isEmpty then config = config.copy(header = None)
          else readHeader(value) match
            case Some(lines) => config = config.copy(header = Some(lines))
            case None        => errors += s"the header file `$value` could not be read"

        // `gate=Unsafe:unsafe`; the prefix defaults to the token's simple name
        // in lower case. Several gates may be given as one `;`-separated list.
        case "gate" =>
          items(value).foreach: item =>
            val gate = item.split(":").nn.toList.map(_.nn) match
              case List(token, prefix) => Gate(token, prefix)
              case List(token)         => Gate(token, Gate(token, "").simpleToken.toLowerCase.nn)
              case _                   => Gate(item, item.toLowerCase.nn)

            config = config.copy(gates = config.gates :+ gate)

        // `rule=<name>:<kind>:<target>`, one predicate per option;
        // `rule=<name>:message:<text>` and `rule=<name>:severity:<level>`
        // set the rule's metadata, and `rule=<name>:exclude-name:<names>`
        // adds names (`;`-separated) the rule leaves alone. The target may
        // itself contain `:`, so only the first two are separators.
        case "rule" =>
          value.split(":", 3).nn.toList.map(_.nn) match
            case List(name, "message", text)  => define(name)(_.copy(message = Some(text)))
            case List(name, "severity", text) => define(name)(_.copy(severity = text))

            case List(name, "exclude-name", names) =>
              val (valid, invalid) = items(names).partition(Predicates.validateName(_).isEmpty)
              invalid.flatMap(Predicates.validateName).foreach: error => errors += error
              define(name): d => d.copy(excluded = d.excluded ++ valid)

            case List(name, kind, target) if Predicates.kinds.contains(kind) =>
              val predicate = Predicate(kind, target)
              val problem   = Predicates.validate(predicate)

              problem.foreach: error => errors += error

              if problem.isEmpty
              then define(name): d => d.copy(predicates = d.predicates :+ predicate)

            case List(name, kind, _) =>
              errors +=
                ( s"`$kind` is not a predicate; rule `$name` expects one of " +
                  Predicates.kinds.mkString("`", "`, `", "`") )

            case _ =>
              errors += s"the `rule` option requires `<name>:<predicate>:<target>`, not `$value`"

        // `enforce=cast` or `enforce=cast:error`.
        case "enforce" =>
          items(value).foreach: item =>
            item.split(":").nn.toList.map(_.nn) match
              case List(name, severity) =>
                config = config.copy(enforced = config.enforced + (name -> Some(severity)))

              case List(name) =>
                config = config.copy(enforced = config.enforced + (name -> None))

              case _ =>
                errors +=
                  s"the `enforce` option requires `<rule>` or `<rule>:<severity>`, not `$item`"

        case other =>
          errors +=
            ( s"`$other` is not a recognised option; expected one of `errors`, `header`, " +
              "`columns`, `umbrella`, `moduleRoot`, `language`, `interpolators`, `gate`, " +
              "`strict`, `omit`, `rule`, `enforce` or `census`" )

    // An enforced rule must be defined, and a defined rule must detect
    // something, or the configuration is not saying what it seems to say.
    config.enforced.keys.foreach: name =>
      if config.definition(name).isEmpty then errors += s"rule `$name` is enforced but not defined"

    config.rules.foreach: rule =>
      if rule.predicates.isEmpty then errors += s"rule `${rule.name}` has no predicates"

    (config, errors.result())

  // The header file's lines, or `None` if it cannot be read. A trailing
  // newline does not add a line: the header is the lines it contains.
  def readHeader(path: String): Option[List[String]] =
    try
      val text = String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(path)), "UTF-8")
      Some(lines(text))
    catch case _: Exception => None

  // Split text into lines, without a phantom empty line after a final newline.
  def lines(text: String): List[String] =
    val split = text.split("\n", -1).nn.toList.map(_.nn)
    if split.lastOption.contains("") then split.init else split

// The parameters a project fixes when it adopts the standard. Part I of
// doc/consequent-style.md states each principle in terms of these rather than
// of any particular project's choices: the licence header is the text in
// `header`, a line may be `columns` wide, and the umbrella re-export package —
// if the project has one — is named by `umbrella`.
//
// Every field has a default, so a project that adopts the conventional shape
// passes no options at all.
final case class Config
  ( // Report every violation as an error rather than a warning.
    errors: Boolean = false,

    // The licence header, line by line, which every file must begin with
    // verbatim. Line `header.length + 1` must be the `package` declaration.
    // `None` disables the header rules (F1, F2, F3) entirely, for a project
    // that has no licence header.
    header: Option[List[String]] = None,

    // The maximum width of a line, in columns.
    columns: Int = 100,

    // The umbrella package into which every public module is re-exported. When
    // unset, the umbrella-export rules (L4, L5, L6) do not fire — a project need
    // not have an umbrella package.
    umbrella: Option[String] = None,

    // The path segment below which each module has its own directory, so that
    // `<moduleRoot>/<module>/src/...` identifies the module a file belongs to.
    moduleRoot: String = "lib",

    // The `-language` features to enable when parsing a file standalone. When
    // unset, the compatibility default for the running compiler is used. The
    // standalone parse must match the compiler's own parse, or the tree-based
    // rules see a different program from the one being compiled.
    language: Option[List[String]] = None,

    // Interpolators whose leading and trailing whitespace is insignificant, so
    // that a multi-line `"""…"""` argument may be laid out as an indented
    // block (A8). Every other interpolator carries significant whitespace and
    // is exempt from the layout rules.
    interpolators: Set[String] = Set("m", "j", "x", "y", "tel"),

    // The project's gates, for S1 and the census. None means S1 never fires.
    gates: List[Gate] = Nil,

    // Rules reported as errors even when `errors` is false, named by rule id
    // or by principle prefix (`S1`, or `S` for every soundness rule). A project
    // adopting the standard gradually can hold the rules it has already
    // satisfied at error severity without promoting the rest.
    strict: Set[String] = Set.empty,

    // Rules of the style left unchecked, named the same way.
    omitted: Set[String] = Set.empty,

    // Every project rule the configuration defines, enforced or not: the
    // census counts each one's matches.
    rules: List[Definition] = Nil,

    // The project rules to check, by name, each with an optional severity
    // (`warn` or `error`) overriding the rule's own.
    enforced: Map[String, Option[String]] = Map.empty,

    // Whether to count rule matches and gate uses per file. Costs one extra
    // traversal per file, which is why it is opt-in; the `flare metrics`
    // command sets it.
    census: Boolean = false ):

  // The number of lines the licence header occupies.
  def headerLines: Int = header.fold(0)(_.length)

  // The line on which the `package` declaration must appear.
  def packageLine: Int = headerLines + 1

  // Does this project have a licence header at all?
  def framed: Boolean = header.exists(_.nonEmpty)

  // Is this rule one the project holds at error severity?
  def strictly(rule: String): Boolean = strict.exists(Config.selects(_, rule))

  // Is this rule one the project has left out?
  def omits(rule: String): Boolean = omitted.exists(Config.selects(_, rule))

  // The project rule definition of this name, if there is one.
  def definition(name: String): Option[Definition] = rules.find(_.name == name)
