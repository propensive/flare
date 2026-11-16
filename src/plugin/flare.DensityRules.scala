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

object DensityRules:
  // ↯/c.3.1 — the "necessity" rule: a construct spread over several source
  // lines whose one-line rendering would fit within the line limit is a
  // violation — a break must always mean "didn't fit". The measurement,
  // the construct kinds covered, and the (extensive) bail-outs all live in
  // `Necessity`.
  object UnnecessaryBreak extends Rule:
    def id: String = "c.3.1"
    def principle: Principle = Principle.Density

    def check(ctx: Context): List[Violation] =
      ctx.necessity.map: site =>
        Violation
          ( ctx.file, site.line, site.col, "c.3.1",
            s"this ${site.kind} fits on one line (would be ${site.width} columns); " +
              "breaks are reserved for code that does not fit" )

  // ↯/c.3.2 — lambda layout, four sub-rules:
  //   ↯/c.3.2.1 named single-line lambda using `(…)` (must be `{…}` or `: …`)
  //   ↯/c.3.2.2 named single-line `{…}` at end-of-line (must be `: …`)
  //   ↯/c.3.2.3 multi-line lambda using `{…}` or `(…)` (must be `: …`)
  //   ↯/c.3.2.4 anonymous (placeholder) lambda using `{…}` or `: …` (must be `(…)`)
  object LambdaLayout extends Rule:
    def id: String = "c.3.2"
    def principle: Principle = Principle.Density

    def check(ctx: Context): List[Violation] =
      import Lambdas.Opener
      val file = ctx.file
      val out  = mutable.ListBuffer[Violation]()

      ctx.lambdaSites.foreach: s =>
        // Multi-line wins regardless of parameter shape — only a colon-arg
        // body can house a multi-line lambda cleanly.
        if s.isMultiLine then
          if s.opener != Opener.Colon then
            out +=
              Violation
                ( file, s.openerLine, s.openerCol, "c.3.2.3",
                  "multi-line lambda must use a colon-arg `f: x => …` form, " +
                    s"not `${s.opener.toString.toLowerCase}`" )
        else if s.isAnonymous then
          if s.opener != Opener.Paren then
            out +=
              Violation
                ( file, s.openerLine, s.openerCol, "c.3.2.4",
                  "anonymous (`_`-)lambda must be wrapped in `(…)`, not " +
                    s"`${s.opener.toString.toLowerCase}`" )
        else // named, single-line
          if s.opener == Opener.Paren then
            val alternative =
              if s.lastOnLine then " (or use `f: x => …` since the lambda is last on the line)"
              else ""

            out +=
              Violation
                ( file, s.openerLine, s.openerCol, "c.3.2.1",
                  "named-parameter lambda must be wrapped in `{…}`, not `(…)`" + alternative )
          else if s.opener == Opener.Brace && s.lastOnLine then
            out +=
              Violation
                ( file, s.openerLine, s.openerCol, "c.3.2.2",
                  "lambda is the last thing on its line; prefer `f: x => …` " +
                    "over `f { x => … }`" )

      out.toList
