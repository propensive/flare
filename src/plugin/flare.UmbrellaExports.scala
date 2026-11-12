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

// One name an export surface puts into the umbrella package: the name as the
// umbrella sees it (the rename, for `x as y`), the `package <name>:` blocks it
// sits inside (`Nil` for the umbrella package itself), and the offset of the
// name in the source.
final case class Exported(name: String, scope: List[String], offset: Int):
  // The name qualified by its blocks, which is what must be unique: blocks of
  // the same name from different surfaces merge into one package.
  def qualified: String = (scope :+ name).mkString(".")

object UmbrellaExports:
  // Collect the simple leaf names re-exported by every top-level `export`
  // statement in the file (including those nested inside `package x:` blocks),
  // together with the line of the first such statement. Used by L4 to check
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

  // Every name the surface exports, with the `package <name>:` blocks it sits
  // in. The wildcard and `given` selectors name nothing, and `x as _` hides
  // `x` rather than exporting it.
  def exported(tree: untpd.Tree): List[Exported] =
    val out = mutable.ListBuffer[Exported]()

    def record(exp: untpd.Export, scope: List[String]): Unit =
      exp.selectors.foreach: selector =>
        val name = selector.rename.toString

        if !selector.isWildcard && name.nonEmpty && name != "_" then
          val named  = if selector.renamed.isEmpty then selector.imported else selector.renamed
          val offset = if named.span.exists then named.span.start else -1
          out += Exported(name, scope, offset)

    // The outermost package is the umbrella; each block nested in it adds a
    // segment to the scope.
    def visit(t: untpd.Tree, scope: List[String], outermost: Boolean): Unit = t match
      case pkg: untpd.PackageDef =>
        val inner = if outermost then scope else scope ++ segments(pkg.pid)
        pkg.stats.foreach(visit(_, inner, false))

      case exp: untpd.Export => record(exp, scope)
      case _                 => ()

    visit(tree, Nil, true)
    out.toList

  private def segments(tree: untpd.Tree): List[String] = tree match
    case untpd.Ident(name)          => List(name.toString)
    case untpd.Select(prefix, name) => segments(prefix) :+ name.toString
    case _                          => Nil
