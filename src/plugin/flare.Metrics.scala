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

// A census of the constructs a project wants to watch the size of, counted
// per file so the totals can be tracked over time.
//
// This is deliberately not a rule: none of what it counts is a violation.
// A project that concentrates its unsafety behind a small set of named
// constructs still needs to know how much is behind them, and whether that
// is growing — a question no per-file diagnostic can answer, because the
// answer is a number over the whole corpus.
//
// What is counted is what the project's configuration defines: every project
// rule, enforced or not, under its name; and for every gate, the definitions
// it gates and the definitions that claim it without being gated. The counts
// are therefore a gauge rather than a census of semantics — good enough to
// see a trend, and not to be read as anything finer.
object Metrics:
  // Count every watched construct in one file. The result is sorted by
  // indicator, and omits indicators that did not occur.
  def collect(ctx: Context): List[(String, Int)] =
    val counts = mutable.LinkedHashMap[String, Int]()
    def bump(key: String): Unit = counts(key) = counts.getOrElse(key, 0) + 1

    ctx.projectHits.foreach: hit => bump(hit.definition.name)

    if ctx.config.gates.nonEmpty then
      walk(ctx.tree):
        case defn: untpd.DefDef =>
          ctx.config.gates.foreach: gate =>
            if SoundnessRules.gated(defn, gate) then bump(s"${gate.prefix}-gate")

            // A definition that claims the gate without taking the token: what
            // ↯/c.9.1.2 reports. It is counted as well as reported because a warning
            // can be hidden -- dotty keeps only the first diagnostic at a
            // position, so a file that already warns about something enclosing
            // loses it -- and an exemption a project has decided to live with
            // should still be visible in the number.
            else if SoundnessRules.nameable(defn) && SoundnessRules.claims(defn.name.toString, gate)
            then bump(s"${gate.prefix}-ungated")

        case _ =>
          ()

    counts.toList.sortBy(_(0))

  private def walk(tree: untpd.Tree)(visit: untpd.Tree => Unit): Unit =
    visit(tree)
    tree.productIterator.foreach(descend(_, visit))

  private def descend(node: Any, visit: untpd.Tree => Unit): Unit = node match
    case sub: untpd.Tree    => walk(sub)(visit)
    case items: Iterable[?] => items.foreach(descend(_, visit))
    case _                  => ()
