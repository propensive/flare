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

import fulminate.*
import probably.*

object Tests extends Suite(m"Flare Tests"):
  // The entry point fume's legacy run (a separate JVM, used when the suites' event schema is
  // not the one fume was built against) and `java -cp <test jar> flare.Tests` both need: the
  // same plain runner as `flare.runTests`, exiting with the suite's status.
  def main(args: Array[String]): Unit = runTests()

  // The language features the standalone parser must enable, matching the
  // `-language` flags this module is compiled with. Without them the parse
  // differs from the compiler's — `relaxedLambdaSyntax` alone decides whether
  // `f: x => body` is a lambda or a type ascription — and every tree-based
  // rule would quietly test the wrong program. `Phase 0` below guards this.
  val features: List[String] =
    List
      ( "implicitConversions", "experimental.modularity", "experimental.dependent",
        "experimental.relaxedLambdaSyntax", "experimental.genericNumberLiterals",
        "experimental.into", "experimental.erasedDefinitions", "experimental.saferExceptions",
        "experimental.strictEqualityPatternMatching", "experimental.subCases",
        "experimental.multiSpreads" )

  // The licence header the stub below begins every file with: a block comment
  // of 32 lines, as a real header is.
  val header: List[String] = "/*" :: List.fill(30)("x") ::: List("*/")

  val config: Config = Config(language = Some(features), header = Some(header))

  // A project that has a gate, for the ↯/c.9.1 tests. A project that has none
  // declares no gate and ↯/c.9.1 never fires.
  val unsafe: Gate = Gate("Unsafe", "unsafe")
  val gated: Config = config.copy(gates = List(unsafe))

  def gatedRules(body: String): List[String] =
    Checker.check("<test>", Some("flare"), stub(body), config = gated).toList.map(_.rule)

  // A project with rules of its own, for the project-rule and census tests.
  def rule(name: String, predicates: (String, String)*): Definition =
    Definition(name, predicates.to(List).map(Predicate(_, _)))

  val definitions: List[Definition] =
    List
      ( rule("loops", "uses" -> "while"),
        rule("mutables", "uses" -> "var"),
        rule("nulls", "uses" -> "null"),
        rule("throws", "uses" -> "throw"),
        rule("catch-alls", "uses" -> "catch-all"),
        rule("unsafe", "references" -> "unsafe*"),
        rule("casts", "invokes" -> "asInstanceOf"),
        rule("captures", "annotates" -> "untrackedCaptures") )

  val counted: Config = gated.copy(rules = definitions, census = true)

  def census(body: String): Map[String, Int] =
    val parsed = parse(body)

    val ctx =
      Context("<test>", Some("flare"), stub(body), parsed.tree, parsed.source, Nil, Nil,
          Set.empty, counted)

    Metrics.collect(ctx).to(Map)

  // The violations of one project rule, enforced alone.
  def projectRules(definition: Definition, body: String): List[Violation] =
    val enforcing =
      config.copy(rules = List(definition), enforced = Map(definition.name -> None))

    Checker.check("<test>", Some("flare"), stub(body), config = enforcing).toList
      . filter(_.rule == definition.name)

  def hits(body: String, predicates: (String, String)*): Int =
    projectRules(rule("probe", predicates*), body).length

  // A project with an umbrella re-export package, for the ↯/c.8.4 and ↯/c.8.5 tests.
  val umbrella: Config = config.copy(umbrella = Some("umbrella"))

  def stub(body: String): String =
    val builder = new StringBuilder
    builder.append("/*\n")
    var i = 0
    while i < 30 do { builder.append("x\n"); i += 1 }
    builder.append("*/\n")
    builder.append("package flare\n")
    builder.append("\n")
    builder.append(body)
    builder.toString

  def violations(body: String): List[Violation] =
    Checker.check("<test>", Some("flare"), stub(body), config = config).toList

  def rules(body: String): List[String] = violations(body).map(_.rule)

  def exportRules(body: String, siblings: List[String], unexported: Set[String] = Set.empty)
  :   List[String] =

    Checker.check("<test>", Some("umbrella"), stub(body), siblings, Nil, unexported, umbrella)
    . toList.map(_.rule)

  def extensionExportRules
    ( body: String, extensions: List[String], unexported: Set[String] = Set.empty )
  :   List[String] =

    Checker.check("<test>", Some("umbrella"), stub(body), Nil, extensions, unexported, umbrella)
    . toList.map(_.rule)

  def parse(body: String): Parsing.Parsed = Parsing.parse("<test>", stub(body), features)

  val plumbingFixture: String =
    List
      ( "object Tels:",
        "  private inline def kebab(s: String): Text = Text(s)",
        "  private def normalise(text: Text): Text = text.s.replace(\"a\", \"b\").nn.tt",
        "  def monthName(month: Mensual): Text = month.toString.tt",
        "  def toJulianDay(year: Int, day: Int): Int =",
        "    val base = year - 474",
        "    val cycle = base/2820",
        "    val rest = base%2820",
        "    rest*365 + cycle*1029983 + day",
        "  def parseHeader(text: Text): Header = Header(text)",
        "  private def typeKindToFrame(kind: TypeKind): Frame = kind match",
        "    case TypeKind.BOOLEAN => Frame.Z",
        "    case TypeKind.BYTE    => Frame.B",
        "    case TypeKind.VOID    => panic(m\"void\")",
        "  def process(text: Text): Text =",
        "    def bytes(text: Text): Data = Array.unsafeFrozen(text.s.getBytes(UTF_8).nn)",
        "    bytes(text).show",
        "  def withUsing(text: Text)(using Diagnostics): Text = text.s.tt",
        "  given Conversion[Int, Expression] = int => Number(int.toDouble)",
        "  override def toString: String = t\"x\".s",
        "  def tag(out: Scribe[Byte], char: Char): Unit = out.append(char.toByte)",
        "",
        "object Other:",
        "  def tag(sink: Scribe[Byte], c: Char): Unit = sink.append(c.toByte)",
        "" )

    . mkString("\n")

  def extractedExtensions(body: String): List[String] =
    val parsed = parse(body)
    Extensions.extract(parsed.tree, parsed.source)

  def extractedUnexported(body: String): Set[String] = Annotations.unexported(parse(body).tree)

  def anchors(body: String): Anchors.AnchorModel =
    val full   = stub(body)
    val parsed = Parsing.parse("<test>", full, features)
    Anchors.build(parsed.tree, parsed.source, full)

  def anchorKinds(body: String, line: Int): List[Anchors.FrameKind] =
    anchors(body).stackAt(line).map(_.kind)

  def run(): Unit =
    suite(m"Phase 0: The standalone parser is configured"):

      test(m"A well-formed file parses to a non-empty tree"):
        parse("object A:\n  val x = 1\n").failed

      . assert(_ == false)

      test(m"Configuring the parser does not fail"):
        parse("object A:\n  val x = 1\n").setupError

      . assert(_ == None)

      test(m"Relaxed lambda syntax parses as a lambda, not a type ascription"):
        parse("val f = List(1).map: x =>\n  x + 1\n").failed

      . assert(_ == false)

      test(m"An unavailable language feature is reported, not swallowed"):
        Parsing.parse("<test>", "object A\n", List("experimental.notAFeature")).setupError

      . assert(_ != None)

      test(m"An unrecognised plugin option is reported"):
        Config.parse(List("notAnOption=1"))(1)

      . assert(_.length == 1)

      test(m"Plugin options are parsed"):
        val file = java.nio.file.Files.createTempFile("header", ".txt").nn
        java.nio.file.Files.write(file, "/*\n *\n */\n".getBytes("UTF-8").nn)
        Config.parse(List("errors", s"header=$file", "columns=120", "umbrella=lib"))(0)

      . assert: c => c.errors && c.headerLines == 3 && c.columns == 120 && c.packageLine == 4

      test(m"A missing header file is reported"):
        Config.parse(List("header=/nonexistent/header.txt"))(1)

      . assert(_.length == 1)

    suite(m"Phase 1: Universal rules"):

      test(m"Tab character is rejected"):
        rules("def\tfoo(): Int = 1\n")

      . assert(_.contains("c.1.5"))

      test(m"Line longer than 100 columns is rejected"):
        rules("val x = "+"a".repeat(120)+"\n")

      . assert(_.contains("c.1.4"))

      test(m"Odd indent is rejected"):
        rules("object A:\n   val x = 1\n")

      . assert(_.contains("c.1.6"))

      test(m"Trailing whitespace is rejected"):
        rules("val x = 1   \n")

      . assert(_.contains("c.1.7"))

      test(m"Three consecutive blank lines are rejected"):
        rules("val a = 1\n\n\n\nval b = 2\n")

      . assert(_.contains("c.6.3"))

      test(m"Two consecutive blank lines between plain statements are rejected"):
        rules("val a = 1\n\n\nval b = 2\n")

      . assert(_.contains("c.6.3"))

      test(m"One blank line between plain statements is accepted"):
        rules("val a = 1\n\nval b = 2\n")

      . assert: r => !r.contains("c.6.3")

      test(m"Two blank lines before a heavy-signature definition are accepted"):
        rules("val a = 1\n\n\ndef heavy(n: Int)\n:   Int =\n\n  n\n")

      . assert: r => !r.contains("c.6.3")

      test(m"Two blank lines after a heavy-signature body are accepted"):
        rules("def heavy(n: Int)\n:   Int =\n\n  n\n\n\nval a = 1\n")

      . assert: r => !r.contains("c.6.3")

      test(m"Three blank lines around a heavy signature are still rejected"):
        rules("val a = 1\n\n\n\ndef heavy(n: Int)\n:   Int =\n\n  n\n")

      . assert(_.contains("c.6.3"))

      test(m"Clean stub produces no universal-rule diagnostics"):
        rules("val x: Int = 1\n")

      . assert(_.forall { r => !Set("c.1.5", "c.1.4", "c.1.6", "c.1.7", "c.6.3").contains(r) })

    suite(m"Phase 1: License, package, imports"):

      test(m"Wrong package name is rejected"):
        Checker.check("<test>", Some("wrongmodule"), stub("val x = 1\n")).toList.map(_.rule)

      . assert(_.contains("c.1.2"))

      test(m"Missing blank after package is rejected"):
        val builder = new StringBuilder
        builder.append("/*\n")
        var i = 0
        while i < 30 do { builder.append("x\n"); i += 1 }
        builder.append("*/\n")
        builder.append("package flare\n")
        builder.append("import alpha.*\n")
        Checker.check("<test>", Some("flare"), builder.toString, config = config).toList.map(_.rule)

      . assert(_.contains("c.1.3"))

      test(m"Out-of-order import groups are rejected"):
        rules("import alpha.*\n\nimport scala.collection.mutable\n\nval x = 1\n")

      . assert(_.contains("c.8.1.2"))

      test(m"Missing blank between import groups is rejected"):
        rules("import scala.collection.mutable\nimport alpha.*\n\nval x = 1\n")

      . assert(_.contains("c.8.1.3"))

      test(m"Out-of-order imports within a group are rejected"):
        rules("import gamma.*\nimport beta.*\n\nval x = 1\n")

      . assert(_.contains("c.8.1.2"))

      test(m"Missing blank line after imports is rejected"):
        rules("import alpha.*\nval x = 1\n")

      . assert(_.contains("c.6.5"))

    suite(m"Phase 1: Per-token rules"):

      test(m"No space after comma is rejected"):
        rules("def f(a: Int,b: Int): Int = a + b\n")

      . assert(_.contains("c.6.1.2"))

      test(m"Space before comma is rejected"):
        rules("def f(a: Int , b: Int): Int = a + b\n")

      . assert(_.contains("c.6.1.1"))

      test(m"Single line with extra spaces after comma is rejected"):
        rules("val x = (1,  2)\n")

      . assert(_.contains("c.6.1.2"))

      test(m"Genuinely aligned multi-row commas are accepted"):
        // Two rows where the second token after each `,` lands at the
        // same absolute column on both lines — a real alignment run.
        rules
          ("val a = T(1,   t\"H\",  t\"Hydrogen\")\n" + "val b = T(20,  t\"Ne\", t\"Neon\")\n")

      . assert(!_.contains("c.6.1.2"))

      test(m"Fake alignment (extra spaces but cols don't match) is rejected"):
        // `val H` vs `val He` shifts the parens column, so the would-be
        // aligned columns inside don't line up across rows.
        rules
          ("val H = T(1,   t\"H\",  t\"Hydrogen\")\n" + "val He = T(2,   t\"He\", t\"Helium\")\n")

      . assert(_.contains("c.6.1.2"))

      test(m"Space inside parentheses on a single line is rejected"):
        rules("def f( a: Int ): Int = a\n")

      . assert(_.contains("c.5.5"))

      test(m"Block comment outside license header is rejected"):
        rules("/* hello */\nval x = 1\n")

      . assert(_.contains("c.1.8.1"))

      test(m"Scaladoc comment is rejected"):
        rules("/** hello */\nval x = 1\n")

      . assert(_.contains("c.1.8.2"))

      test(m"Annotation followed by blank line is rejected"):
        rules("@deprecated\n\ndef foo(): Int = 1\n")

      . assert(_.contains("c.6.6.2"))

      test(m"Param annotation does not arm 551.2 on a following blank line"):
        rules("case class P(@ident x: Int)\n\ncase class Q(y: Int)\n")

      . assert(!_.contains("c.6.6.2"))

      test(m"Annotation immediately before a decl, then a blank line, is accepted"):
        rules("@foo\ncase class A(x: Int)\n\ncase class B(y: Int)\n")

      . assert(!_.contains("c.6.6.2"))

    suite(m"Phase 2: Operator and method-name spacing"):

      test(m"Missing space before `=>` is rejected"):
        rules("val f = (x: Int)=> x + 1\n")

      . assert(_.contains("c.5.1"))

      test(m"Missing space after `=>` is rejected"):
        rules("val f = (x: Int) =>x + 1\n")

      . assert(_.contains("c.5.1"))

      test(m"Missing space around `<:` is rejected"):
        rules("def f[T<:Int](x: T): T = x\n")

      . assert(_.contains("c.5.1"))

      test(m"Asymmetric `&` spacing is rejected"):
        rules("type T = Int& String\n")

      . assert(_.contains("c.5.1"))

      test(m"Single-char `&` with no spaces is accepted"):
        rules("def f(x: Int): Int = x&255\n")

      . assert(!_.contains("c.5.1"))

      test(m"Spaced `=>` is accepted"):
        rules("val f = (x: Int) => x + 1\n")

      . assert(!_.contains("c.5.1"))

      test(m"`=>` at line start (continuation) is accepted"):
        rules("val f =\n  (x: Int) =>\n    x + 1\n")

      . assert(!_.contains("c.5.1"))

      test(m"Missing space before `=` is rejected"):
        rules("val x= 1\n")

      . assert(_.contains("c.5.2"))

      test(m"Missing space after `=` is rejected"):
        rules("val x =1\n")

      . assert(_.contains("c.5.2"))

      test(m"Two spaces *after* `=` is rejected"):
        rules("val x =  1\n")

      . assert(_.contains("c.5.2"))

      test(m"Two spaces before `=` (alignment) is accepted"):
        // Multi-line param blocks often pad before `=` to align the
        // operators vertically; the rule must allow this.
        rules("val x  = 1\n")

      . assert(!_.contains("c.5.2"))

      test(m"`name_=` setter method declaration is not flagged"):
        rules("def x_=(value: Int): Unit = ()\n")

      . assert(!_.contains("c.5.2"))

      test(m"Single space around `=` is accepted"):
        rules("val x = 1\n")

      . assert(!_.contains("c.5.2"))

      test(m"`=` at line end (multi-line RHS) is accepted"):
        rules("val x =\n  1\n")

      . assert(!_.contains("c.5.2"))

      test(m"Missing space around `+=` is rejected"):
        rules("var x = 0\nx+=1\n")

      . assert(_.contains("c.5.2"))

      test(m"Single space around `+=` is accepted"):
        rules("var x = 0\nx += 1\n")

      . assert(!_.contains("c.5.2"))

      test(m"Default parameter `=` with bad spacing is rejected"):
        rules("def f(x: Int=5): Int = x\n")

      . assert(_.contains("c.5.2"))

      test(m"Symbolic operator method without space before parens is rejected"):
        rules("infix def +(right: Int): Int = right\n")

      . assert(_.contains("c.5.3"))

      test(m"Symbolic operator method with space is accepted"):
        rules("infix def + (right: Int): Int = right\n")

      . assert(!_.contains("c.5.3"))

      test(m"`if/then/else` does not pool predicate ops with else-clause ops"):
        rules("def f(x: Int, y: Int, a: Int, b: Int): Int = if x == y then a else a^b\n")

      . assert(!_.contains("c.5.1"))

      test(m"`else if` chain does not pool ops across branches"):
        rules
          ( "def f(x: Boolean, a: Int, b: Int): Int = " +
            "if x then 1 else if a == b then 2 else a^b\n" )

      . assert(!_.contains("c.5.1"))

      test(m"Parenthesised `if/then/else` followed by an operator is accepted"):
        rules("def f(x: Int, a: Int, b: Int): Int = (if x == 0 then a else b) + 1\n")

      . assert(!_.contains("c.5.1"))

      test(m"`case class` modifier does not flush operator frames spuriously"):
        rules("case class Foo(x: Int, y: Int)\n")

      . assert(!_.contains("c.5.1"))

      test(m"Cross-precedence violation is still rejected"):
        rules("def f(a: Int, b: Int, c: Int): Int = a+b * c\n")

      . assert(_.contains("c.5.1"))

    suite(m"Phase 3: Continuations and shape"):

      test(m"`=>` continuation with wrong space count is rejected"):
        rules("given x: [a]\n=> a is Foo =\n  ???\n")

      . assert(_.contains("c.4.3"))

      test(m"`=>` continuation with two spaces is accepted"):
        rules("given x: [a]\n=>  a is Foo =\n\n  ???\n")

      . assert(!_.contains("c.4.3"))

      test(m"Heavy-signature `:` continuation with wrong spaces is rejected"):
        rules("def foo\n  ( x: Int )\n: Int =\n\n  x\n")

      . assert(_.contains("c.4.3"))

      test(m"Missing blank line before heavy-signature body is rejected"):
        rules("def foo\n  ( x: Int )\n:   Int =\n  x\n")

      . assert(_.contains("c.6.4"))

      test(m"Heavy-signature with blank line before body is accepted"):
        rules("def foo\n  ( x: Int )\n:   Int =\n\n  x\n")

      . assert(!_.contains("c.6.4"))

      test(m"Chain `. method` with same-indent preceded by blank is rejected"):
        rules("val a = foo\n\n. method\n")

      . assert(_.contains("c.4.1.2"))

      test(m"Chain `. method` after deeper line without blank is rejected"):
        rules("val a = foo:\n  bar\n. method\n")

      . assert(_.contains("c.4.1.1"))

    suite(m"Phase 4: Cross-cutting"):

      test(m"Companion object after class is rejected"):
        rules("class Foo:\n  val x = 1\n\nobject Foo:\n  val y = 2\n")

      . assert(_.contains("c.8.3"))

      test(m"Companion object before class is accepted"):
        rules("object Foo:\n  val y = 2\n\nclass Foo:\n  val x = 1\n")

      . assert(!_.contains("c.8.3"))

      test(m"Same-named decl in a different scope is not a companion (398)"):
        rules("enum Hole:\n  case Foo(x: Int)\n\nobject Foo:\n  val y = 2\n")

      . assert(!_.contains("c.8.3"))

    suite(m"Phase 3: Match-case rules"):

      test(m"Misaligned `=>` in case run is rejected"):
        rules
          ( "def f(x: Any): Any = x match\n" +
            "  case Short      => 1\n" +
            "  case LongerName => 2\n" +
            "  case Med => 3\n" )

      . assert(_.contains("c.7.1"))

      test(m"Aligned `=>` in case run is accepted"):
        rules
          ( "def f(x: Any): Any = x match\n" +
            "  case Short      => 1\n" +
            "  case LongerName => 2\n" +
            "  case Medium     => 3\n" )

      . assert(!_.contains("c.7.1"))

      test(m"Multi-line case without preceding blank line is rejected"):
        rules
          ( "def f(x: Any): Any = x match\n" +
            "  case Foo => 1\n" +
            "  case Bar =>\n" +
            "    bigBody\n" )

      . assert(_.contains("c.6.2"))

      test(m"Multi-line case as first case (after `match`) is accepted"):
        rules
          ("def f(x: Any): Any = x match\n" + "  case Bar =>\n" + "    bigBody\n")

      . assert(!_.contains("c.6.2"))

      test(m"Multi-line case after blank is accepted"):
        rules
          ( "def f(x: Any): Any = x match\n" +
            "  case Foo => 1\n" +
            "\n" +
            "  case Bar =>\n" +
            "    bigBody\n" )

      . assert(!_.contains("c.6.2"))

      test(m"Operator-continuation line does not trigger ↯/c.6.2"):
        // Dotty may split `t == 1\n|| t == 2` into two `Block.stats` at
        // the parser level, but inserting a blank line between them
        // would break the operator continuation. The rule must skip
        // when `cur.startLine` begins with an infix operator.
        rules
          ( "def f(c: Int): Boolean =\n" +
            "  val t = c\n" +
            "\n" +
            "  t == 1\n" +
            "  || t == 2\n" +
            "  || t == 3\n" )

      . assert(!_.contains("c.6.2"))

      test(m"Multi-line pattern with trailing `=>` is accepted"):
        rules
          ("def f(x: Any): Any = x match\n" + "  case Foo\n" + "    ( a, b ) =>\n" + "    body\n")

      . assert(!_.contains("c.7.1.1"))

      test(m"Multi-line pattern with `=>` alone on its own line is rejected"):
        rules
          ( "def f(x: Any): Any = x match\n" +
            "  case Foo\n" +
            "    ( a, b )\n" +
            "  =>\n" +
            "    body\n" )

      . assert(_.contains("c.7.1.1"))

      test(m"Multi-line pattern with body on same line as `=>` is rejected"):
        rules
          ("def f(x: Any): Any = x match\n" + "  case Foo\n" + "    ( a, b ) => body\n")

      . assert(_.contains("c.7.1.2"))

    suite(m"Phase 3: Sibling padding and using alignment"):

      test(m"Adjacent multi-line defs without blank line is rejected"):
        rules("def a: Int =\n  1\ndef b: Int =\n  2\n")

      . assert(_.contains("c.6.2"))

      test(m"Adjacent multi-line defs with blank line is accepted"):
        rules("def a: Int =\n  1\n\ndef b: Int =\n  2\n")

      . assert(!_.contains("c.6.2"))

      test(m"First definition in a new indented scope is accepted"):
        rules("class Foo:\n  def a: Int =\n    1\n\n  def b: Int =\n    2\n")

      . assert(!_.contains("c.6.2"))

      test(m"Same-indent decls in unrelated scopes skip sibling check"):
        rules("class A:\n  def a: Int = 1\n\nclass B:\n  def b: Int = 2\n")

      . assert(!_.contains("c.6.2"))

      test(m"Misaligned param after `using` clause first row is rejected"):
        rules("def f\n  ( using a: A,\n        b: B )\n:   Int = 0\n")

      . assert(_.contains("c.7.2"))

      test(m"Aligned `using` clause is accepted"):
        rules("def f\n  ( using a: A,\n          b: B )\n:   Int = 0\n")

      . assert(!_.contains("c.7.2"))

      test(m"Same-line type-annotation `:` is accepted"):
        rules("def foo: Int = 1\n")

      . assert(!_.contains("c.2.3"))

      test(m"Type-annotation `:` aligned with `def` on its own line is accepted"):
        rules("def foo\n:   Int =\n  1\n")

      . assert(!_.contains("c.2.3"))

      test(m"Mis-indented type-annotation `:` on its own line is rejected"):
        rules("def foo\n  : Int =\n    1\n")

      . assert(_.contains("c.2.3"))

      test(m"`:` inside a parameter list is not flagged by 833.3"):
        rules("def f\n  ( a: Int )\n:   Int =\n  a\n")

      . assert(!_.contains("c.2.3"))

      test(m"Mis-indented `:` after multi-line param list with type params is rejected"):
        rules
          ( "def f[a, b]\n  ( x: Int,\n    y: Int )\n        :   Int =\n  0\n" )

      . assert(_.contains("c.2.3"))

      test(m"Mis-indented `:` after param list containing a default value is rejected"):
        rules
          ( "def f\n  ( x: Int = 0,\n    y: Int = 1 )\n        :   Int =\n  0\n" )

      . assert(_.contains("c.2.3"))

    suite(m"Phase 5: Keyword sequences (R33)"):

      test(m"Compact `if/then/else` is accepted"):
        rules("def f(x: Int): Int = if x > 0 then x else -x\n")

      . assert: r => !r.contains("c.2.2.1") && !r.contains("c.2.2.2")

      test(m"`if/then/else` with `else` on a new line is accepted"):
        rules("def f(x: Int): Int =\n  if x > 0 then x\n  else -x\n")

      . assert(!_.contains("c.2.2.1"))

      test(m"`if/then/else` fully split with aligned keywords is accepted"):
        rules("def f(x: Int): Int =\n  if x > 0\n  then x\n  else -x\n")

      . assert: r => !r.contains("c.2.2.1")

      test(m"`if/then/else` with `then` broken but `else` not is rejected"):
        rules("def f(x: Int): Int =\n  if x > 0\n  then x else -x\n")

      . assert(_.contains("c.2.2.1"))

      test(m"`if/then/else` with `then` not aligned with `if` is rejected"):
        rules("def f(x: Int): Int =\n  if x > 0\n      then x\n      else -x\n")

      . assert(_.contains("c.2.2.1"))

      test(m"`if/then/else` with `then` indented, `else` inline is rejected"):
        rules("def f(x: Int): Int =\n  if x > 0 then\n    x\n  else -x\n")

      . assert(_.contains("c.2.2.2"))

      test(m"`if/then/else` with both bodies indented is accepted"):
        rules("def f(x: Int): Int =\n  if x > 0 then\n    x\n  else\n    -x\n")

      . assert: r => !r.contains("c.2.2.1") && !r.contains("c.2.2.2")

      test(m"Compact `else if` chain is accepted"):
        rules("def f(x: Int): Int = if x > 0 then 1 else if x < 0 then -1 else 0\n")

      . assert: r => !r.contains("c.2.2.1") && !r.contains("c.2.2.2")

      test(m"`else if` with broken keywords and inline bodies is accepted"):
        rules
          ( "def f(x: Int): Int =\n" +
            "  if x > 0 then 1\n" +
            "  else if x < 0 then -1\n" +
            "  else 0\n" )

      . assert: r => !r.contains("c.2.2.1") && !r.contains("c.2.2.2")

      test(m"`else if` chain with all bodies indented is accepted"):
        rules
          ( "def f(x: Int): Int =\n" +
            "  if x > 0 then\n" +
            "    1\n" +
            "  else if x < 0 then\n" +
            "    -1\n" +
            "  else\n" +
            "    0\n" )

      . assert: r => !r.contains("c.2.2.1") && !r.contains("c.2.2.2")

      test(m"`else if` chain: indented then-body, inline tail-body rejected"):
        rules
          ( "def f(x: Int): Int =\n" +
            "  if x > 0 then\n" +
            "    1\n" +
            "  else if x < 0 then -1\n" +
            "  else 0\n" )

      . assert(_.contains("c.2.2.2"))

      test(m"`else if` chain misaligned with original `if` is rejected"):
        rules
          ( "def f(x: Int): Int =\n" +
            "  if x > 0 then 1\n" +
            "    else if x < 0 then -1\n" +
            "  else 0\n" )

      . assert(_.contains("c.2.2.1"))

      test(m"`else` followed by indented inner `if` starts a new anchor"):
        // Newline + indentation between `else` and the next `if` means
        // the inner `if` is not a bridge: the outer chain ends at
        // `else`, and the inner `if` starts a fresh chain whose
        // `then`/`else` align with the inner `if`'s column.
        rules
          ( "def f(x: Int): Int =\n" +
            "  if x > 0 then x\n" +
            "  else\n" +
            "    if x < 0 then -x\n" +
            "    else 0\n" )

      . assert: r => !r.contains("c.2.2.1") && !r.contains("c.2.2.2")

      test(m"Inner `if` as new anchor: misaligned inner `then` is rejected"):
        rules
          ( "def f(x: Int): Int =\n" +
            "  if x > 0 then x\n" +
            "  else\n" +
            "    if x < 0\n" +
            "        then -x\n" +
            "    else 0\n" )

      . assert(_.contains("c.2.2.1"))

      test(m"Bridge's internal `then` is part of the unit (not checked)"):
        // The inner `then` of an `else if … then` bridge belongs to
        // the bridge unit. Even though it sits at a column far from
        // the outer `if`'s anchor, the placement rule does not apply
        // to it; only the bridge's leading `else` is checked.
        rules
          ( "def f(x: Int): Int =\n" +
            "  if x > 0\n" +
            "  then 1\n" +
            "  else if x < 0\n" +
            "       then -1\n" +
            "  else 0\n" )

      . assert: r => !r.contains("c.2.2.1")

      test(m"Inner then-only `if` doesn't steal the outer chain's `else`"):
        // The inner `if char >= 0 then ...` (no else) sits inside the
        // outer `then`-body. Without indentation-aware else-matching,
        // findKeyword would absorb the outer `else if esc` as the
        // inner `if`'s else, producing a spurious 833.1 misalignment.
        rules
          ( "def recur(text: String, esc: Boolean): Unit =\n" +
            "  if 1 < text.length\n" +
            "  then\n" +
            "    val char = text.charAt(0)\n" +
            "    if char >= 0 then println(char.toChar)\n" +
            "    recur(text, esc)\n" +
            "  else if esc then throw new RuntimeException(\"\")\n" )

      . assert: r => !r.contains("c.2.2.1")

      test(m"Case-guard `if` in pattern match isn't processed as if-chain"):
        // `case x if guard => …` looks like an `if` with no `then` to
        // findKeyword unless we bail on `=>` at depth 0. Without the
        // bail, the case-guard `if` would walk forward and pair with
        // an unrelated `then` later in the file, producing spurious
        // 833.1 violations against an `if` column nowhere near the
        // actual chain.
        rules
          ( "def f(x: Int): Int = x match\n" +
            "  case n if n > 0 => 1\n" +
            "  case n if n < 0 => -1\n" +
            "  case _          => 0\n" +
            "\n" +
            "def g(y: Int): Int =\n" +
            "  if y > 0 then y\n" +
            "  else -y\n" )

      . assert: r => !r.contains("c.2.2.1")

      test(m"For-comprehension filter `if` isn't processed as if-chain"):
        // `if filter` inside a for-comprehension has no `then`. Without
        // bailing on `yield`/`do`/`<-`, findKeyword would walk past the
        // filter and pair with an unrelated `then` later in the file.
        rules
          ( "def f(xs: List[Int]): List[Int] = for x <- xs if x > 0 yield x\n" +
            "\n" +
            "def g(y: Int): Int =\n" +
            "  if y > 0 then y\n" +
            "  else -y\n" )

      . assert: r => !r.contains("c.2.2.1")

      test(m"Compact `while/do` is accepted"):
        rules("def f(): Unit = while running() do step()\n")

      . assert: r => !r.contains("c.2.2.1")

      test(m"`while/do` with `do` misaligned with `while` is rejected"):
        rules("def f(): Unit =\n  while running()\n      do step()\n")

      . assert(_.contains("c.2.2.1"))

      test(m"Compact `try/catch/finally` is accepted"):
        rules("def f: Int = try compute() catch case e: Throwable => 0 finally cleanup()\n")

      . assert: r => !r.contains("c.2.2.1")

      test(m"`try/catch/finally`: broken `catch`, inline `finally` rejected"):
        rules("def f: Int =\n  try compute()\n  catch case e: Throwable => 0 finally cleanup()\n")

      . assert(_.contains("c.2.2.1"))

      test(m"`try`/`finally` without `catch` is accepted (compact)"):
        rules("def f(): Unit =\n  try block\n  finally cleanup()\n")

      . assert: r => !r.contains("c.2.2.1") && !r.contains("c.2.2.2")

      test(m"`inline if` aligns broken `then`/`else` with `inline`, not `if`"):
        // The `if` after `inline` starts mid-line, but visually the
        // construct begins at `inline`. A broken `then`/`else` aligned
        // with `inline` should be accepted.
        rules
          ("inline def f(x: Int): Int =\n" + "  inline if x > 0\n" + "  then x\n" + "  else -x\n")

      . assert: r => !r.contains("c.2.2.1") && !r.contains("c.2.2.2")

      test(m"`else inline if … then` is recognised as a chain bridge"):
        // The `else` is followed by `inline if` (modifier + `if`) on
        // the same line; this should fold into a single bridge rather
        // than be treated as a final `else` whose body happens to be
        // an `if`.
        rules
          ( "inline def f(x: Int): Int =\n" +
            "  inline if x > 0 then 1\n" +
            "  else inline if x < 0 then -1\n" +
            "  else 0\n" )

      . assert: r => !r.contains("c.2.2.1") && !r.contains("c.2.2.2")

      test(m"Inline inner `else` in bridge doesn't fire 833.1"):
        // The bridge's inner `if/then` brings its own inline `else`;
        // that inner `else` belongs to the bridge, not to the outer
        // chain, so it must not be appended as the chain's K_(i+1).
        rules
          ( "inline def f(x: Int): Int =\n" +
            "  inline if x > 0 then inline if x == 1 then 1 else 2\n" +
            "  else inline if x == -1 then -1 else -2\n" )

      . assert: r => !r.contains("c.2.2.1")

      test(m"`try`/`catch` without `finally` won't steal sibling's `finally`"):
        // The first def's `try`/`catch` has no `finally`; the second
        // def's `try foo finally bar` has its own. Without a dedent
        // bail in `findKeyword`, the first `try` would walk past the
        // function boundary, pair with the second def's `finally`,
        // and produce a 833.2 violation against the inline body of
        // that stolen `finally`.
        rules
          ( "def safely(): Unit =\n" +
            "  try\n" +
            "    val v = compute()\n" +
            "    process(v)\n" +
            "  catch\n" +
            "    case e: Exception => log(e)\n" +
            "    case e: Throwable => panic(e)\n" +
            "\n" +
            "def focus(): Unit =\n" +
            "  try block\n" +
            "  finally cleanup()\n" )

      . assert: r => !r.contains("c.2.2.2")

      test(m"Compact `for/yield` is accepted"):
        rules("def f: List[Int] = for x <- List(1, 2) yield x\n")

      . assert: r => !r.contains("c.2.2.1")

      test(m"Compact `for/do` is accepted"):
        rules("def f(): Unit = for x <- List(1, 2) do println(x)\n")

      . assert: r => !r.contains("c.2.2.1")

    suite(m"Phase 5: For-comprehension generator alignment (R34)"):

      test(m"For-comprehension aligned-LHS style is accepted"):
        rules("val r =\n  for x <- List(1)\n      y <- List(2)\n  yield x + y\n")

      . assert: r => !r.contains("c.7.3.1") && !r.contains("c.7.3.2") && !r.contains("c.7.3.3")

      test(m"For-comprehension indented-block style is accepted"):
        rules("val r =\n  for\n    x <- List(1)\n    y <- List(2)\n  yield x + y\n")

      . assert: r => !r.contains("c.7.3.1") && !r.contains("c.7.3.2") && !r.contains("c.7.3.3")

      test(m"For-comprehension with misaligned generator LHS is rejected"):
        rules("val r =\n  for x <- List(1)\n     y <- List(2)\n  yield x + y\n")

      . assert(_.contains("c.7.3.2"))

      test(m"For-comprehension with misaligned `<-` operators is rejected"):
        rules("val r =\n  for x  <- List(1)\n      y <- List(2)\n  yield x + y\n")

      . assert(_.contains("c.7.3.1"))

      test(m"For-comprehension with `if` filter aligned with `<-` is accepted"):
        rules
          ( "val r =\n  for x  <- List(1)\n         if x > 0\n      y  <- List(2)" +
            "\n  yield x + y\n" )

      . assert: r => !r.contains("c.7.3.3")

      test(m"For-comprehension with `if` filter at LHS column is rejected"):
        rules
          ( "val r =\n  for x  <- List(1)\n      if x > 0\n      y  <- List(2)" +
            "\n  yield x + y\n" )

      . assert(_.contains("c.7.3.3"))

      test(m"For-comprehension with `if` on same line as `<-` is accepted"):
        rules("val r =\n  for x <- List(1) if x > 0\n  yield x\n")

      . assert: r => !r.contains("c.7.3.3") && !r.contains("c.7.3.4")

      test(m"For-comprehension with blank line before `if` is rejected"):
        rules
          ( "val r =\n  for x  <- List(1)\n\n         if x > 0\n      y  <- List(2)" +
            "\n  yield x + y\n" )

      . assert(_.contains("c.7.3.4"))

      test(m"For-comprehension aligned `if` filter is exempt from 473.1"):
        rules
          ( "val r =\n  for x  <- List(1)\n         if x > 0\n      y  <- List(2)" +
            "\n  yield x + y\n" )

      . assert: r => !r.contains("c.2.1")

      test(m"Inline term quote with inside space is rejected"):
        rules("val q = '{ x }\n")

      . assert(_.contains("c.2.9.6"))

      test(m"Inline term splice with inside space is rejected"):
        rules("val s = ${ x }\n")

      . assert(_.contains("c.2.9.6"))

      test(m"Inline type quote with inside space is rejected"):
        rules("val t = '[ Int ]\n")

      . assert(_.contains("c.2.9.6"))

      test(m"Tight inline quote is accepted"):
        rules("val q = '{x}\n")

      . assert: r => !r.contains("c.2.9.6")

      test(m"Tight inline splice is accepted"):
        rules("val s = ${x}\n")

      . assert: r => !r.contains("c.2.9.6")

      test(m"Tight inline type quote is accepted"):
        rules("val t = '[Int]\n")

      . assert: r => !r.contains("c.2.9.6")

      test(m"Multi-line quote opener is not flagged as inline"):
        rules("val q =\n  ' {\n    body\n  }\n")

      . assert: r => !r.contains("c.2.9.6")

    suite(m"Phase 6: Indented-scope body indent (Rule B)"):

      test(m"Colon-block body at anchor+2 is accepted"):
        rules("val foo: Int = bar:\n  baz\n")

      . assert: r => !r.contains("c.2.5")

      test(m"Colon-lambda body at anchor+2 is accepted"):
        rules("val xs = items.map: x =>\n  f(x)\n")

      . assert: r => !r.contains("c.2.5")

      test(m"Nested match on a case line is accepted"):
        rules("foo match\n  case Bar(b) => b match\n    case Q => q\n")

      . assert: r => !r.contains("c.2.5")

      test(m"Tuple as = RHS is accepted"):
        rules("val pair: (Int, Int) =\n  (a, b)\n")

      . assert: r => !r.contains("c.2.5")

      test(m"= RHS receiver on its own line with arg block is accepted"):
        rules("val foo: Bar =\n  Bar\n    ( baz, quux )\n")

      . assert: r => !r.contains("c.2.5")

      test(m"Colon-block body too deep is rejected"):
        rules("val foo: Int = bar:\n      baz\n")

      . assert(_.contains("c.2.5"))

      test(m"Multi-line signature with = not last is rejected"):
        rules("def fn(a: A)\n: Quux = quux:\n  body\n")

      . assert(_.contains("c.2.6"))

      test(m"Multi-line signature with = last is accepted"):
        rules("def fn(a: A)\n: Quux =\n  quux\n")

      . assert: r => !r.contains("c.2.6")

      test(m"Param block under a val is owned by 833.4, not Rule B"):
        rules("val foo: Bar = Bar\n  ( baz, quux )\n")

      . assert: r => !r.contains("c.2.5")

      test(m"Lambda parameter list is not a heavy continuation (833.4)"):
        rules("foo +\n  (data: Origin) =>\n    body\n")

      . assert: r => !r.contains("c.2.4")

      test(m"Genuine heavy bracket continuation is still rejected (833.4)"):
        rules("foo bar\n  ( baz, quux )\n")

      . assert: r => r.contains("c.2.4")

      test(m"Heavy bracket after a symbolic operator defers to 616, not 833.4"):
        rules("foo +\n  (baz, quux)\n")

      . assert: r => !r.contains("c.2.4")

      test(m"Tuple as the first line inside a quote/block is not 833.4"):
        rules("' {\n  ( baz, quux )\n")

      . assert: r => !r.contains("c.2.4")

      test(m"Constructor params after an `into` class are not 833.4"):
        rules("into case class Response private\n  ( a: A, b: B )\n")

      . assert: r => !r.contains("c.2.4")

      test(m"if-sequence as RHS keeps its own anchor (833.1), not Rule B"):
        rules("val x = if pred\nthen y\nelse z\n")

      . assert: r => r.contains("c.2.2.1") && !r.contains("c.2.5")

      test(m"Chain-method colon-lambda body (+4) is not flagged"):
        rules("foo\n  .map: x =>\n    body\n")

      . assert: r => !r.contains("c.2.5")

      test(m"String-interpolator first body line is handled"):
        rules("val x = bar:\n  t\"a\"+b\n  more\n")

      . assert: r => !r.contains("c.2.5")

    suite(m"Phase 5: Infix-operator continuation"):

      test(m"Single-line infix is accepted"):
        rules("val x = alpha ++ beta\n")

      . assert: r => !r.contains("c.4.2.1") && !r.contains("c.4.2.2")

      test(m"Operator at end of line with +2 continuation is accepted"):
        rules("val x = alpha ++\n  beta\n")

      . assert: r => !r.contains("c.4.2.1") && !r.contains("c.4.2.2")

      test(m"Flat +2 operator chain is accepted"):
        rules("val x = a ++\n  b ++\n  c\n")

      . assert: r => !r.contains("c.4.2.1") && !r.contains("c.4.2.2")

      test(m"Colon-lambda inside an unclosed paren is never a 247 site"):
        rules("val e =\n  f\n    ( g.let: p =>\n        h(p)\n\n      . or(k) )\n")

      . assert: r => !r.contains("c.3.1")

      test(m"Trailing operator abutting its left operand is rejected"):
        rules("val x = alpha ++\n  beta\nval y = gamma++\n  delta\n")

      . assert(_.contains("c.4.2.3"))

      test(m"Trailing operator with two spaces before it is rejected"):
        rules("val x = alpha  ++\n  beta\n")

      . assert(_.contains("c.4.2.3"))

      test(m"Trailing operator with exactly one space is accepted"):
        rules("val x = alpha ++\n  beta\n")

      . assert: r => !r.contains("c.4.2.3")

      test(m"Tight infix stays legal when it fits on one line"):
        rules("val x = alpha++beta\n")

      . assert: r => !r.contains("c.4.2.3")

      test(m"Operator beginning the continuation line is rejected"):
        rules("val x =\n  alpha\n  ++ beta\n")

      . assert(_.contains("c.4.2.1"))

      test(m"Operator alone on its own line is rejected"):
        rules("val x =\n  alpha\n  ++\n  beta\n")

      . assert(_.contains("c.4.2.1"))

      test(m"Continuation not indented +2 is rejected"):
        rules("val x =\n  alpha ++\n  beta\n")

      . assert(_.contains("c.4.2.2"))

      test(m"Symbolic infix type operator follows the same rule"):
        rules("type T =\n  Foo\n  | Bar\n")

      . assert(_.contains("c.4.2.1"))

      test(m"Correctly-laid symbolic infix type is accepted"):
        rules("type T =\n  Foo |\n    Bar\n")

      . assert: r => !r.contains("c.4.2.1") && !r.contains("c.4.2.2")

      test(m"Word-named infix operator is exempt"):
        rules("val b =\n  alpha max\n  beta\n")

      . assert: r => !r.contains("c.4.2.1") && !r.contains("c.4.2.2")

      test(m"Pattern alternative is exempt"):
        rules("def f(x: Any) = x match\n  case A\n  | B => 1\n")

      . assert: r => !r.contains("c.4.2.1") && !r.contains("c.4.2.2")

      test(m"Method-selection chain is exempt"):
        rules("val x =\n  foo\n  . bar\n")

      . assert: r => !r.contains("c.4.2.1") && !r.contains("c.4.2.2")

      test(m"Operator-continuation expression does not trigger 315"):
        rules("def f =\n  val a = 1\n  a == 1 ||\n    a == 2\n")

      . assert: r => !r.contains("c.6.2")

      test(m"Trailing operator after a quote-block `}` defers to 616, not 473.6"):
        rules("val x =\n  ' {\n      foo\n    } ::\n    tail\n")

      . assert: r => !r.contains("c.2.9.5")

    suite(m"Phase 5: Multi-line interpolated strings"):

      test(m"Canonical multi-line m-string is accepted"):
        rules("def message =\n  m\"\"\"\n    line one\n    line two\n  \"\"\"\n")

      . assert: r => !r.exists(_.startsWith("c.2.8"))

      test(m"Heavy-block `( m\"\"\" )` argument form is accepted"):
        rules("val x =\n  ( m\"\"\"\n      content here\n    \"\"\" )\n")

      . assert: r => !r.exists(_.startsWith("c.2.8"))

      test(m"Content on the opener line is rejected (560.1)"):
        rules("def msg =\n  m\"\"\"the content\n    more\n  \"\"\"\n")

      . assert(_.contains("c.2.8.1"))

      test(m"Content not indented +2 is rejected (560.2)"):
        rules("def msg =\n  m\"\"\"\n   content\n  \"\"\"\n")

      . assert(_.contains("c.2.8.2"))

      test(m"Under-indented content line is rejected (560.3)"):
        rules("def msg =\n  m\"\"\"\n    first line\n  second\n  \"\"\"\n")

      . assert(_.contains("c.2.8.3"))

      test(m"Closer not aligned with opener is rejected (560.4)"):
        rules("def msg =\n  m\"\"\"\n    content\n    \"\"\"\n")

      . assert(_.contains("c.2.8.4"))

      test(m"Other interpolators' multi-line content is exempt from 560"):
        rules("def t1 =\n  t\"\"\"\nunindented significant content\n  \"\"\"\n")

      . assert: r => !r.exists(_.startsWith("c.2.8"))

      test(m"Long content line of a multi-line string is not flagged 230"):
        rules("def msg =\n  m\"\"\"\n    "+"x".repeat(110)+"\n  \"\"\"\n")

      . assert: r => !r.contains("c.1.4")

    suite(m"Phase 7: Umbrella export completeness (↯/c.8.4)"):

      test(m"Module not re-exported to the umbrella is rejected (↯/c.8.4)"):
        exportRules("export delta.{Json}\n", List("Json", "NumberMode"))

      . assert(_.contains("c.8.4"))

      test(m"All modules re-exported is accepted"):
        exportRules("export delta.{Json, NumberMode}\n", List("Json", "NumberMode"))

      . assert: r => !r.contains("c.8.4")

      test(m"Module exported inside a nested package counts as exported"):
        exportRules
          ( "package formatting:\n  export delta.formatting.NumberMode\n", List("NumberMode") )

      . assert: r => !r.contains("c.8.4")

      test(m"Single-selector export without braces is recognised"):
        exportRules("export delta.Json\n", List("Json"))

      . assert: r => !r.contains("c.8.4")

      test(m"No violation when there are no sibling modules"):
        exportRules("export delta.{Json}\n", Nil)

      . assert: r => !r.contains("c.8.4")

      test(m"An `@unexported` module suppresses the violation"):
        exportRules("export embarcadero.{Image}\n", List("Image", "Timestamp"), Set("Timestamp"))

      . assert: r => !r.contains("c.8.4")

      test(m"An `@unexported` module only suppresses the named module"):
        exportRules
          ( "export embarcadero.{Image}\n", List("Image", "Timestamp", "Layer"), Set("Timestamp") )

      . assert(_.contains("c.8.4"))

    suite(m"Phase 7: Umbrella extension-method export completeness (↯/c.8.5)"):

      test(m"Extension method not re-exported to the umbrella is rejected (↯/c.8.5)"):
        extensionExportRules("export epsilon.{x, xp}\n", List("x", "xp", "xml"))

      . assert(_.contains("c.8.5"))

      test(m"All extension methods re-exported is accepted"):
        extensionExportRules("export epsilon.{x, xp, xml}\n", List("x", "xp", "xml"))

      . assert: r => !r.contains("c.8.5")

      test(m"No violation when there are no sibling extension methods"):
        extensionExportRules("export epsilon.{x, xp}\n", Nil)

      . assert: r => !r.contains("c.8.5")

      test(m"An `@unexported` extension method suppresses the extension violation"):
        extensionExportRules("export epsilon.{x}\n", List("x", "xml"), Set("xml"))

      . assert: r => !r.contains("c.8.5")

      test(m"Extract names a single-line extension method"):
        extractedExtensions("extension (value: Text) def xml: Xml = ???\n")

      . assert(_ == List("xml"))

      test(m"Extract names every method in a multi-def extension block"):
        extractedExtensions("extension (value: Text)\n  def a: Int = 1\n  def b: Int = 2\n")

      . assert(_ == List("a", "b"))

      test(m"Extract names a type-parameterised, context-bound extension"):
        extractedExtensions("extension [t: Encodable in Xml](value: t)\n  def xml: Xml = ???\n")

      . assert(_ == List("xml"))

      test(m"Extract skips private extension methods"):
        extractedExtensions("extension (value: Text)\n  def a: Int = 1\n  private def b: Int = 2\n")

      . assert(_ == List("a"))

      test(m"Extract skips qualified-private extension methods"):
        extractedExtensions
          ( "extension (value: Text)\n  def a: Int = 1\n  private[flare] def b = 2\n" )

      . assert(_ == List("a"))

    suite(m"Phase 7: @unexported annotation extraction"):

      test(m"@unexported on an object is detected"):
        extractedUnexported("@unexported\nobject Timestamp\n")

      . assert(_ == Set("Timestamp"))

      test(m"@unexported on a trait is detected"):
        extractedUnexported("@unexported\ntrait Executor\n")

      . assert(_ == Set("Executor"))

      test(m"@unexported on an extension method is detected"):
        extractedUnexported("extension (x: Int)\n  @unexported def apply(y: Int): Int = y\n")

      . assert(_ == Set("apply"))

      test(m"unannotated definitions are not collected"):
        extractedUnexported("object Foo\nextension (x: Int) def bar: Int = x\n")

      . assert(_ == Set())

    suite(m"Phase 8: Anchor model"):

      test(m"Def with indented rhs yields a DefnRhs frame"):
        anchors("def foo(): Int =\n  1 + 1\n").innermostAt(36)

      . assert(_ == Some(Anchors.Frame(Anchors.FrameKind.DefnRhs, 35, 1, 3, 35, 36)))

      test(m"Single-line def yields no frame"):
        anchors("def foo(): Int = 1\n").frames

      . assert(_ == Nil)

      test(m"Class body yields a ColonBlock frame"):
        anchors("class Foo:\n  val x: Int = 1\n").innermostAt(36)

      . assert(_ == Some(Anchors.Frame(Anchors.FrameKind.ColonBlock, 35, 1, 3, 35, 36)))

      test(m"Nested class and method bodies nest correctly"):
        anchorKinds("class Foo:\n  def bar(): Int =\n    42\n", 37)

      . assert(_ == List(Anchors.FrameKind.ColonBlock, Anchors.FrameKind.DefnRhs))

      test(m"Method frame inside a class covers only the method body"):
        anchorKinds("class Foo:\n  def bar(): Int =\n    42\n", 36)

      . assert(_ == List(Anchors.FrameKind.ColonBlock))

      test(m"Match inside a def stacks a MatchBody over the DefnRhs"):
        val src = "def foo(x: Int): Int =\n  x match\n    case 1 => 0\n    case _ => 1\n"
        anchorKinds(src, 37)

      . assert(_ == List(Anchors.FrameKind.DefnRhs, Anchors.FrameKind.MatchBody))

      test(m"Match frame is anchored on the match keyword's line"):
        val src = "def foo(x: Int): Int =\n  x match\n    case 1 => 0\n"
        anchors(src).innermostAt(37)

      . assert(_ == Some(Anchors.Frame(Anchors.FrameKind.MatchBody, 36, 3, 5, 36, 37)))

      test(m"Case body on a later line yields a LambdaBody frame"):
        val src = "val r = 1 match\n  case 1 =>\n    2\n"
        anchorKinds(src, 37)

      . assert(_ == List(Anchors.FrameKind.MatchBody, Anchors.FrameKind.LambdaBody))

      test(m"Lambda body under a colon-arg application is framed"):
        val src = "def foo(xs: List[Int]): List[Int] =\n  xs.map: x =>\n    x + 1\n"
        anchors(src).innermostAt(37)

      . assert(_ == Some(Anchors.Frame(Anchors.FrameKind.LambdaBody, 36, 3, 5, 36, 37)))

      test(m"Colon-arg lambda stacks over the enclosing DefnRhs"):
        val src = "def foo(xs: List[Int]): List[Int] =\n  xs.map: x =>\n    x + 1\n"
        anchorKinds(src, 37)

      . assert(_ == List(Anchors.FrameKind.DefnRhs, Anchors.FrameKind.LambdaBody))

      test(m"Colon-block application yields a ColonBlock frame"):
        anchors("def foo(): Int =\n  locally:\n    42\n").innermostAt(37)

      . assert(_ == Some(Anchors.Frame(Anchors.FrameKind.ColonBlock, 36, 3, 5, 36, 37)))

      test(m"Multi-line quote yields a QuoteSplice frame"):
        val src = "def foo(): Int =\n  ' {\n    42\n  }\n"
        anchors(src).innermostAt(37)

      . assert(_ == Some(Anchors.Frame(Anchors.FrameKind.QuoteSplice, 36, 3, 5, 36, 38)))

      test(m"Sibling defs close their scopes at the next sibling"):
        val src = "def foo(): Int =\n  1\n\ndef bar(): Int =\n  2\n"
        anchors(src).stackAt(38)

      . assert(_ == Nil)

      test(m"Second sibling def opens its own frame"):
        val src = "def foo(): Int =\n  1\n\ndef bar(): Int =\n  2\n"
        anchors(src).innermostAt(39)

      . assert(_ == Some(Anchors.Frame(Anchors.FrameKind.DefnRhs, 38, 1, 3, 38, 39)))

      test(m"Blank line inside a scope keeps the frame open"):
        val src = "def foo(): Int =\n  val x = 1\n\n  x\n"
        anchors(src).stackAt(37).map(_.kind)

      . assert(_ == List(Anchors.FrameKind.DefnRhs))

      test(m"Anchor extends leftward to the val keyword's column"):
        val src = "object A:\n  val foo: Int = bar:\n    compute()\n"
        anchors(src).innermostAt(37)

      . assert(_ == Some(Anchors.Frame(Anchors.FrameKind.ColonBlock, 36, 3, 5, 36, 37)))

      test(m"Chain opener yields a ChainCall frame"):
        val src = "def foo(x: List[Int]): List[Int] =\n  x\n  . map: y =>\n      y + 1\n"
        anchorKinds(src, 38)

      . assert(_ == List(Anchors.FrameKind.DefnRhs, Anchors.FrameKind.ChainCall))

    suite(m"Necessity (↯/c.3.1)"):

      test(m"Broken if/then/else that fits on one line is rejected"):
        rules("def f(a: Boolean): Int =\n  if a\n  then 1\n  else 2\n")

      . assert(_.contains("c.3.1"))

      test(m"247 message names the construct kind and width"):
        violations("def f(a: Boolean): Int =\n  if a\n  then 1\n  else 2\n")
          .filter(_.rule == "c.3.1").map(_.message)

      . assert(_.exists { m => m.contains("if-expression") && m.contains("columns") })

      test(m"Broken if that genuinely does not fit is accepted"):
        rules
          ( "def f(aLongConditionValue: Boolean): Int =\n" +
            "  if aLongConditionValue\n" +
            "  then firstAlternativeValue + secondAlternativeValueWithLongName\n" +
            "  else thirdAlternativeValue + fourthAlternativeValueWithLongName\n" )

      . assert: r => !r.contains("c.3.1")

      test(m"Multi-line infix chain that fits on one line is rejected"):
        rules("val x = 1 +\n  2\n")

      . assert(_.contains("c.3.1"))

      test(m"Multi-line infix chain that does not fit is accepted"):
        rules
          ( "val veryLongValueName = firstOperandWithAnExtremelyLongName +\n" +
            "  secondOperandWithAnEvenLongerNameThatOverflowsTheLimit\n" )

      . assert: r => !r.contains("c.3.1")

      test(m"Brace lambda with its body on a later line is rejected"):
        rules("val y = List(1).map { x =>\n  x + 1 }\n")

      . assert(_.contains("c.3.1"))

      test(m"Colon-arg lambda with a short body on a later line is rejected"):
        rules("def f(xs: List[Int]): List[Int] =\n  xs.map: x =>\n    x + 1\n")

      . assert(_.contains("c.3.1"))

      test(m"Broken if containing a comment is accepted"):
        rules("def f(a: Boolean): Int =\n  if a // choose\n  then 1\n  else 2\n")

      . assert: r => !r.contains("c.3.1")

      test(m"Broken if containing a colon-block line is accepted"):
        rules("def f(a: Boolean): Int =\n  if a then bar:\n    1\n  else 2\n")

      . assert: r => !r.contains("c.3.1")

      test(m"Chain broken around a blank interior line is accepted"):
        rules("val x = 1 +\n\n  2\n")

      . assert: r => !r.contains("c.3.1")

      test(m"Chain containing a multi-line string is accepted"):
        rules("val s = a +\n  \"\"\"line1\nline2\"\"\"\n")

      . assert: r => !r.contains("c.3.1")

      test(m"If with a multi-statement then-block is accepted"):
        rules("def f(a: Boolean): Int =\n  if a then\n    val b = 1\n    b + 1\n  else 2\n")

      . assert: r => !r.contains("c.3.1")

      test(m"Broken while that fits on one line is rejected"):
        rules("def f(): Unit =\n  while cond\n  do work()\n")

      . assert(_.contains("c.3.1"))

      test(m"For with single-line generators and broken yield is rejected"):
        rules("val x =\n  for i <- List(1)\n  yield i\n")

      . assert(_.contains("c.3.1"))

      test(m"For whose generators span several lines is accepted"):
        rules("val x =\n  for\n    i <- List(1)\n    j <- List(2)\n  yield i\n")

      . assert: r => !r.contains("c.3.1")

      test(m"Broken try/finally that fits on one line is rejected"):
        rules("def f(): Int =\n  try compute()\n  finally cleanup()\n")

      . assert(_.contains("c.3.1"))

      test(m"Broken try with a catch case is accepted"):
        rules("def f(): Int =\n  try compute()\n  catch case e: Exception => 0\n")

      . assert: r => !r.contains("c.3.1")

    suite(m"↯/c.9.1: unsafe naming"):
      test(m"A gated method with the prefix is accepted"):
        gatedRules("def unsafeRead(n: Int)(using erased Unsafe): Int = n\n")

      . assert: r => !r.exists(_.startsWith("c.9.1"))

      test(m"A gated method without the prefix is rejected"):
        gatedRules("def raw(using erased Unsafe): Int = 1\n")

      . assert(_.contains("c.9.1.1"))

      test(m"A prefixed method with no token is rejected"):
        gatedRules("def unsafeRead(n: Int): Int = n\n")

      . assert(_.contains("c.9.1.2"))

      test(m"An ungated, unprefixed method is accepted"):
        gatedRules("def read(n: Int): Int = n\n")

      . assert: r => !r.exists(_.startsWith("c.9.1"))

      test(m"A named using parameter is a gate"):
        gatedRules("def raw(using unsafe: Unsafe): Int = 1\n")

      . assert(_.contains("c.9.1.1"))

      test(m"A qualified token is a gate"):
        gatedRules("def raw(using erased vacuous.Unsafe): Int = 1\n")

      . assert(_.contains("c.9.1.1"))

      test(m"A non-using parameter of token type is not a gate"):
        gatedRules("def unsafeRead(token: Unsafe): Int = 1\n")

      . assert(_.contains("c.9.1.2"))

      test(m"A method named exactly `unsafe` must take the token"):
        gatedRules("def unsafe(n: Int): Int = n\n")

      . assert(_.contains("c.9.1.2"))

      test(m"unsafely is exempt from the prefix rule"):
        gatedRules("def unsafely[result](block: () => result): result = block()\n")

      . assert: r => !r.exists(_.startsWith("c.9.1"))

      test(m"An extension method is checked"):
        gatedRules("extension (n: Int)\n  def raw(using erased Unsafe): Int = n\n")

      . assert(_.contains("c.9.1.1"))

      test(m"A gated apply is rejected, having no name to prefix"):
        gatedRules("object A:\n  def apply(n: Int)(using erased Unsafe): Int = n\n")

      . assert(_.contains("c.9.1.1"))

      test(m"A gated given is exempt, being summoned by type"):
        gatedRules("given reader: (Unsafe ?=> Int) = 1\n")

      . assert: r => !r.exists(_.startsWith("c.9.1"))

      test(m"A constructor taking the token is exempt"):
        gatedRules("class Wrapper(n: Int)(using erased Unsafe)\n")

      . assert: r => !r.exists(_.startsWith("c.9.1"))

      test(m"The rule does not fire when no token is configured"):
        rules("def raw(using erased Unsafe): Int = 1\n")

      . assert: r => !r.exists(_.startsWith("c.9.1"))

      test(m"The rule does not fire on a prefixed name without a token"):
        rules("def unsafeRead(n: Int): Int = n\n")

      . assert: r => !r.exists(_.startsWith("c.9.1"))

    suite(m"Metrics census"):
      test(m"While loops are counted"):
        census("def f(): Unit =\n  while cond do\n    work()\n").get("loops")

      . assert(_ == Some(1))

      test(m"Mutable definitions are counted"):
        census("object A:\n  var x = 1\n  var y = 2\n  val z = 3\n").get("mutables")

      . assert(_ == Some(2))

      test(m"A val is not counted as a var"):
        census("object A:\n  val z = 3\n").get("mutables")

      . assert(_ == None)

      test(m"Null literals are counted"):
        census("object A:\n  val x = null\n").get("nulls")

      . assert(_ == Some(1))

      test(m"Throws are counted"):
        census("def f(): Int = throw Error()\n").get("throws")

      . assert(_ == Some(1))

      test(m"A catch-all clause is counted"):
        census("def f(): Int =\n  try work() catch case _: Throwable => 0\n").get("catch-alls")

      . assert(_ == Some(1))

      test(m"A specific catch clause is not a catch-all"):
        census("def f(): Int =\n  try work() catch case _: NumberFormatException => 0\n")
        . get("catch-alls")

      . assert(_ == None)

      test(m"A globbed reference is counted under its rule"):
        census("def f(): Int = caps.unsafe.unsafeAssumePure(x)\n").get("unsafe")

      . assert(_ == Some(2))

      test(m"An unsafeNulls language import is counted"):
        census("import scala.language.unsafeNulls\n").get("unsafe")

      . assert(_ == Some(1))

      test(m"Invocations are counted"):
        census("def f(): Int = x.asInstanceOf[Int] + y.asInstanceOf[Int]\n").get("casts")

      . assert(_ == Some(2))

      test(m"Names no rule watches are not counted"):
        census("def f(): Int = x.head\n").get("head")

      . assert(_ == None)

      test(m"An annotation in a definition's modifiers is counted"):
        census("object A:\n  @caps.unsafe.untrackedCaptures private var x = 1\n")
        . get("captures")

      . assert(_ == Some(1))

      test(m"Gated definitions are counted"):
        census("def unsafeRead(using erased Unsafe): Int = 1\n").get("unsafe-gate")

      . assert(_ == Some(1))

      test(m"A prefixed definition with no gate is counted"):
        census("object A:\n  def unsafeFrozen(n: Int): Int = n\n").get("unsafe-ungated")

      . assert(_ == Some(1))

      test(m"An `unsafely` block is not an unbacked claim"):
        census("def unsafely[result](block: () => result): result = block()\n")
        . get("unsafe-ungated")

      . assert(_ == None)

      test(m"A gated definition is not counted as ungated"):
        census("def unsafeRead(using erased Unsafe): Int = 1\n").get("unsafe-ungated")

      . assert(_ == None)

      test(m"An indicator that does not occur is absent"):
        census("object A:\n  val z = 3\n").get("loops")

      . assert(_ == None)

      test(m"A rule is counted whether or not it is enforced"):
        census("def f(): Unit =\n  while cond do\n    work()\n").contains("loops")

      . assert(_ == true)

    suite(m"Config option parsing"):
      test(m"A semicolon-separated list parses, the comma being unusable via -P"):
        Config.parse(List("omit=c.8.4;c.9"))(0).omitted

      . assert(_ == Set("c.8.4", "c.9"))

      test(m"A repeated strict option accumulates"):
        Config.parse(List("strict=c.9.1", "strict=↯/c.8.4"))(0).strict

      . assert(_ == Set("c.9.1", "↯/c.8.4"))

      test(m"An option error is labelled with its code"):
        Config.parse(List("nonsense=1"))(1).map(_.takeWhile(_ != ' '))

      . assert(_ == List("[↯/f.21.3.6]"))

      test(m"An entry in the letter form is reported with the code it now is"):
        Config.parse(List("omit=L4", "strict=S;A2.1"))(1)

      . assert:
          _ == List
            ( "[↯/f.21.3.9] `A2.1` is now `c.2.2.1`; name a rule by its code",
              "[↯/f.21.3.9] `L4` is now `c.8.4`; name a rule by its code",
              "[↯/f.21.3.9] `S` is now `c.9`; name a rule by its code" )

      test(m"An entry in the letter form which names a project rule is not reported"):
        Config.parse(List("rule=L4:invokes:asInstanceOf", "omit=L4"))(1)

      . assert(_ == Nil)

      test(m"A letter identifier migrates to its code"):
        List("F4", "A2.1", "T1.2", "S", "cast", "c.1.4").map(Rules.migrated)

      . assert(_ == List(Some("c.1.4"), Some("c.2.2.1"), Some("c.7.1.2"), Some("c.9"), None, None))

      test(m"A gate parses with its prefix"):
        Config.parse(List("gate=vacuous.Unsafe:unsafe"))(0).gates

      . assert(_ == List(Gate("vacuous.Unsafe", "unsafe")))

      test(m"A gate's prefix defaults to its lower-cased token"):
        Config.parse(List("gate=Privileged"))(0).gates

      . assert(_ == List(Gate("Privileged", "privileged")))

      test(m"A rule's predicates accumulate across options"):
        Config.parse(List("rule=cast:invokes:asInstanceOf", "rule=cast:invokes:isInstanceOf",
            "rule=cast:severity:error", "rule=cast:message:no casts"))(0).definition("cast")

      . assert: definition =>
          val predicates =
            List(Predicate("invokes", "asInstanceOf"), Predicate("invokes", "isInstanceOf"))

          definition == Some(Definition("cast", predicates, Some("no casts"), "error"))

      test(m"A target may contain a colon"):
        Config.parse(List("rule=todo:matches:TODO:.*"))(0).definition("todo").map(_.predicates)

      . assert(_ == Some(List(Predicate("matches", "TODO:.*"))))

      test(m"An unknown predicate is reported"):
        Config.parse(List("rule=cast:summons:Unsafe"))(1)

      . assert(_.length == 1)

      test(m"Enforcing an undefined rule is reported"):
        Config.parse(List("enforce=cast"))(1)

      . assert(_.length == 1)

      test(m"An enforcement may carry a severity"):
        Config.parse(List("rule=cast:invokes:asInstanceOf", "enforce=cast:error"))(0).enforced

      . assert(_ == Map("cast" -> Some("error")))

      test(m"An unrecognised option is reported"):
        Config.parse(List("nonsense=1"))(1)

      . assert(_.length == 1)

      test(m"A strict entry selects a rule and its variants but not a longer code"):
        List("c.9.1", "c.9.1.2", "c.9.12").map(Config.selects("c.9.1", _))

      . assert(_ == List(true, true, false))

      test(m"A principle's code selects every rule of the principle"):
        (Config.selects("c.9", "c.9.1.1"), Config.selects("c.9", "c.91.1"))

      . assert(_ == (true, false))

      test(m"An entry may begin with ↯/"):
        (Config.selects("↯/c.8.4", "c.8.4"), Config.selects("↯/c.8", "c.8.4"))

      . assert(_ == (true, true))

      test(m"A letter identifier selects nothing"):
        (Config.selects("S1", "c.9.1"), Config.selects("S", "c.9.1.1"))

      . assert(_ == (false, false))

      test(m"A project rule is selected by its exact name"):
        (Config.selects("cast", "cast"), Config.selects("cast", "casting"))

      . assert(_ == (true, false))

    suite(m"Project rules"):
      test(m"invokes matches a selection and an application"):
        hits
          ( "def f(): Int = x.asInstanceOf[Int] + g(1)\n",
            "invokes" -> "asInstanceOf",
            "invokes" -> "g" )

      . assert(_ == 2)

      test(m"invokes matches an infix operator"):
        hits("def f(): Int = a max b\n", "invokes" -> "max")

      . assert(_ == 1)

      test(m"invokes does not match a definition of the name"):
        hits("def g(n: Int): Int = n\n", "invokes" -> "g")

      . assert(_ == 0)

      test(m"references matches a type position too"):
        hits("val x: Unsafe = y\n", "references" -> "Unsafe")

      . assert(_ == 1)

      test(m"annotates matches a definition's annotation"):
        hits("@deprecated def f(): Int = 1\n", "annotates" -> "deprecated")

      . assert(_ == 1)

      test(m"annotates matches an expression annotation"):
        hits("val x = (y: @unchecked)\n", "annotates" -> "unchecked")

      . assert(_ == 1)

      test(m"An annotation is not an instantiation"):
        hits("@deprecated def f(): Int = 1\n", "instantiates" -> "deprecated")

      . assert(_ == 0)

      test(m"instantiates matches new and a capitalised apply"):
        hits
          ( "val a = new Foo(1)\nval b = Foo(2)\nval c = foo(3)\n",
            "instantiates" -> "Foo",
            "instantiates" -> "foo" )

      . assert(_ == 2)

      test(m"extends matches a parent with and without arguments"):
        hits
          ( "class A extends Base(1)\nobject B extends Base, Other\ntrait C extends Base[Int]\n",
            "extends" -> "Base" )

      . assert(_ == 3)

      test(m"derives matches a derives clause and not a parent"):
        hits("case class A(x: Int) extends B derives Show\n", "derives" -> "Show", "derives" -> "B")

      . assert(_ == 1)

      test(m"imports matches a path and everything beneath it"):
        hits
          ( "import scala.collection.mutable\nimport scala.collection.mutable.Map\n" +
            "import scala.collection.immutable.Map\n",
            "imports" -> "scala.collection.mutable" )

      . assert(_ == 2)

      test(m"imports matches a selector beneath the path"):
        hits
          ( "import scala.collection.{mutable, immutable}\n",
            "imports" -> "scala.collection.mutable" )

      . assert(_ == 1)

      test(m"defines matches definitions by glob, not parameters"):
        hits
          ( "class FooImpl(barImpl: Int)\ndef bazImpl(): Int = 1\nval quxImpl = 2\n",
            "defines" -> "*Impl" )

      . assert(_ == 3)

      test(m"overrides matches only override definitions"):
        hits
          ( "class A:\n  override def toString: String = \"\"\n  def hashCode: Int = 1\n",
            "overrides" -> "toString",
            "overrides" -> "hashCode" )

      . assert(_ == 1)

      test(m"uses matches language constructs"):
        hits
          ( "def f(): Int =\n  var x = 1\n  while x < 2 do x += 1\n" +
            "  if x == null then throw Error() else return x\n",
            "uses" -> "var",
            "uses" -> "while",
            "uses" -> "null",
            "uses" -> "throw",
            "uses" -> "return" )

      . assert(_ == 5)

      test(m"uses matches modifiers and shapes"):
        hits
          ( "implicit val a: Int = 1\nlazy val b = 2\ntype T <: Any\nimport x.*\n" +
            "def f(g: => Int, h: Int = 1): Int = g\ndef +(o: Int): Int = o\n",
            "uses" -> "implicit",
            "uses" -> "lazy",
            "uses" -> "abstract-type",
            "uses" -> "wildcard-import",
            "uses" -> "by-name",
            "uses" -> "default-argument",
            "uses" -> "symbolic-name" )

      . assert(_ == 7)

      test(m"matches runs a regex over the source lines"):
        hits("val x = 1 // TODO later\nval y = 2 // FIXME\n", "matches" -> "TODO|FIXME")

      . assert(_ == 2)

      test(m"A hit is positioned on the construct"):
        projectRules(rule("cast", "invokes" -> "asInstanceOf"), "val x = y.asInstanceOf[Int]\n")
        . map: v => (v.line, v.rule)

      . assert(_ == List((35, "cast")))

      test(m"A rule's message is reported"):
        val cast = Predicate("invokes", "asInstanceOf")
        val definition = Definition("cast", List(cast), Some("no casts"))
        projectRules(definition, "val x = y.asInstanceOf[Int]\n").map(_.message)

      . assert(_ == List("no casts"))

      test(m"A rule that is defined but not enforced does not fire"):
        val defined = config.copy(rules = List(rule("cast", "invokes" -> "asInstanceOf")))
        val source = stub("val x = y.asInstanceOf[Int]\n")
        Checker.check("<test>", Some("flare"), source, config = defined).toList.map(_.rule)

      . assert(!_.contains("cast"))

      test(m"An invalid construct is a configuration error"):
        Predicates.validate(Predicate("uses", "goto"))

      . assert(_ != None)

      test(m"An invalid regex is a configuration error"):
        Predicates.validate(Predicate("matches", "("))

      . assert(_ != None)

    suite(m"Gates"):
      test(m"A second gate with its own prefix is checked"):
        val privileged = config.copy(gates = List(unsafe, Gate("Privileged", "privileged")))

        Checker.check("<test>", Some("flare"), stub("def raw(using erased Privileged): Int = 1\n"),
            config = privileged).toList.map(_.message)

      . assert(_.exists(_.contains("`privileged`")))

      test(m"A name claiming one gate is not checked against another"):
        val privileged = config.copy(gates = List(unsafe, Gate("Privileged", "privileged")))

        val source = stub("def privilegedRead(using erased Privileged): Int = 1\n")
        Checker.check("<test>", Some("flare"), source, config = privileged).toList.map(_.rule)

      . assert: r => !r.exists(_.startsWith("c.9.1"))

    suite(m"Omitted and strict rules"):
      test(m"An omitted rule does not fire"):
        val omitting = config.copy(omitted = Set("c.1.7"))
        val source = stub("val x = 1 \n")
        Checker.check("<test>", Some("flare"), source, config = omitting).toList.map(_.rule)

      . assert(!_.contains("c.1.7"))

      test(m"An omitted principle removes every rule of it"):
        val enabled = Rules.enabled(config.copy(omitted = Set("c.1"))).map(_.id)
        (enabled.exists(_.startsWith("c.1.")), enabled.exists(_.startsWith("c.2.")))

      . assert(_ == (false, true))

      test(m"A project rule's enforcement does not touch the style's rules"):
        val c = Definition("c", List(Predicate("invokes", "asInstanceOf")), None, "error")
        val enforcing = config.copy(rules = List(c), enforced = Map("c" -> Some("error")))
        Linter.severity(enforcing, "c.1.7")

      . assert(_ == Linter.Severity.Warning)

      test(m"A strict rule is an error and the rest are warnings"):
        val strict = config.copy(strict = Set("c.1.7"))
        (Linter.severity(strict, "c.1.7"), Linter.severity(strict, "c.1.4"))

      . assert(_ == (Linter.Severity.Error, Linter.Severity.Warning))

      test(m"An enforcement's severity overrides the rule's own"):
        val cast = Definition("cast", List(Predicate("invokes", "asInstanceOf")), None, "error")
        val enforcing = config.copy(rules = List(cast), enforced = Map("cast" -> Some("warn")))

        Linter.severity(enforcing, "cast")

      . assert(_ == Linter.Severity.Warning)

      test(m"A rule's own severity applies when the enforcement names none"):
        val cast = Definition("cast", List(Predicate("invokes", "asInstanceOf")), None, "error")
        val enforcing = config.copy(rules = List(cast), enforced = Map("cast" -> None))

        Linter.severity(enforcing, "cast")

      . assert(_ == Linter.Severity.Error)

    suite(m"Licence header"):
      test(m"A file beginning with the header is accepted"):
        rules("val x = 1\n")

      . assert(!_.contains("c.1.1"))

      test(m"A differing header line is reported at that line"):
        val text = stub("val x = 1\n").replaceFirst("x\n", "y\n")
        Checker.check("<test>", Some("flare"), text, config = config).toList
        . filter(_.rule == "c.1.1").map(_.line)

      . assert(_ == List(2))

      test(m"A file shorter than the header is reported"):
        Checker.check("<test>", Some("flare"), "/*\nx\n", config = config).toList
        . filter(_.rule == "c.1.1").map(_.line)

      . assert(_ == List(3))

      test(m"A placeholder in a template header line matches any text and padding"):
        FrameRules.LicenceFrame.matches
          ( "|   Soundness, version ${SOUNDNESS}.        |",
            "|   Soundness, version 0.64.0.              |" )

      . assert(_ == true)

      test(m"A template line still requires its literal text"):
        FrameRules.LicenceFrame.matches
          ( "|   Soundness, version ${SOUNDNESS}.   |", "|   Flare, version 0.2.1.              |" )

      . assert(_ == false)

      test(m"A project with no header has no header rules"):
        Checker.check("<test>", Some("flare"), "package flare\n\nval x = 1\n",
            config = config.copy(header = None)).toList.map(_.rule)

      . assert: r => !r.exists(Set("c.1.1", "c.1.2", "c.1.3"))

    suite(m"Bolt codes"):
      test(m"A violation of a style rule is reported under ↯/f.21.1, carrying the rule's code"):
        FlareBolts.prefix("c.2.2.1", false)

      . assert(_ == "[↯/f.21.1/c.2.2.1] ")

      test(m"A violation of a project rule is reported under ↯/f.21.2, with its name"):
        FlareBolts.prefix("cast", false)

      . assert(_ == "[↯/f.21.2] cast: ")

      test(m"A code is coloured as fulminate colours it"):
        FlareBolts.colorLabel("f.21.1/c.2.2.1")

      . assert(_ == Bolt('f', 21, 1, cause = Bolt('c', 2, 2, 1)).colorLabel.toString)

      test(m"A coloured violation begins with the coloured code"):
        FlareBolts.prefix("c.1.4", true)

      . assert(_ == Bolt('f', 21, 1, cause = Bolt('c', 1, 4)).colorLabel.toString+" ")

      test(m"Every rule of the style is identified by a code"):
        Rules.style.map(_.id).filterNot(Rules.styled)

      . assert(_ == Nil)

    suite(m"Linter"):
      test(m"The linter reports violations with severities and the census"):
        val parsed = parse("val x = y.asInstanceOf[Int] \n")
        val enforcing = counted.copy(enforced = Map("casts" -> Some("error")))

        val report =
          Linter.check(enforcing, "<test>", stub("val x = y.asInstanceOf[Int] \n"), parsed.tree,
              parsed.source, features)

        val severities =
          report.violations.map { v => (v(0).rule, v(1)) }.filter: v =>
            v(0) == "casts" || v(0) == "c.1.7"

        (severities, report.census.toMap.get("casts"), report.errors)

      . assert: outcome =>
          val severities =
            List(("c.1.7", Linter.Severity.Warning), ("casts", Linter.Severity.Error))

          outcome == (severities, Some(1), true)

      test(m"Every rule is a parser rule"):
        Rules.lastPhase(Rules.enabled(counted.copy(enforced = Map("casts" -> None))))

      . assert(_ == "parser")

    suite(m"Candidates: definitions put to a model"):
      val plumbing: String = plumbingFixture

      def candidates(body: String): List[Candidates.Candidate] =
        val parsed = parse(body)
        Candidates.collect("<test>", stub(body), parsed.tree, parsed.source, Candidates.Filters())

      val found = candidates(plumbing)

      test(m"The gated definitions are collected and the rest are not"):
        found.map(_.name).sorted

      . assert: names =>
          val expected =
            List
              ( "bytes",
                "conversion",
                "kebab",
                "monthName",
                "normalise",
                "tag",
                "tag",
                "typeKindToFrame" )

          names == expected

      test(m"Kinds are read from modifiers and nesting"):
        found.map { c => c.name -> c.kind.word }.toMap

      . assert: kinds =>
          kinds ==
            Map
              ( "bytes"           -> "local",
                "conversion"      -> "conversion",
                "kebab"           -> "private",
                "monthName"       -> "public",
                "normalise"       -> "private",
                "tag"             -> "public",
                "typeKindToFrame" -> "private" )

      test(m"A locus is the owner chain and the signature as written"):
        found.find(_.name == "kebab").map(_.locus)

      . assert(_ == Some("<test> ▸ Tels.kebab(s: String): Text"))

      test(m"A local def's locus descends through its enclosing def"):
        found.find(_.name == "bytes").map(_.locus)

      . assert(_ == Some("<test> ▸ Tels.process(text)/bytes(text: Text): Data"))

      test(m"Lines are display-only: a locus survives lines inserted above"):
        val shifted = candidates("// a comment\n\n\n" + plumbing)
        (shifted.map(_.locus), shifted.map(_.normalised), shifted.map(_.line) != found.map(_.line))

      . assert(_ == (found.map(_.locus), found.map(_.normalised), true))

      test(m"A rename keeps the normalised text"):
        val dashed = plumbing.replace("def kebab(s: String)", "def dashed(text: String)")
        val renamed = candidates(dashed.replace("Text(s)", "Text(text)"))
        renamed.find(_.name == "dashed").map(_.normalised)

      . assert(_ == found.find(_.name == "kebab").map(_.normalised))

      test(m"Verbatim twins with different parameter names are duplicates"):
        val twins = Candidates.duplicates(found)
        found.filter(_.name == "tag").map: c => twins(c.normalised)

      . assert(_ == List(1, 1))

      test(m"Call sites are counted within the definition's scope"):
        found.map { c => c.name -> c.calls }.toMap.filter: (name, _) =>
          name == "bytes" || name == "monthName"

      . assert(_ == Map("bytes" -> 1, "monthName" -> 0))

      test(m"One-liners and body lengths are measured"):
        found.map { c => c.name -> (c.oneLiner, c.bodyLines) }.toMap

      . assert: measures =>
          measures ==
            Map
              ( "bytes"           -> (true, 1),
                "conversion"      -> (true, 1),
                "kebab"           -> (true, 1),
                "monthName"       -> (true, 1),
                "normalise"       -> (true, 1),
                "tag"             -> (true, 1),
                "typeKindToFrame" -> (true, 4) )
