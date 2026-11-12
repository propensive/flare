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

object LocatabilityRules:
  // L2: the file's name must match one of the documented conventions, and a
  // `<module>.<Type>.scala` file must actually declare that top-level type.
  object FileNaming extends Rule:
    def id: String = "L2"
    def principle: Principle = Principle.Locatability

    def check(ctx: Context): List[Violation] =
      val file = ctx.file
      val out  = mutable.ListBuffer[Violation]()

      val segments = file.split("/").nn
      val name     = segments(segments.length - 1).nn

      if name.endsWith(".scala") then
        val base   = name.substring(0, name.length - ".scala".length).nn
        val parts  = file.split(s"/${ctx.config.moduleRoot}/").nn
        val module = if parts.length < 2 then None else Some(parts(1).nn.split("/").nn(0).nn)
        val umbrella = ctx.config.umbrella

        val ok = module.fold(true): m =>
          base == s"${m}_core" ||
            base.startsWith(s"$m.") ||
            base.startsWith(s"${m}_") && base.endsWith("_core") ||
            umbrella.exists { u => base == s"${u}_${m}_core" } ||
            umbrella.exists { u => base.startsWith(s"${u}_") && base.endsWith("_core") } ||
            isCrossModuleExport(base)

        if !ok then
          out +=
            Violation
              ( file, 1, 1, "L2",
                s"file name `$name` does not match a documented naming convention" )
        else
          // Patterns of the form `<lowercase>.<Uppercase>.scala` name a single
          // top-level type. Verify the parsed file declares it as a class,
          // trait, enum, object, or type alias. Lowercase-tail patterns
          // (`gossamer.internal.scala`, `ambience.variables.scala`) are
          // namespace-style files and remain exempt.
          val typedName = """[a-z][a-zA-Z0-9_]*\.([A-Z][a-zA-Z0-9_]*)""".r

          typedName.findFirstMatchIn(base).foreach: m =>
            val typeName = m.group(1).nn

            if !declaresTopLevel(ctx.tree, typeName) then
              out +=
                Violation
                  ( file, 1, 1, "L2",
                    s"file name `$name` declares no top-level `$typeName` " +
                      "(class/trait/enum/object/type)" )

      out.toList

    // True iff the package body (or any nested package body) holds a `TypeDef`
    // or `ModuleDef` whose name matches `target`. Used by L2 to verify that
    // a `<module>.<Type>.scala` filename actually declares that type.
    private def declaresTopLevel(tree: untpd.Tree, target: String): Boolean =
      tree match
        case untpd.EmptyTree       => true  // parse failure: don't false-positive

        case pkg: untpd.PackageDef =>
          pkg.stats.exists:
            case td: untpd.TypeDef        => td.name.toString == target
            case md: untpd.ModuleDef      => md.name.toString == target
            case nested: untpd.PackageDef => declaresTopLevel(nested, target)
            case _                        => false

        case _                     => false

    private def isCrossModuleExport(base: String): Boolean =
      val u = base.indexOf('_')
      val d = base.indexOf('.')
      val firstCut = List(u, d).filter(_ >= 0)

      if firstCut.isEmpty then false
      else
        val prefix = base.substring(0, firstCut.min).nn
        // Cross-module export pattern: <other-module>_<this-module>_core or
        // <other-module>.<TypeName>
        prefix.headOption.exists(_.isLower)

  // L3: a type's companion `object` — the most-read API surface — must
  // appear before the `class`/`trait`/`enum` it accompanies.
  object CompanionOrdering extends Rule:
    def id: String = "L3"
    def principle: Principle = Principle.Locatability

    def check(ctx: Context): List[Violation] =
      val out = mutable.ListBuffer[Violation]()

      ctx.companions.objectLines.foreach: (key, objLine) =>
        ctx.companions.typeLines.get(key).foreach: typeLine =>
          if objLine > typeLine then
            val name = key._2
            out +=
              Violation
                ( ctx.file, objLine, 1, "L3",
                  s"object `$name` must appear before class/trait/enum `$name`" )

      out.toList

  // L4: every public module in a component (a top-level definition living in
  // its own `<component>.<Name>.scala` file, other than `internal` modules) must
  // be re-exported into the umbrella package. `siblingTypes` is the list of
  // such module names found alongside the `<umbrella>_<component>_<suffix>.scala`
  // export surface; any name absent from the surface's `export` clauses is a
  // violation. The list is empty for every file other than an export surface, so
  // this check is a no-op elsewhere.
  object UmbrellaExportCompleteness extends Rule:
    def id: String = "L4"
    def principle: Principle = Principle.Locatability

    def check(ctx: Context): List[Violation] =
      val missing =
        ctx.siblingTypes
          .filterNot(ctx.umbrellaExports.names.contains)
          .filterNot(ctx.unexported.contains)

      if missing.nonEmpty then
        val listed  = missing.map { name => s"`$name`" }.mkString(", ")
        val subject = if missing.length == 1 then "module is" else "modules are"

        List
          ( Violation
              ( ctx.file, ctx.umbrellaExports.firstLine, 1, "L4",
                s"the $subject defined but not exported to the " +
                  s"`${ctx.config.umbrella.getOrElse("")}` package: $listed" ) )
      else
        Nil

  // L5: every public top-level extension method in a component must, like
  // its public modules (L4), be re-exported into the umbrella package by
  // its leaf name. `siblingExtensions` is the list of such method names found in
  // the `<component>_<suffix>.scala` definition files alongside the export
  // surface; any name absent from the surface's `export` clauses (and not marked
  // `@unexported`) is a violation. The list is empty for every file other than an
  // export surface, so this check is a no-op elsewhere.
  object ExtensionExportCompleteness extends Rule:
    def id: String = "L5"
    def principle: Principle = Principle.Locatability

    def check(ctx: Context): List[Violation] =
      val missing =
        ctx.siblingExtensions
          .filterNot(ctx.umbrellaExports.names.contains)
          .filterNot(ctx.unexported.contains)

      if missing.nonEmpty then
        val listed  = missing.map { name => s"`$name`" }.mkString(", ")
        val subject = if missing.length == 1 then "extension method is" else "extension methods are"

        List
          ( Violation
              ( ctx.file, ctx.umbrellaExports.firstLine, 1, "L5",
                s"the $subject defined but not exported to the " +
                  s"`${ctx.config.umbrella.getOrElse("")}` package: $listed" ) )
      else
        Nil

  // L6: a name in the umbrella package must come from one component. Two
  // components exporting the same type-level name into the umbrella package,
  // or the same name into a `package <name>:` block (whose namespaces merge
  // across surfaces), leave a reference to it ambiguous. A lowercase name in
  // the umbrella package itself is exempt: it is almost always an extension
  // method, and extension methods of one name from several components are
  // told apart by their receivers (`in`, `from`, `to`).
  //
  // `elsewhere` maps every name the other components' surfaces export to the
  // components exporting it; it is empty for every file other than an export
  // surface, so this check is a no-op elsewhere. Each surface reports its own
  // export, so both ends of a clash are found.
  object UmbrellaNameUniqueness extends Rule:
    def id: String = "L6"
    def principle: Principle = Principle.Locatability

    def check(ctx: Context): List[Violation] =
      if ctx.elsewhere.isEmpty then Nil else
        val umbrella = ctx.config.umbrella.getOrElse("")

        val checked = ctx.exported.filter: exported =>
          exported.scope.nonEmpty || exported.name.headOption.exists(_.isUpper)

        checked.flatMap: exported =>
          ctx.elsewhere.get(exported.qualified).filter(_.nonEmpty).map: components =>
            val (line, column) = position(ctx, exported.offset)
            val listed = components.to(List).sorted.map { c => s"`$c`" }.mkString(", ")
            val target = (umbrella :: exported.scope).mkString(".")

            Violation
              ( ctx.file, line, column, "L6",
                s"`${exported.name}` is also exported to the `$target` package by $listed" )

    private def position(ctx: Context, offset: Int): (Int, Int) =
      if offset < 0 || offset > ctx.source.content.length then (1, 1)
      else (ctx.source.offsetToLine(offset) + 1, ctx.source.column(offset) + 1)
