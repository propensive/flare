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

case class DefnAnchor
  ( anchorLine: Int,
    anchorCol:  Int,
    colonLine:  Int,
    colonCol:   Int )

object Definitions:
  // Walk the untyped tree for every `ValDef`/`DefDef` that carries an
  // explicit type ascription, and record (a) the column of the leftmost
  // non-whitespace token on the keyword's line — the "anchor", typically
  // a leading modifier or the keyword itself (`val`/`var`/`def`/`given`) —
  // and (b) the position of the `:` that introduces the type. ↯/c.2.3
  // (↯/c.2.3) then requires the `:` to sit either on the anchor line or in
  // the anchor column on a fresh line.
  //
  // This is tree-driven so we don't need to maintain cross-line bracket-
  // depth state to decide when a `=` ends the signature region. The
  // parser already pairs each `:` with its containing definition.
  def extract(tree: untpd.Tree, source: SourceFile): List[DefnAnchor] =
    val out     = mutable.ListBuffer[DefnAnchor]()
    val content = String(source.content)

    walk(tree): t =>
      t match
        case d: untpd.ValOrDefDef => recordIfAnnotated(d, source, content, out)
        case _                    => ()

    out.toList

  private def recordIfAnnotated
    ( d:       untpd.ValOrDefDef,
      source:  SourceFile,
      content: String,
      out:     mutable.ListBuffer[DefnAnchor] )
  :   Unit =

    val tpt = d.tpt
    if !tpt.span.exists || tpt.span.isZeroExtent then return
    if !d.span.exists then return

    // The colon is the last non-whitespace character before the `tpt`'s
    // start (the only thing the parser could have written there is `:`).
    var i = tpt.span.start - 1
    while i >= 0 && i < content.length && content.charAt(i).isWhitespace do i -= 1
    if i < 0 || i >= content.length || content.charAt(i) != ':' then return

    val colonOffset = i
    val colonLine   = source.offsetToLine(colonOffset) + 1
    val colonCol    = source.column(colonOffset) + 1

    // The keyword line is the line of the definition's `point` (`val`/`def`
    // for member defs, or the identifier for parameter `ValDef`s). The
    // anchor column is the leftmost non-whitespace column on that line,
    // capturing any leading modifiers.
    val pointOffset = d.span.point.max(0).min(content.length - 1)
    val anchorLine  = source.offsetToLine(pointOffset) + 1
    val anchorCol   = leadingColumn(content, source, anchorLine)

    out += DefnAnchor(anchorLine, anchorCol, colonLine, colonCol)

  private def leadingColumn(content: String, source: SourceFile, line: Int): Int =
    val start = source.lineToOffset(line - 1)
    var i     = start
    while i < content.length &&
      content.charAt(i) != '\n' &&
      (content.charAt(i) == ' ' || content.charAt(i) == '\t')
    do i += 1

    source.column(i) + 1

  // Generic untyped-tree pre-order traversal driven off `productIterator`,
  // mirroring the helper in `Annotations` and `Comprehensions`.
  private def walk(t: untpd.Tree)(visit: untpd.Tree => Unit): Unit =
    visit(t)
    t.productIterator.foreach(descend(_, visit))

  private def descend(x: Any, visit: untpd.Tree => Unit): Unit = x match
    case sub: untpd.Tree  => walk(sub)(visit)
    case it:  Iterable[?] => it.foreach(descend(_, visit))
    case _                => ()
