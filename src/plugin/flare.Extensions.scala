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
import dotty.tools.dotc.util.SourceFile

object Extensions:
  // Collect the leaf names of every public method declared in a top-level
  // `extension` block. Such methods are re-exported into the umbrella package by their
  // simple name (just like any other public member), so ↯/c.8.5 needs the list
  // of names a component's export surface is expected to carry. Methods marked
  // `private`/`protected` — including qualified forms like `private[foo]`, which
  // carry a `privateWithin` but not always the bare `Private` flag — are
  // package-internal and neither can nor need to be re-exported, so skip them.
  def extract(tree: untpd.Tree, source: SourceFile): List[String] =
    val out = mutable.ListBuffer[String]()

    def public(method: untpd.DefDef): Boolean =
      !method.mods.flags.isOneOf(Flags.Private | Flags.Protected) &&
        method.mods.privateWithin.isEmpty

    walk(tree):
      case ext: untpd.ExtMethods =>
        ext.methods.foreach:
          case method: untpd.DefDef if public(method) =>
            val name = method.name.toString
            if name.nonEmpty then out += name

          case _ =>
            ()

      case _ =>
        ()

    out.distinct.toList

  // Generic untyped-tree pre-order traversal driven off `productIterator`,
  // mirroring the helper in `Definitions` and `Comprehensions`.
  private def walk(t: untpd.Tree)(visit: untpd.Tree => Unit): Unit =
    visit(t)
    t.productIterator.foreach(descend(_, visit))

  private def descend(x: Any, visit: untpd.Tree => Unit): Unit = x match
    case sub: untpd.Tree  => walk(sub)(visit)
    case it:  Iterable[?] => it.foreach(descend(_, visit))
    case _                => ()
