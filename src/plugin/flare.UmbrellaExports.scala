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

import dotty.tools.dotc.ast.untpd
import dotty.tools.dotc.util.SourceFile

case class ExportInfo(names: Set[String], firstLine: Int)

object UmbrellaExports:
  // Collect the simple leaf names re-exported by every top-level `export`
  // statement in the file (including those nested inside `package x:` blocks),
  // together with the line of the first such statement. Used by ↯/c.8.4 to check
  // that each public module in a component is re-exported into the umbrella
  // package. `packageLine` is the fallback position, used when a surface
  // contains no exports at all and there is no export to point at.
  def extract(tree: untpd.Tree, source: SourceFile, packageLine: Int = 33): ExportInfo =
    val names     = mutable.Set[String]()
    var firstLine = -1

    def record(exp: untpd.Export): Unit =
      val span = exp.span

      if span.exists then
        val line = source.offsetToLine(span.start) + 1
        if firstLine < 0 || line < firstLine then firstLine = line

      exp.selectors.foreach: selector =>
        // Skip the wildcard selector (`export foo.*`) via `isWildcard`, not by
        // name: a backtick-quoted `` `*` `` exports the *multiplication operator*,
        // whose leaf name is also "*", and must be recorded.
        if !selector.isWildcard then
          val name = selector.imported.name.toString
          if name.nonEmpty && name != "_" then names += name

    def visit(t: untpd.Tree): Unit = t match
      case pkg: untpd.PackageDef => pkg.stats.foreach(visit)
      case exp: untpd.Export     => record(exp)
      case _                     => ()

    visit(tree)

    ExportInfo(names.to(Set), if firstLine < 0 then packageLine else firstLine)
