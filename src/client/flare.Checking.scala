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
import pyrocosm.{Board, Status as Gauge}

// `flare.Frontend`, qualified throughout: the `soundness.*` wildcard exports scintillate's
// `Frontend` too, now that the web front-end is on the classpath.

// One check, as the command line and the dashboard both run it: the compiler over a profile's
// files, then the linter over each unit, every finding handed to a sink as it is found, under a
// gauge of files parsed and then checked. The sink decides where it all goes — a terminal
// board, or the dashboard's cells — and can call the check off.
object Checking:
  object Sink:
    // A board: each finding scrolls in as an excerpt, beneath the gauge, as it is found.
    def apply(board: Board, root: Text): Sink = new Sink:
      def aborted: Boolean = board.aborted
      def gauge(status: Gauge, caption: Text): Unit = board.gauge(status, caption)

      def found(finding: Report.Finding, text: Text): Unit =
        board.append(Report.excerpt(root, finding, text))

  trait Sink:
    def aborted: Boolean
    def gauge(status: Gauge, caption: Text): Unit
    def found(finding: Report.Finding, text: Text): Unit

  // What a check found: the compiler's result over `files` and the linter's findings.
  case class Outcome(files: Int, result: flare.Frontend.Result, findings: List[Report.Finding]):
    def texts: Map[Text, Text] =
      Map.from(result.units.map { (unit: flare.Frontend.Parsed) => (unit.path, unit.text) }.stdlib)

    def parseErrors: List[flare.Frontend.Diagnostic] = result.diagnostics.filter(_.error)

    // Every finding, a parse error among them under the rule `parse`.
    def all: List[Report.Finding] =
      val failures = parseErrors.map: (d: flare.Frontend.Diagnostic) =>
        Report.Finding(d.path, d.line, d.column, t"parse", d.message, true)

      failures + findings

    def errors: Boolean = !parseErrors.nil || findings.exists(_.error)

  def run(profile: Workspace.Profile, pluginConfig: Config, files: List[Text], sink: Sink)
  :   Outcome =

    val enabled = Rules.enabled(pluginConfig)
    val features = profile.languages.map(_.s).stdlib
    val parsed: Atomic[Int] = Atomic(0)
    val checked: Atomic[Int] = Atomic(0)

    val result =
      flare.Frontend.run(files, profile.languages, Rules.lastPhase(enabled).tt): (phase: String) =>
        if phase == "parser" then
          sink.gauge
            ( Gauge.Reckoning(parsed.since(_ + 1).toLong, files.size.toLong), t"Parsing" )

    def check(unit: flare.Frontend.Parsed): List[Report.Finding] =
      if sink.aborted then Nil else
        val report =
          Linter.check(pluginConfig, unit.path.s, unit.text.s, unit.tree, unit.source, features)

        val found: List[Report.Finding] =
          List.from:
            report.violations.map: (violation: Violation, severity: Linter.Severity) =>
              Report.Finding
                ( violation.file.tt,
                  violation.line,
                  violation.column,
                  violation.rule.tt,
                  violation.message.tt,
                  severity == Linter.Severity.Error )

        found.each: (finding: Report.Finding) => sink.found(finding, unit.text)

        val total = result.units.size.toLong
        sink.gauge(Gauge.Reckoning(checked.since(_ + 1).toLong, total), t"Checking")

        found

    val findings: List[Report.Finding] =
      result.units.bind[List[Report.Finding], Report.Finding, List[Report.Finding]](check)

    Outcome(files.size, result, findings)
