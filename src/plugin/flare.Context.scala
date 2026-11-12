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

// The per-file bundle of inputs and derived data shared by every check.
// Construction is cheap; derived values are computed lazily, at most once
// per file.
final class Context
  ( val file:              String,
    val expectedModule:    Option[String],
    val text:              String,
    val tree:              untpd.Tree,
    val source:            SourceFile,
    val siblingTypes:      List[String],
    val siblingExtensions: List[String],
    val unexported:        Set[String],
    val config:            Config = Config(),
    val elsewhere:         Map[String, Set[String]] = Map.empty ):

  // Whether the standalone parse produced a usable tree. Every `lazy val`
  // below derives from `tree`; when the parse failed they are all empty, and a
  // rule that consults them would pass vacuously. Rules must not silently
  // weaken, so `Checker` reports the failure rather than checking on.
  def parsed: Boolean = !tree.isEmpty

  lazy val tokenRows: IndexedSeq[IndexedSeq[Lexeme]] = Tokenizer.tokenize(text)
  lazy val lines: IndexedSeq[Line] = tokenRows.map(Line(_))

  lazy val imports: List[ImportInfo] = Imports.extract(tree, source)
  lazy val packageInfo: Option[PackageInfo] = Packages.extract(tree, source)
  lazy val annotationEndLines: Set[Int] = Annotations.collectEndLines(tree, source)
  lazy val companions: CompanionDecls = Companions.extract(tree, source)
  lazy val caseGroups: List[List[CaseInfo]] = Cases.extract(tree, source)
  lazy val forGroups: List[List[GenLine]] = Comprehensions.extract(tree, source)
  lazy val sequences: List[Sequence] = Sequences.extract(tree, source)
  lazy val stmtGroups: List[StmtGroup] = Statements.extract(tree, source)
  lazy val lambdaSites: List[Lambdas.LambdaSite] = Lambdas.extract(tree, source)
  lazy val operatorSites: List[OpInfo] = Operators.extract(tree, source)
  lazy val interpolations: List[InterpolationInfo] = Interpolations.extract(tree, source)
  lazy val definitions: List[DefnAnchor] = Definitions.extract(tree, source)

  lazy val umbrellaExports: ExportInfo =
    UmbrellaExports.extract(tree, source, config.packageLine)

  // When this file is an export surface, every name it puts into the umbrella
  // package; for any other file, nothing.
  lazy val exported: List[Exported] =
    if Umbrella.surfaceComponent(file, config).isEmpty then Nil else UmbrellaExports.exported(tree)

  lazy val anchors: Anchors.AnchorModel = Anchors.build(tree, source, text)
  lazy val quoteSites: QuoteSites.Model = QuoteSites.extract(lines)

  lazy val necessity: List[Necessity.Site] =
    Necessity.extract(tree, source, text, config.columns)

  lazy val brackets: Brackets.Model = Brackets.extract(lines)

  // Every project rule's matches, evaluated together: the enforced rules
  // report theirs, and the census counts them all.
  lazy val projectHits: List[Predicates.Hit] =
    Predicates.hits(config.rules, tree, source, text, exported)
