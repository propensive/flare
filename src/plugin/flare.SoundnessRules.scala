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
import dotty.tools.dotc.core.Flags
import dotty.tools.dotc.core.NameOps.isConstructorName
import dotty.tools.dotc.util.SourceFile

object SoundnessRules:
  // Does this name claim the gate? The prefix alone, or the prefix followed by
  // an uppercase letter: it must be a word of its own. This is what exempts
  // `unsafely` — the entry point that supplies the token — and `unsafety`,
  // without either needing a special case. The census asks the same question
  // when it counts unbacked claims, so it is answered in one place.
  def claims(name: String, gate: Gate): Boolean =
    name == gate.prefix ||
      (name.startsWith(gate.prefix) && name.lift(gate.prefix.length).exists(_.isUpper))

  // Does `defn` take the gate's token in a `using` clause? The token is
  // matched on its simple name, so `Unsafe`, `vacuous.Unsafe` and an `erased`
  // or anonymous parameter all count.
  def gated(defn: untpd.DefDef, gate: Gate): Boolean =
    val simple = gate.simpleToken

    defn.paramss.exists:
      case params: List[?] => params.exists:
        case param: untpd.ValDef => param.mods.is(Flags.Given) && names(param.tpt, simple)
        case _                   => false

  // Definitions the naming rule has an opinion about. A constructor cannot be
  // renamed, and a `given` is summoned by type rather than called by name, so
  // neither can carry the prefix; both are left to the soundness argument in
  // their comment.
  def nameable(defn: untpd.DefDef): Boolean =
    !defn.name.isConstructorName && !defn.mods.is(Flags.Given)

  // Does this type tree name `target`? Only the simple name is compared, so a
  // qualified reference matches its own last segment.
  private def names(tpt: untpd.Tree, target: String): Boolean = tpt match
    case untpd.Ident(name)     => name.toString == target
    case untpd.Select(_, name) => name.toString == target
    case _                     => false

  // ↯/c.9.1: a method that takes a gate's token as a `using` parameter must be
  // named for what it is, and a method named for what it is must take the
  // token. The two directions are one rule because either alone is a lie: an
  // ungated `unsafe…` name claims a checked boundary that nothing enforces,
  // and a gated method with an ordinary name hides one that does.
  //
  // The gates are configuration, and the rule does not fire when there are
  // none — a project that has no such token has nothing to check.
  object UnsafeNaming extends Rule:
    def id: String = "c.9.1"
    def principle: Principle = Principle.Soundness

    def check(ctx: Context): List[Violation] =
      ctx.config.gates.flatMap: gate =>
        val out = mutable.ListBuffer[Violation]()

        walk(ctx.tree):
          case defn: untpd.DefDef if nameable(defn) =>
            val name   = defn.name.toString
            val claim  = claims(name, gate)
            val gating = gated(defn, gate)

            if gating && !claim then
              out += violation(ctx, defn, "c.9.1.1",
                  s"`$name` is gated by `${gate.token}`, so its name must begin `${gate.prefix}`")
            else if claim && !gating then
              out += violation(ctx, defn, "c.9.1.2",
                  s"`$name` is named as `${gate.prefix}`, so it must take " +
                    s"`(using erased ${gate.token})`")

          case _ =>
            ()

        out.toList

    // A violation at the definition's name. `span.point` is the name's offset
    // for a `DefDef`, which puts the diagnostic on the identifier rather than
    // on a leading modifier.
    private def violation(ctx: Context, defn: untpd.DefDef, rule: String, message: String)
    :   Violation =

      val (line, column) = position(ctx.source, defn.span.point)
      Violation(ctx.file, line, column, rule, message)

    private def position(source: SourceFile, offset: Int): (Int, Int) =
      if offset < 0 || offset > source.content.length then (1, 1)
      else (source.offsetToLine(offset) + 1, source.column(offset) + 1)

  // Generic untyped-tree pre-order traversal, as in `Definitions`.
  private def walk(tree: untpd.Tree)(visit: untpd.Tree => Unit): Unit =
    visit(tree)
    tree.productIterator.foreach(descend(_, visit))

  private def descend(node: Any, visit: untpd.Tree => Unit): Unit = node match
    case sub: untpd.Tree    => walk(sub)(visit)
    case items: Iterable[?] => items.foreach(descend(_, visit))
    case _                  => ()
