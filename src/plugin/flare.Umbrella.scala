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

// The umbrella-package analysis behind L4, L5 and L6: what an export surface's
// sibling files declare, so that the surface can be checked for completeness.
// It reads and parses neighbouring `.scala` files itself, with the same
// language features the compilation is running with, so the plugin and the
// `flare` command see the same trees.
//
// Everything here returns empty data for a project without an umbrella
// package, and the umbrella rules are then no-ops.
object Umbrella:
  // What the umbrella rules need to know about `path`'s siblings: the public
  // modules L4 requires to be re-exported, the extension methods L5 requires,
  // the names deliberately excluded from both, and — for L6 — every name the
  // surfaces of *other* components export, qualified by its `package <name>:`
  // blocks, with the components that export it.
  final case class Siblings
    ( modules:    List[String],
      extensions: List[String],
      unexported: Set[String],
      elsewhere:  Map[String, Set[String]] = Map.empty )

  val none: Siblings = Siblings(Nil, Nil, Set.empty)

  def siblings(path: String, config: Config, features: List[String]): Siblings =
    if config.umbrella.isEmpty then none else
      Siblings
        ( siblingModules(path, config),
          siblingExtensions(path, config, features),
          unexported(path, config, features) ++ siblingSurfaceExports(path, config, features),
          if config.omits("L6") then Map.empty else foreignExports(path, config, features) )

  // An export surface: the file that re-exports one component's public modules
  // into the umbrella package. It is named `<umbrella>_<component>_<suffix>
  // .scala` and sits in the component's own source directory, which the
  // `moduleRoot` option identifies (`<moduleRoot>/<component>/…`).
  //
  // Returns the component name when `path` is such a file, and `None`
  // otherwise — which is every file, for a project that sets no `umbrella`.
  //
  // Compiler-plugin source roots (`/src/plugin/`) are excluded: their
  // extraction helpers are deliberately unexported plugin internals.
  def surfaceComponent(path: String, config: Config): Option[String] =
    config.umbrella.flatMap: umbrella =>
      if path.contains("/src/plugin/") then None else
        val parts = path.split(s"/${config.moduleRoot}/").nn

        if parts.length < 2 then None else
          val component = parts(1).nn.split("/").nn(0).nn
          val fileName  = java.io.File(path).getName.nn

          Option.when(fileName.startsWith(s"${umbrella}_${component}_"))(component)

  // The `.scala` files beside `path`, or `Nil` if the directory can't be read.
  private def siblingFiles(path: String): List[java.io.File] =
    val parent = java.io.File(path).getParentFile
    val files  = if parent == null then null else parent.listFiles

    if files == null then Nil else files.toList.map(_.nn).filter(_.getName.nn.endsWith(".scala"))

  // When `path` is an export surface, the names of the sibling public modules
  // (`<component>.<Name>.scala` files in the same directory, excluding any
  // `internal` module) that L4 requires to be re-exported. A module is
  // included only when its file declares `Name` publicly: modules with no
  // accessible top-level declaration (e.g. a `private[component] object`)
  // cannot be — and need not be — re-exported.
  private def siblingModules(path: String, config: Config): List[String] =
    surfaceComponent(path, config).to(List).flatMap: component =>
      val prefix = s"$component."

      siblingFiles(path).flatMap: sibling =>
        val name = sibling.getName.nn

        Option.when(name.startsWith(prefix)):
          val mid = name.substring(prefix.length, name.length - ".scala".length).nn
          mid.takeWhile(_ != '.')

        . filter: module =>
            module.nonEmpty && !module.toLowerCase.nn.contains("internal") &&
              declaresPublicly(sibling, module)

  // When `path` is an export surface, the names of the public top-level
  // extension methods that L5 requires to be re-exported. They live in the
  // sibling `<component>_<suffix>.scala` definition files (e.g.
  // `xylophone_core.scala`) in the same directory; their lowercase
  // `<component>_` prefix distinguishes them from the `<umbrella>_…` and
  // `<other>_<component>_…` export surfaces, which begin with a different
  // prefix. `internal` definition files are skipped.
  private def siblingExtensions(path: String, config: Config, features: List[String])
  :   List[String] =

    surfaceComponent(path, config).to(List).flatMap: component =>
      val prefix = s"${component}_"

      siblingFiles(path).filter: sibling =>
        val name = sibling.getName.nn
        name.startsWith(prefix) && !name.toLowerCase.nn.contains("internal")

      . flatMap(extensionMethods(_, features))

    . distinct

  // Parse `file` and return the leaf names of its public top-level extension
  // methods. Returns `Nil` if the file can't be read or parsed.
  private def extensionMethods(file: java.io.File, features: List[String]): List[String] =
    parseFile(file, features).map: parsed => Extensions.extract(parsed.tree, parsed.source)
    . getOrElse(Nil)

  // When `path` is an export surface, the simple names of every sibling
  // definition annotated `@unexported` — both in the `<component>.<Name>
  // .scala` module files and the `<component>_<suffix>.scala` definition files
  // in the same directory. L4 and L5 treat these names as deliberately
  // excluded from the umbrella package.
  private def unexported(path: String, config: Config, features: List[String]): Set[String] =
    surfaceComponent(path, config).to(Set).flatMap: component =>
      siblingFiles(path).filter: sibling =>
        val name = sibling.getName.nn
        name.startsWith(s"$component.") || name.startsWith(s"${component}_")

      . flatMap(unexportedNames(_, features)).to(Set)

  // Parse `file` and return the simple names of definitions it annotates
  // `@unexported`. Returns the empty set if the file can't be read or parsed.
  private def unexportedNames(file: java.io.File, features: List[String]): Set[String] =
    parseFile(file, features).map: parsed => Annotations.unexported(parsed.tree)
    . getOrElse(Set.empty)

  // When `path` is an export surface, the export names from the *other*
  // surfaces in the same directory. A component split across several surfaces
  // (e.g. a `core`/`parser` split) re-exports each module from exactly one of
  // them; a module exported by a sibling surface is therefore not missing from
  // this one.
  private def siblingSurfaceExports(path: String, config: Config, features: List[String])
  :   Set[String] =

    val fileName = java.io.File(path).getName.nn

    config.umbrella.to(Set).flatMap: umbrella =>
      surfaceComponent(path, config).to(Set).flatMap: component =>
        siblingFiles(path).filter: sibling =>
          val name = sibling.getName.nn
          name != fileName && name.startsWith(s"${umbrella}_${component}_")

        . flatMap(surfaceExportNames(_, features)).to(Set)

  // Parse an export-surface `file` and return the simple names it re-exports.
  private def surfaceExportNames(file: java.io.File, features: List[String]): Set[String] =
    parseFile(file, features)
    . map: parsed => UmbrellaExports.extract(parsed.tree, parsed.source).names
    . getOrElse(Set.empty)

  // When `path` is an export surface, every name the export surfaces of the
  // project's *other* components put into the umbrella package, qualified by
  // the `package <name>:` blocks it sits in, mapped to the components that
  // export it. L6 reports a name this surface exports too.
  //
  // The other surfaces are found where this one is: in each component's
  // directory beneath `<moduleRoot>`, at the same depth as this surface sits
  // in its own (`<moduleRoot>/*/src/*/` for a surface at
  // `<moduleRoot>/<component>/src/core/`).
  private def foreignExports(path: String, config: Config, features: List[String])
  :   Map[String, Set[String]] =

    val found: List[(String, String)] =
      config.umbrella.to(List).flatMap: umbrella =>
        surfaceComponent(path, config).to(List).flatMap: component =>
          val marker = s"/${config.moduleRoot}/"
          val index  = path.indexOf(marker)

          if index < 0 then Nil else
            val modules = path.substring(0, index + marker.length - 1).nn
            val depth   = path.substring(index + marker.length).nn.split("/").nn.length - 2

            surfaces(modules, depth, umbrella).filter(_(0) != component).flatMap: (other, file) =>
              surfaceExported(file, features).map(_ -> other)

    found.groupMap(_(0))(_(1)).view.mapValues(_.to(Set)).toMap

  // Every export surface of every component beneath `modules`, `depth`
  // directories into the component's own directory, with its component.
  // Every surface's check needs the same list, so the scan is cached against
  // the modification times of the directories it read: adding or removing a
  // surface, or a component, changes one of them.
  private final case class Scan
    ( directories: List[(java.io.File, Long)], surfaces: List[(String, java.io.File)] ):
    def current: Boolean = directories.forall: (dir, modified) => dir.lastModified == modified

  private val scanCache = java.util.concurrent.ConcurrentHashMap[String, Scan]()

  private def surfaces(modules: String, depth: Int, umbrella: String)
  :   List[(String, java.io.File)] =

    val key    = s"$modules:$depth:$umbrella"
    val cached = scanCache.get(key)

    if cached != null && cached.current then cached.surfaces else
      val listed = List.newBuilder[(java.io.File, Long)]

      def children(directory: java.io.File): List[java.io.File] =
        listed += directory -> directory.lastModified
        val files = directory.listFiles
        if files == null then Nil else files.toList.map(_.nn).sortBy(_.getName.nn)

      // The surfaces `depth` directories beneath a component's directory,
      // excluding compiler-plugin source roots as `surfaceComponent` does.
      def descend(directory: java.io.File, depth: Int, prefix: String): List[java.io.File] =
        if depth > 0 then
          children(directory).filter(_.isDirectory).flatMap(descend(_, depth - 1, prefix))
        else if directory.getPath.nn.contains("/src/plugin") then
          Nil
        else
          children(directory).filter: file =>
            val name = file.getName.nn
            name.startsWith(prefix) && name.endsWith(".scala") && file.isFile

      val found =
        children(java.io.File(modules)).filter(_.isDirectory).flatMap: directory =>
          val component = directory.getName.nn
          descend(directory, depth, s"${umbrella}_${component}_").map(component -> _)

      scanCache.put(key, Scan(listed.result(), found))
      found

  // The qualified names an export surface exports, cached by path against the
  // file's modification time and size: every surface is read by every other
  // surface's check, so each is parsed once per change rather than once per
  // reader.
  private val exportCache =
    java.util.concurrent.ConcurrentHashMap[String, (Long, Long, List[String])]()

  private def surfaceExported(file: java.io.File, features: List[String]): List[String] =
    val key      = file.getPath.nn
    val modified = file.lastModified
    val size     = file.length

    exportCache.get(key) match
      case (`modified`, `size`, names) => names

      case _ =>
        val names =
          parseFile(file, features).map: parsed =>
            UmbrellaExports.exported(parsed.tree).map(_.qualified).distinct

          . getOrElse(Nil)

        exportCache.put(key, (modified, size, names))
        names

  // Read and parse a sibling file, using the same language features the
  // compilation itself is running with. `None` if it can't be read.
  private def parseFile(file: java.io.File, features: List[String]): Option[Parsing.Parsed] =
    try
      val content = String(java.nio.file.Files.readAllBytes(file.toPath))
      Some(Parsing.parse(file.getPath.nn, content, features))
    catch case _: Throwable => None

  // Modifiers that may precede a public top-level declaration; `private` and
  // `protected` are deliberately absent, so a `private[x] object Foo` line never
  // matches the public-declaration pattern below.
  private val PublicModifiers =
    "(?:final|sealed|abstract|case|open|transparent|inline|erased|lazy|override|implicit)"

  // Does `file` declare `module` with a publicly-accessible top-level definition?
  // A column-0 `object`/`class`/`trait`/`enum`/`type`/`val`/`def`/`given Name`
  // (optionally preceded by non-access modifiers) counts; a `private`/`protected`
  // declaration does not, because the line then begins with that access modifier.
  private def declaresPublicly(file: java.io.File, module: String): Boolean =
    val keywords = "opaque\\s+type|object|class|trait|enum|type|val|def|given"
    val pattern  = s"(?m)^(?:$PublicModifiers\\s+)*(?:$keywords)\\s+$module(?![A-Za-z0-9])"

    try
      val content = String(java.nio.file.Files.readAllBytes(file.toPath))
      pattern.r.findFirstIn(content).isDefined
    catch case _: Throwable => true
