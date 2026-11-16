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

object QuoteRules:
  // ↯/c.2.9: the layout of macro quotes and splices (↯/c.2.9.1–↯/c.2.9.6, see §5
  // "Macro quotes and splices" in doc/consequent-style.md). This family
  // is contractually frozen: the sites come from `QuoteSites.extract`
  // (via `ctx.quoteSites`) and this rule reproduces the original
  // checker's violations exactly.
  //
  // A multi-line quote/splice (`' {` or `$ {` ending its line, with the
  // matching `}` on a later line) expects:
  //
  //   (a) a single space between `'`/`$` and `{`        — ↯/c.2.9.2
  //   (b) the `' {` pair alone on its own line          — ↯/c.2.9.3
  //   (c) body lines indented to column `{`-col + 2     — ↯/c.2.9.4
  //   (d) `}` alone on its line at the column of `{`    — ↯/c.2.9.1 / ↯/c.2.9.5
  //
  // An inline quote/splice (`'{x}`, `'[T]`, `${x}`) must not pad its
  // contents with a space directly after the opener or directly before
  // the closer — ↯/c.2.9.6.
  //
  // Emission preserves the original per-line order — ↯/c.2.9.4 first, then
  // multi-line opener/closer sites in token order, then inline sites —
  // so positional collisions (a misaligned closer draws ↯/c.2.9.4, ↯/c.2.9.1 and
  // ↯/c.2.9.5 at one position) keep reporting the same diagnostic first.
  object QuoteSpliceLayout extends Rule:
    def id: String = "c.2.9"
    def principle: Principle = Principle.Anchoring

    def check(ctx: Context): List[Violation] =
      val out    = mutable.ListBuffer[Violation]()
      val model  = ctx.quoteSites
      val byLine = model.sites.groupBy(_.line)
      var idx    = 0

      while idx < ctx.lines.length do
        val line    = ctx.lines(idx)
        val lineNum = idx + 1

        model.bodyTops.get(lineNum).foreach: top =>
          checkBodyIndent(ctx.file, line, lineNum, top, out)

        byLine.getOrElse(lineNum, Nil).foreach: site =>
          site match
            case site: QuoteSites.Opener => checkOpener(ctx.file, site, out)
            case site: QuoteSites.Closer => checkCloser(ctx.file, site, out)
            case site: QuoteSites.Inline => checkInline(ctx.file, site, out)

        idx += 1

      out.toList

    // (c) On lines inside an open multi-line quote/splice — neither the
    // opener nor the closer — the first non-whitespace character must
    // sit at column `{`+2 (the canonical body indent). Continuations and
    // sub-expressions inside a body statement may indent further; only
    // dedents below the canonical column are flagged. Lines that begin
    // inside a multi-line string literal (Sort.Strs) or are comment-only
    // don't count as body lines for this rule — their layout is governed
    // by the surrounding string/comment, not by the quote's indent.
    private def checkBodyIndent
      ( file:    String,
        line:    Line,
        lineNum: Int,
        top:     QuoteSites.BodyTop,
        out:     mutable.ListBuffer[Violation] )
    :   Unit =

      val stringLike = line.firstReal.exists: t => t.kind == Sort.Strs || t.kind == Sort.Comment

      val alignedCloser =
        line.firstReal.exists(_.text == "}") && line.leadingCols + 1 == top.braceCol

      val skip = line.isBlank || lineNum == top.openerLine || stringLike || alignedCloser

      if !skip && line.leadingCols < top.braceCol + 1 then
        out +=
          Violation
            ( file, lineNum, line.leadingCols + 1, "c.2.9.4",
              s"body of a multi-line quote/splice must be indented to column " +
                s"${top.braceCol + 2} (found ${line.leadingCols + 1})" )

    // (a) Exactly one space character between `'`/`$` and `{` — ↯/c.2.9.2.
    // (b) Only enclosing opening brackets (`(`, `{`) may sit before
    // `'`/`$` on the line; anything else is a violation — ↯/c.2.9.3.
    private def checkOpener
      ( file: String, site: QuoteSites.Opener, out: mutable.ListBuffer[Violation] )
    :   Unit =

      if !site.singleSpace then
        out +=
          Violation
            ( file, site.line, site.braceCol, "c.2.9.2",
              s"`${site.prefix}` and `{` of a multi-line quote/splice must " +
                "be separated by exactly one space" )

      if site.badBefore then
        out +=
          Violation
            ( file, site.line, site.firstSemanticCol, "c.2.9.3",
              s"the `${site.prefix} {` opener of a multi-line quote/splice " +
                "must be alone on its line (only enclosing `(` / `{` permitted)" )

    // (d-col) The closer column matches the opening `{` — ↯/c.2.9.1.
    // (d-alone) Nothing semantic before `}` on the line; only enclosing
    // closing brackets (`)`, `}`) may trail it — plus a trailing symbolic
    // infix operator that is the last token on the line, which is an ↯/c.4.2
    // operator continuation (`'{ … } ::` cons'd with an operand on the
    // next line) and is governed by that rule — ↯/c.2.9.5.
    private def checkCloser
      ( file: String, site: QuoteSites.Closer, out: mutable.ListBuffer[Violation] )
    :   Unit =

      if site.col != site.openBraceCol then
        out +=
          Violation
            ( file, site.line, site.col, "c.2.9.1",
              s"closing `}` of a multi-line quote/splice at column ${site.col} " +
                s"does not align with its opening `{` at column ${site.openBraceCol}" )

      if site.semanticBefore || site.badAfter then
        out +=
          Violation
            ( file, site.line, site.col, "c.2.9.5",
              "closing `}` of a multi-line quote/splice must be alone on its line " +
                "(only trailing `)` / `}` permitted)" )

    // ↯/c.2.9.6: a quote/splice whose opener and closer sit on the same
    // source line is "inline"; its contents must not be padded by a space
    // immediately after the opener or immediately before the closer.
    // Covers term quotes (`'{…}`), type quotes (`'[…]`), and term
    // splices (`${…}`). Multi-line quote/splices are handled by
    // ↯/c.2.9.1–↯/c.2.9.5.
    private def checkInline
      ( file: String, site: QuoteSites.Inline, out: mutable.ListBuffer[Violation] )
    :   Unit =

      val closeText = if site.openText == "{" then "}" else "]"

      if site.spaceAfterCol >= 0 then
        out +=
          Violation
            ( file, site.line, site.spaceAfterCol, "c.2.9.6",
              s"no space is permitted directly after `${site.prefix}${site.openText}` " +
                "in an inline quote/splice" )

      if site.spaceBeforeCol >= 0 then
        out +=
          Violation
            ( file, site.line, site.spaceBeforeCol, "c.2.9.6",
              s"no space is permitted directly before `$closeText` " + "in an inline quote/splice" )
