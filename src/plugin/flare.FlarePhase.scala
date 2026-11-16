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

import scala.collection.mutable

import dotty.tools.dotc.*, ast.tpd, core.Contexts.*, plugins.*, util.{SourceFile, SourcePosition}
import dotty.tools.dotc.util.Spans.Span

// The compiler plugin's phase: a thin adapter from a compilation unit to the
// `Linter`, reporting what it finds through the compiler's own reporter. It
// runs after the typer only because a standard plugin cannot run earlier; the
// rules themselves read the parser's output.
class FlarePhase(options: List[String]) extends PluginPhase:
  val phaseName: String                = "flare"
  override val runsAfter: Set[String]  = Set("typer")
  override val runsBefore: Set[String] = Set("pickler")

  private val (config, optionErrors) = Config.parse(options)

  private val seen: mutable.Set[String] = mutable.Set.empty
  private var reportedOptions: Boolean = false

  // The `-language` features this compilation is running with. The standalone
  // parser used for sibling files must agree with the compiler's own parse, so
  // they are read from the live context rather than assumed — a plugin built
  // against one compiler is then correct on any compiler that accepts it.
  private def languageFeatures(using context: Context): List[String] =
    config.language.getOrElse:
      try
        import dotty.tools.dotc.config.Settings.Setting.value
        value(context.settings.language)(using context).map(Parsing.name)
      catch case _: Throwable => Nil

  override def transformUnit(tree: tpd.Tree)(using context: Context): tpd.Tree =
    val source: SourceFile = context.compilationUnit.source
    val path: String       = source.file.nn.path

    if !reportedOptions then
      reportedOptions = true

      // Each is labelled already with its variant of `↯/f.21.3`.
      optionErrors.foreach: error => report.warning(error)

    if seen.add(path) then
      val text: String = String(source.content)

      val useColor =
        try
          import dotty.tools.dotc.config.Settings.Setting.value
          value(context.settings.color)(using context) != "never"
        catch case _: Throwable => false

      val result =
        Linter.check
          ( config, path, text, context.compilationUnit.untpdTree, source, languageFeatures )

      result.violations.foreach: (violation, severity) =>
        val pos   = position(source, violation.line, violation.column)
        val msg   = FlareBolts.prefix(violation.rule, useColor)+violation.message
        val fatal = severity == Linter.Severity.Error

        // Rendering a diagnostic at `pos` exercises the compiler's own message
        // renderer, which can throw on a pathological position (e.g. one mapping
        // into an empty or truncated source). A house-style check must never abort
        // the build because of that: fall back to a position-less diagnostic.
        try if fatal then report.error(msg, pos) else report.warning(msg, pos)
        catch case _: Throwable =>
          if fatal then report.error(msg) else report.warning(msg)

    super.transformUnit(tree)

  private def position(source: SourceFile, line: Int, column: Int): SourcePosition =
    val lineStart =
      try source.lineToOffset((line - 1).max(0))
      catch case _: Throwable => 0

    val offset = (lineStart + (column - 1).max(0)).min(source.content.length)
    SourcePosition(source, Span(offset))
