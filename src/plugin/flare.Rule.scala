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

// The principles from which every rule derives. Part I of
// doc/consequent-style.md states each of the style's principles and its
// readability motivation; every rule in `Rules.style` cites the principle it
// derives from. Eight are about how code reads; Soundness is about what it
// claims, and is the one whose rules a project must configure to enable.
// Project is not a principle of the style at all: it is where a project's
// own rules — defined in its configuration — sit in a report.
enum Principle:
  case Frame, Anchoring, Density, ContinuationMarking, Balance, Proximity, Tabulation,
    Locatability, Soundness, Project

// One rule: an identifier (a style rule's is its code without the `↯/`, and
// one such as `c.2.2` may span several variants, emitted with the variant's
// code; a project rule's is its name), the principle it derives from, the
// last compiler phase whose output it needs, and a check over the per-file
// `Context`. A rule must not throw on malformed input: extraction failures
// surface as an absence of data, never as an exception.
trait Rule:
  def id: String
  def principle: Principle
  def check(ctx: Context): List[Violation]

  // The compiler phase after which this rule can run. Every rule today reads
  // the parser's output alone; `flare check` stops the compiler after the
  // latest phase any enabled rule names.
  def phase: String = "parser"

// A project rule: one of the definitions in a project's configuration, checked
// where a profile enforces it. Its hits come from the per-file `Context`, which
// evaluates every definition in one traversal, so enforcing several costs no
// more than enforcing one.
final class ProjectRule(val definition: Definition) extends Rule:
  def id: String = definition.name
  def principle: Principle = Principle.Project

  def check(ctx: Context): List[Violation] =
    ctx.projectHits.filter(_.definition.name == definition.name).map: hit =>
      Violation(ctx.file, hit.line, hit.column, id, definition.message.getOrElse(describe(hit)))

  // The default message: what the predicate saw, in its own terms.
  private def describe(hit: Predicates.Hit): String =
    val target = hit.predicate.target

    hit.predicate.kind match
      case "invokes"      => s"`$target` is invoked here"
      case "references"   => s"`$target` is referenced here"
      case "annotates"    => s"`@$target` is applied here"
      case "instantiates" => s"`$target` is instantiated here"
      case "extends"      => s"`$target` is extended here"
      case "derives"      => s"`$target` is derived here"
      case "imports"      => s"`$target` is imported here"
      case "defines"      => s"`$target` is defined here"
      case "overrides"    => s"`$target` is overridden here"
      case "uses"         => s"`$target` is used here"
      case "matches"      => s"this line matches `$target`"
      case kind           => s"`$target` $kind here"

object Rules:
  // The rules of Consequent Style, in emission order: dotty's reporter keeps
  // only the first diagnostic per position, so the order documents (and
  // preserves) the collision resolutions of the old per-line walk.
  val style: List[Rule] =
    List
      ( // RawTabs (↯/c.1.5) leads the registry: the old raw pre-tokenizer scan
        // ran in the preamble of `check`, before every other rule, so any
        // positional collision with a later rule must keep resolving in its
        // favour. (No such collision exists in the corpus — the tree is
        // tab-free — but the historical order is preserved regardless.)
        FrameRules.RawTabs,
        FrameRules.LicenceFrame, FrameRules.PackageDeclaration, FrameRules.PackageBlank,
        FrameRules.ImportSeparation, FrameRules.ImportOrdering,
        AnchorRules.SequenceLayout, AnchorRules.DefinitionAnchors,
        // OperatorContinuation must precede ContinuationIndent: both can fire
        // at the same position (a continuation line led by an operator), and
        // dotty's reporter keeps only the first diagnostic per position — ↯/c.4.2
        // is the more specific message there.
        ContinuationRules.OperatorContinuation,
        AnchorRules.BodyScopeIndent, AnchorRules.ContinuationIndent,
        AnchorRules.SignatureEqLast,
        AnchorRules.InterpolationLayout, TabulationRules.CaseAlignment,
        TabulationRules.ForComprehensionAlignment, DensityRules.LambdaLayout,
        ProximityRules.ChunkSeparation,
        LocatabilityRules.FileNaming, LocatabilityRules.CompanionOrdering,
        LocatabilityRules.UmbrellaExportCompleteness,
        LocatabilityRules.ExtensionExportCompleteness,
        // LineLength (↯/c.1.4) fired at the top of the old per-line walk, before
        // the quote/splice family — it must keep winning their one positional
        // collision (a 101-column line inside an inline splice).
        FrameRules.LineLength,
        // The former per-line-walk cluster, in its old within-line order:
        // ↯/c.1.6, ↯/c.1.7, ↯/c.6.3, then the `checkTokens` family (↯/c.1.8, ↯/c.5.1, ↯/c.5.2,
        // ↯/c.5.3, in `checkTokens` call order), then ↯/c.6.1. All of these fired
        // after LineLength (↯/c.1.4) at the top of `checkLine` and before the
        // ↯/c.4.3, ↯/c.4.1, ↯/c.2.7 and ↯/c.6.4 block below. Two placement constraints carry
        // evidence from the corpus:
        //  - IndentWidth (↯/c.1.6) must follow ProximityRules.ChunkSeparation:
        //    both fire at monotonous.Alphabet:186:1, and ↯/c.6.2 is the
        //    surviving diagnostic in the baseline compile, so ↯/c.6.2 must keep
        //    winning — dotty's reporter keeps only the first diagnostic per
        //    position.
        //  - No other positional collision involves ↯/c.1.6, ↯/c.1.7, ↯/c.6.3, ↯/c.1.8,
        //    ↯/c.5.1, ↯/c.5.2, ↯/c.5.3 or ↯/c.6.1 anywhere in the corpus (checked over
        //    the full parse-path oracle), so the remaining placements
        //    simply preserve the old walk's within-line order.
        FrameRules.IndentWidth, FrameRules.TrailingWhitespace,
        ProximityRules.BlankLineRun, FrameRules.CommentShape,
        BalanceRules.OperatorSpacing, BalanceRules.AssignmentSpacing,
        BalanceRules.SymbolicMethodNames, ProximityRules.CommaSpacing,
        // These four fired towards the end of the old per-line walk (in this
        // order: ↯/c.4.3, ↯/c.4.1, ↯/c.2.7, ↯/c.6.4), after LineLength and before the
        // quote/splice family, so they sit between those two here. No
        // positional collision with any other rule exists in the corpus, but
        // the relative order is preserved regardless.
        ContinuationRules.HardSpace, ContinuationRules.ChainContinuation,
        AnchorRules.GivenArrowAlign, ProximityRules.ReturnTypeBlank,
        // FormalBlockSpacing (↯/c.5.4) must follow FrameRules.LineLength: both
        // fire at turbulence.Benchmarks:523:101, and ↯/c.1.4 fired first in the
        // old walk (LineLength ran at the top of checkLine, before the
        // bracket-interior checks), so ↯/c.1.4 must keep winning there.
        // HeavyBracketAnchor (↯/c.2.4) must follow AnchorRules
        // .ContinuationIndent: both fire at jacinta.stagedInternal:621:21
        // and monotonous.Alphabet:287:25, and ↯/c.2.1 won in the baseline
        // compile (↯/c.2.1 is the surviving diagnostic at monotonous
        // .Alphabet:287:25), so it must keep firing first. No other
        // positional collision involves ↯/c.5.4, ↯/c.5.5, ↯/c.7.2 or ↯/c.2.4 in the
        // corpus.
        BalanceRules.FormalBlockSpacing, BalanceRules.CompactBracketSpacing,
        TabulationRules.UsingAlignment, AnchorRules.HeavyBracketAnchor,
        // AnnotationAdjacency (↯/c.6.6) fired at the very bottom of the old
        // `checkLine` (the ↯/c.6.6.2 blank-line check and the end-of-file ↯/c.6.6.1
        // flush both ran after every other per-line check), so it sits after
        // the whole walk-era cluster; no positional collision involves ↯/c.6.6
        // in the corpus.
        ProximityRules.AnnotationAdjacency,
        // QuoteSpliceLayout stays last: the ↯/c.2.9.1–↯/c.2.9.6 family used to fire at
        // the end of the per-line walk, after every registry rule, so any
        // positional collision with an earlier rule (e.g. ↯/c.4.2.1 with ↯/c.2.9.4)
        // must keep resolving in the earlier rule's favour — dotty's
        // reporter keeps only the first diagnostic per position.
        QuoteRules.QuoteSpliceLayout,
        // UnnecessaryBreak (↯/c.3.1) is a new rule, not a port of the per-line
        // walk, so it carries no historical ordering evidence — it sits
        // last so that any positional collision with an established rule
        // resolves in the established rule's favour. The corpus has one
        // such collision: ↯/c.3.1 and ↯/c.2.9.4 both fire at stratiform
        // .bintelInternal:633:13, and ↯/c.2.9.4 (the established diagnostic)
        // must keep winning — dotty's reporter keeps only the first
        // diagnostic per position.
        DensityRules.UnnecessaryBreak,
        // UnsafeNaming (↯/c.9.1) is likewise a new rule with no historical
        // ordering evidence, and reports on a definition's name — a position
        // no layout rule occupies — so it sits last and yields to any
        // established rule it should ever collide with.
        SoundnessRules.UnsafeNaming )

  // Is `rule` a rule of the style, named by its code without the `↯/` —
  // `c.2.2`, or a variant, `c.2.2.1` — rather than a project rule, named as
  // the project names it?
  def styled(rule: String): Boolean = rule.matches("c(\\.[0-9]+)+")

  // The code a rule or a principle of the style named in the letter form of
  // the identifiers before the codes now has, the letters being the initials
  // of the principles in the order of their numbers: `L4` is now `c.8.4`,
  // `A2.1` is `c.2.2.1` and `S` is `c.9`. `None` for any other text.
  def migrated(entry: String): Option[String] =
    def principle(letter: String): Int = "FADCBPTLS".indexOf(letter) + 1

    entry match
      case LetterRule(letter, number) => Some(s"c.${principle(letter.nn)}.$number")
      case LetterPrinciple(letter)    => Some(s"c.${principle(letter.nn)}")
      case _                          => None

  private val LetterRule      = "([FADCBPTLS])([0-9]+(?:\\.[0-9]+)?)".r
  private val LetterPrinciple = "([FADCBPTLS])".r

  // The rules a configuration enables: the style's, less those it omits, then
  // a project rule for each definition it enforces, in the order they were
  // enforced. Project rules come last for the same reason the newest style
  // rules do: a positional collision resolves in the established rule's favour.
  def enabled(config: Config): List[Rule] =
    style.filterNot { rule => config.omits(rule.id) } ++
      config.rules.filter { d => config.enforced.contains(d.name) }.map(ProjectRule(_))

  // The latest compiler phase any of these rules needs, in the order the
  // compiler runs them. Today every rule is a parser rule.
  def lastPhase(rules: List[Rule]): String =
    val order = List("parser", "typer")
    rules.map(_.phase).maxByOption(order.indexOf).getOrElse("parser")
