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

// The bolt codes of flare, which begin `↯/f`, as those of Pyrocosm and its other tools do
// (`pyrocosm.Bolts`). Each module of flare is a subdivision, numbered after Pyrocosm's own and
// those of the tools before it; the record of what every code means is `bolt.tel` in
// propensive/pyrocosm. The plugin is loaded into a compiler which has no Soundness, so here a code
// is written as text without its `↯/` — `f.21.1/c.2.2.1` — and the client makes a
// `fulminate.Bolt` of the same numbers.
object FlareBolts:
  final val plugin   = 21
  final val records  = 22
  final val client   = 23
  final val launcher = 24

  // The plugin's errors: a violation of a rule of Consequent Style, which carries the rule's own
  // `c` code; a violation of a project rule, which has none; and an option the plugin could not
  // use, with a variant for each way it can fail.
  final val violation        = 1
  final val projectViolation = 2
  final val option           = 3

  // The `error`th error of `subdivision`, or its `variant`th variant: `f.21.3.2`.
  def code(subdivision: Int, error: Int, variant: Int = 0): String =
    val suffix = if variant == 0 then "" else s".$variant"
    s"f.$subdivision.$error$suffix"

  // The code a violation of `rule` is reported under: `f.21.1/c.2.2.1` for a rule of the style,
  // and `f.21.2` for a project rule, which is named beside it.
  def reported(rule: String): String =
    if Rules.styled(rule) then s"${code(plugin, violation)}/$rule"
    else code(plugin, projectViolation)

  // The code in brackets, as it is written before a message: `[↯/f.21.1/c.2.2.1]`.
  def label(code: String): String = s"[↯/$code]"

  // The bracketed code in colour, for a terminal, exactly as fulminate's `Bolt.colorLabel`
  // renders it: the brackets, `/` and dots gray, `↯` and the project letters orange, the numbers
  // cyan.
  def colorLabel(code: String): String =
    val esc    = 27.toChar
    val gray   = s"$esc[38;2;128;128;128m"
    val orange = s"$esc[38;2;255;165;0m"
    val cyan   = s"$esc[38;2;0;200;255m"
    val reset  = s"$esc[0m"

    val groups =
      code.split("/").nn.toList.map(_.nn).map: group =>
        val numbers = group.drop(2).split("\\.").nn.toList.map(_.nn)
        s"$gray/$orange${group.take(1)}" + numbers.map { number => s"$gray.$cyan$number" }.mkString

    s"$gray[$orange↯${groups.mkString}$gray]$reset"

  // How the report of a violation of `rule` begins: its code's label, and a project rule's name.
  // `[↯/f.21.1/c.2.2.1] ` for a rule of the style, and `[↯/f.21.2] cast: ` for a project rule.
  def prefix(rule: String, color: Boolean): String =
    val bolt    = reported(rule)
    val written = if color then colorLabel(bolt) else label(bolt)
    if Rules.styled(rule) then s"$written " else s"$written $rule: "

  // An option the plugin could not use, labelled with the variant of `f.21.3` it is.
  def problem(variant: Int, message: String): String =
    s"${label(code(plugin, option, variant))} $message"
