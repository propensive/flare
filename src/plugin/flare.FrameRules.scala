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

object FrameRules:
  
  // ↯/c.1.4: hard limit of `columns` columns. Interior lines of multi-line
  // triple-quoted strings are exempt: their text is string content, whose
  // width is governed by ↯/c.2.8 for the layout interpolators and is
  // significant data for the rest.
  object LineLength extends Rule:
    def id: String = "c.1.4"
    def principle: Principle = Principle.Frame

    def check(ctx: Context): List[Violation] =
      val out = mutable.ListBuffer[Violation]()
      var idx = 0

      while idx < ctx.lines.length do
        val line = ctx.lines(idx)

        val isStringContinuation =
          line.firstReal.exists(_.kind == Sort.Strs) && line.leadingWs.isEmpty

        if !isStringContinuation && line.visibleLen > ctx.config.columns then
          out +=
            Violation
              ( ctx.file, idx + 1, ctx.config.columns + 1, "c.1.4",
                s"line exceeds ${ctx.config.columns} columns " +
                  s"(is ${line.visibleLen} columns)" )

        idx += 1

      out.toList

  // ↯/c.1.5: no tab characters anywhere in the file. This is a raw
  // pre-tokenizer scan: it walks `ctx.text` verbatim, character by
  // character, exactly as the old preamble scan did before tokenization —
  // so it also reaches tabs inside strings and comments, which the
  // tokenized `ctx.lines` view would classify differently.
  object RawTabs extends Rule:
    def id: String = "c.1.5"
    def principle: Principle = Principle.Frame

    def check(ctx: Context): List[Violation] =
      val out  = mutable.ListBuffer[Violation]()
      val text = ctx.text
      var line = 1
      var col  = 1
      var i    = 0

      while i < text.length do
        val ch = text.charAt(i)

        if ch == '\t' then
          val message = "tab character is not permitted; use spaces"
          out += Violation(ctx.file, line, col, "c.1.5", message)

        if ch == '\n' then
          line += 1
          col = 1
        else
          col += 1

        i += 1

      out.toList

  // ↯/c.1.6: the leading indent of every code line must be an even
  // number of columns. The rule suspends inside open `(...)` blocks:
  // continuation rows inside parameter lists align under names from the
  // opener line and may need an odd number of leading spaces (e.g. under
  // `inline commensurable` at col 18). The suspension tracker is a
  // rule-local copy of the walk's `openParens` counter rather than a view
  // over `ctx.brackets`: the old counter paired only round parentheses
  // (never `[`/`]`) and clamped its depth at zero at the end of every
  // line, neither of which the bracket-pair table reproduces.
  object IndentWidth extends Rule:
    def id: String = "c.1.6"
    def principle: Principle = Principle.Frame

    def check(ctx: Context): List[Violation] =
      val out        = mutable.ListBuffer[Violation]()
      var openParens = 0
      var idx        = 0

      while idx < ctx.lines.length do
        val line            = ctx.lines(idx)
        val isStringContent = line.firstReal.exists(_.kind == Sort.Strs)

        if !isStringContent && openParens == 0 && !line.isBlank && line.leadingCols%2 != 0 then
          out +=
            Violation
              ( ctx.file, idx + 1, 1, "c.1.6",
                s"indent width ${line.leadingCols} is not a multiple of 2" )

        var depth = openParens
        var i     = 0

        while i < line.rest.length do
          val t = line.rest(i)

          if t.kind == Sort.Code then
            if t.text == "(" then depth += 1 else if t.text == ")" then depth -= 1

          i += 1

        openParens = depth max 0
        idx += 1

      out.toList

  // ↯/c.1.7: no trailing whitespace at the end of a line. Interior (and
  // closing) lines of multi-line triple-quoted strings are exempt: they
  // tokenize as a single `Strs` token with no leading `Space`, and their
  // text is string content — significant for `sh`/raw strings, prose for
  // the layout interpolators — so the rule must not fire on them.
  // Whitespace-only lines (no non-space, non-comment token) don't count
  // as trailing either.
  object TrailingWhitespace extends Rule:
    def id: String = "c.1.7"
    def principle: Principle = Principle.Frame

    def check(ctx: Context): List[Violation] =
      val out = mutable.ListBuffer[Violation]()
      var idx = 0

      while idx < ctx.lines.length do
        val line = ctx.lines(idx)

        val isStringContinuation =
          line.firstReal.exists(_.kind == Sort.Strs) && line.leadingWs.isEmpty

        if !isStringContinuation then
          line.lexemes.lastOption match
            case Some(token) if token.kind == Sort.Space && token.text.length > 0 =>
              val hasNonWs =
                line.lexemes.exists: t => t.kind != Sort.Space && t.kind != Sort.Comment

              if hasNonWs then
                val col = line.lexemes.iterator.map(_.text.length).sum - token.text.length + 1
                out += Violation(ctx.file, idx + 1, col, "c.1.7", "line has trailing whitespace")

            case _ =>
              ()

        idx += 1

      out.toList

  // ↯/c.1.8: block comments. `/** ... */` doc comments are never permitted
  // (↯/c.1.8.2 — documentation lives in `doc/` markdown), and `/* ... */`
  // block comments are reserved for the license header on lines 1–32
  // (↯/c.1.8.1). The rule spans the Frame, Proximity and Locatability
  // principles; it lives here with the Frame family because its licence
  // exemption is defined by the same fixed frame `LicenceFrame` enforces.
  object CommentShape extends Rule:
    def id: String = "c.1.8"
    def principle: Principle = Principle.Frame

    def check(ctx: Context): List[Violation] =
      val out = mutable.ListBuffer[Violation]()
      var idx = 0

      while idx < ctx.lines.length do
        val lineNum   = idx + 1
        val line      = ctx.lines(idx)
        val arr       = line.arr
        val cols      = line.cols
        val inLicense = lineNum >= 1 && lineNum <= ctx.config.headerLines
        var i         = 0

        while i < arr.length do
          if arr(i).kind == Sort.Comment then
            val text = arr(i).text

            if text.startsWith("/**") then
              out +=
                Violation
                  ( ctx.file, lineNum, cols(i), "c.1.8.2",
                    "`/** ... */` block comments are not permitted; use `doc/` markdown instead" )
            else if text.startsWith("/*") && !inLicense && ctx.config.framed then
              out +=
                Violation
                  ( ctx.file, lineNum, cols(i), "c.1.8.1",
                    "`/* ... */` block comments are reserved for the license header " +
                      s"(lines 1-${ctx.config.headerLines})" )

          i += 1

        idx += 1

      out.toList

  // ↯/c.1.1: the licence header — the text of the project's header file — occupies
  // the first `header.length` lines of every file, verbatim. A project that
  // sets no header has no ↯/c.1.1. One violation is enough: the first line that
  // differs is where the author should look, and a header that is wrong on
  // one line is usually wrong on many.
  //
  // A header file may be a template: a `${NAME}` placeholder (the `envsubst`
  // convention, which is how such a file is applied) stands for any text, and
  // the padding after it may then differ in length too, so a run of spaces in
  // a template line matches any run of spaces.
  object LicenceFrame extends Rule:
    def id: String = "c.1.1"
    def principle: Principle = Principle.Frame

    def check(ctx: Context): List[Violation] =
      ctx.config.header.filter(_.nonEmpty).to(List).flatMap: header =>
        val lines = Config.lines(ctx.text)

        header.zipWithIndex.collectFirst:
          case (expected, idx) if !lines.lift(idx).exists(matches(expected, _)) =>
            val number = idx + 1

            val reason =
              if idx >= lines.length then "the file ends before the licence header does"
              else s"line $number must be the licence header's, `${expected.trim}`"

            Violation(ctx.file, number, 1, "c.1.1", reason)

    private val Placeholder = """\$\{[A-Za-z_][A-Za-z0-9_]*\}""".r

    // Does a source line match a header line? Exactly, unless the header line
    // is a template.
    def matches(expected: String, actual: String): Boolean =
      if !expected.contains("${") then expected == actual
      else
        val literal = Placeholder.split(expected).toList
        val pattern = literal.map(quoted).mkString("(?s).*")
        val trailing = if Placeholder.findAllIn(expected).size >= literal.length then ".*" else ""
        (pattern + trailing).r.matches(actual)

    // A literal fragment as a regex, with a run of two or more spaces standing
    // for any run of spaces.
    private def quoted(fragment: String): String =
      fragment.split("  +", -1).nn.toList.map: part => java.util.regex.Pattern.quote(part).nn
        . mkString(" +")

  // ↯/c.1.2: validate the file's `package` declaration. The parsed package
  // info distinguishes "no real declaration" (empty-package wrapper)
  // from a real `PackageDef`. Multi-segment paths (`a.b`), wrong line,
  // names that don't match `expectedModule`, names with invalid
  // characters (e.g. backticked identifiers), and extra statements on
  // the same line are each rejected with rule "c.1.2".
  object PackageDeclaration extends Rule:
    def id: String = "c.1.2"
    def principle: Principle = Principle.Frame

    def check(ctx: Context): List[Violation] =
      val file = ctx.file
      val out  = mutable.ListBuffer[Violation]()

      ctx.packageInfo match
        case None =>
          out +=
            Violation
              ( file, ctx.config.packageLine, 1, "c.1.2",
                s"line ${ctx.config.packageLine} must be `package <module>`" )

        case Some(p) if p.line != ctx.config.packageLine =>
          out +=
            Violation
              ( file, p.line, 1, "c.1.2",
                s"expected `package` declaration on line ${ctx.config.packageLine}, " +
                  s"found content on line ${p.line}" )

        case Some(p) =>
          val name = p.segments.mkString(".")

          if p.segments.length > 1 || !p.segments.head.matches("[A-Za-z_][A-Za-z0-9_]*") then
            out +=
              Violation
                ( file, p.line, 1, "c.1.2",
                  s"package declaration must be a single identifier segment, not `$name`" )
          else
            ctx.expectedModule.foreach: expected =>
              if name != expected then
                out +=
                  Violation
                    ( file, p.line, 1, "c.1.2",
                      s"package `$name` does not match expected module `$expected`" )

          if p.extraStatementOnSameLine then
            out +=
              Violation
                ( file, p.line, 1, "c.1.2",
                  "package declaration must contain only `package <ident>` on line " +
                    s"${ctx.config.packageLine}" )

      out.toList

  // ↯/c.1.3: when the `package` declaration sits correctly on its line, the next
  // line must be a single blank line separating it from what follows.
  object PackageBlank extends Rule:
    def id: String = "c.1.3"
    def principle: Principle = Principle.Frame

    def check(ctx: Context): List[Violation] =
      val blankLine     = ctx.config.packageLine + 1
      val packagePlaced = ctx.packageInfo.exists(_.line == ctx.config.packageLine)

      if packagePlaced && ctx.lines.length >= blankLine && !ctx.lines(blankLine - 1).isBlank then
        List
          ( Violation
              ( ctx.file, blankLine, 1, "c.1.3",
                s"line $blankLine must be a single blank line after `package`" ) )
      else
        Nil

  // ↯/c.6.5: a blank line must separate the import region from the
  // first declaration. The scan begins on line 35 (after the package
  // declaration and its blank line) and ends at the first line that is
  // neither blank nor part of an import; if that line directly abuts the
  // line above it, the separator is missing. Only applies when the file
  // has a correctly-placed package declaration and at least one import.
  object ImportSeparation extends Rule:
    def id: String = "c.6.5"
    def principle: Principle = Principle.Proximity

    def check(ctx: Context): List[Violation] =
      if !ctx.packageInfo.exists(_.line == ctx.config.packageLine) || ctx.imports.isEmpty then Nil
      else
        val importLineSet = ctx.imports.iterator.flatMap { i => i.startLine to i.endLine }.toSet
        var idx    = ctx.config.packageLine + 1
        var result = List[Violation]()
        var done   = false

        while !done && idx < ctx.lines.length do
          val line    = ctx.lines(idx)
          val lineNum = idx + 1

          if !line.isBlank && !importLineSet.contains(lineNum) then
            if idx > 0 && !ctx.lines(idx - 1).isBlank then
              result =
                List
                  ( Violation
                      ( ctx.file, lineNum, 1, "c.6.5",
                        "missing blank line between imports and first declaration" ) )

            done = true

          idx += 1

        result

  // ↯/c.8.1: classify and order the parsed top-level imports. Multi-line
  // imports (`import a.{\n  b,\n  c\n}`) span lines naturally via the tree,
  // and multi-import lines (`import a.b, c.d`) appear as multiple Import
  // nodes whose `startLine` collide — only the first import on each line
  // drives the group, the rest are treated as continuations.
  object ImportOrdering extends Rule:
    def id: String = "c.8.1"
    def principle: Principle = Principle.Locatability

    def check(ctx: Context): List[Violation] =
      val file = ctx.file
      val out  = mutable.ListBuffer[Violation]()

      val rows: List[ImportInfo] =
        ctx.imports.groupBy(_.startLine).toList.sortBy(_._1).flatMap(_._2.headOption)

      var prevGroup:   Option[Int]    = None
      var prevName:    Option[String] = None
      var prevEndLine: Int            = -1

      rows.foreach: imp =>
        val group = classifyImport(imp.path)

        // Top-level aliases are forbidden only for project-family imports (group 5+).
        // Standard-library aliases (`import scala.collection.mutable as scm`,
        // `import java.util.concurrent as juc`) and language-feature aliases
        // remain an established convention.
        if imp.hasTopLevelAlias && group >= 5 then
          out +=
            Violation
              ( file, imp.startLine, 1, "c.8.1.1",
                "top-level imports must not use aliases (`as` or `=>`); write the full path" )

        prevGroup match
          case Some(pg) =>
            // Groups 5 and 6 are "third-party" siblings: lowercase wildcard
            // (`import contingency.*`) and other named imports
            // (`import filesystemOptions.x`, `import AsyncError.Reason`)
            // routinely interleave in project code, so we don't require
            // ordering or blank lines between them. Alphabetical and
            // blank-within-group checks still apply within strictly the
            // same group (5 with 5, 6 with 6).
            val areSiblings  = group >= 5 && pg >= 5
            val blankBetween = blankLinesBetween(prevEndLine, imp.startLine, ctx.tokenRows) > 0

            if !areSiblings && group < pg then
              out +=
                Violation
                  ( file, imp.startLine, 1, "c.8.1.2",
                    s"import group $group appears after group $pg" )
            else if !areSiblings && group > pg then
              if !blankBetween then
                out +=
                  Violation
                    ( file, imp.startLine, 1, "c.8.1.3",
                      "import groups must be separated by exactly one blank line" )
            else if group == pg then
              if blankBetween then
                out +=
                  Violation
                    ( file, imp.startLine, 1, "c.8.1.3",
                      "unexpected blank line within an import group" )

              prevName.foreach: pn =>
                if imp.path < pn then
                  out +=
                    Violation
                      ( file, imp.startLine, 1, "c.8.1.2",
                        s"import `${imp.path}` is out of alphabetical order (after `$pn`)" )

          case None => ()

        prevGroup   = Some(group)
        prevName    = Some(imp.path)
        prevEndLine = imp.endLine

      out.toList

    private def blankLinesBetween
      ( prevEnd: Int,
        curStart: Int,
        lines: IndexedSeq[IndexedSeq[Lexeme]] )
    :   Int =

      var count = 0
      var l     = prevEnd + 1

      while l < curStart do
        val idx = l - 1

        if idx >= 0 && idx < lines.length then
          val toks = lines(idx)
          if toks.isEmpty || toks.forall(_.kind == Sort.Space) then count += 1

        l += 1

      count

    private def classifyImport(path: String): Int =
      // For multi-import lines (`import a.*, b.x, c.y`), classify by the first
      // import only — additional comma-separated imports are sub-imports of
      // the same group. A braced wildcard `{… , *}` counts as a wildcard
      // import (group 4), since the `*` selector still pulls in everything.
      // Stop the first-import scan at the first top-level `,` (commas inside
      // `{…}` are part of the same import).
      val firstImport =
        val sb = new StringBuilder
        var depth = 0
        var done  = false

        path.foreach: c =>
          if !done then
            if c == '{' then { depth += 1; sb.append(c) }
            else if c == '}' then { depth -= 1; sb.append(c) }
            else if depth == 0 && c == ',' then done = true
            else sb.append(c)

        sb.toString

      val firstSegment = firstImport.takeWhile: c => c != '.' && c != '{' && c != ' '

      val wildcardImport =
        firstImport.endsWith(".*") || firstImport.endsWith("*}") || firstImport.endsWith("*, *}")

      firstSegment match
        case "language"                          => 1
        case "java" | "javax"                    => 2
        case "scala"                             => 3
        // Compiler / JVM-internals and JEE: dotty (compiler API), `com.sun.*`
        // (Oracle JVM internals), `sun.*` (raw JVM internals), and `jakarta.*`
        // (JEE). These are alias-friendly like the JDK but conceptually
        // distinct from `scala.*`, so they form their own group.
        case "dotty" | "com" | "sun" | "jakarta" => 4

        case _ =>
          if firstSegment.headOption.exists(_.isUpper) then 6 else if !wildcardImport then 6 else 5
