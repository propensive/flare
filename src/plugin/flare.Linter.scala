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

import dotty.tools.dotc.ast.untpd
import dotty.tools.dotc.util.SourceFile

// The per-file engine shared by the compiler plugin and the `flare` command:
// given a configuration and a parsed file, the violations to report, each
// with its severity, and — when the configuration asks for it — the census.
// Both callers hand over the compiler's own untyped tree, so a file is never
// parsed twice and the two can never disagree about what it contains.
object Linter:
  enum Severity:
    case Warning, Error

  final case class Report
    ( violations: List[(Violation, Severity)], census: List[(String, Int)] ):
    def errors: Boolean = violations.exists(_(1) == Severity.Error)

  def check
    ( config:   Config,
      path:     String,
      text:     String,
      tree:     untpd.Tree,
      source:   SourceFile,
      features: List[String] )
  :   Report =

    val module   = Checker.expectedModule(path, config.moduleRoot)
    val siblings = Umbrella.siblings(path, config, features)

    val ctx =
      Context
        ( path, module, text, tree, source, siblings.modules, siblings.extensions,
          siblings.unexported, config, siblings.elsewhere )

    val violations = Checker.check(ctx).toList.map: v => (v, severity(config, v.rule))
    val census     = if config.census then Metrics.collect(ctx) else Nil

    Report(violations, census)

  // The severity a violation of `rule` carries. A project rule's enforcement
  // may name one; failing that, the rule's own; a style rule is an error when
  // the project holds it, or every rule, at error severity.
  def severity(config: Config, rule: String): Severity =
    val family = rule.takeWhile(_ != '.')

    val level: Option[String] =
      config.enforced.get(family).flatten.orElse(config.definition(family).map(_.severity))

    level match
      case Some("error")                                  => Severity.Error
      case Some(_)                                        => Severity.Warning
      case None if config.errors || config.strictly(rule) => Severity.Error
      case None                                           => Severity.Warning
