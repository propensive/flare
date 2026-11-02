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

import soundness.*

import codepages.utf8Codepage
import pyrocosm.{Fingerprint, Notes}

// The census, and where it goes. `flare metrics` counts every project rule's matches and every
// gate's uses across a profile's sources, and records the result in git notes so that the counts
// can be followed commit by commit:
//
//  - the census itself is a note on the git TREE object of exactly the input files (made with a
//    temporary index and `write-tree`), so that identical inputs across commits share one
//    measurement and are never measured twice;
//  - a pointer note on the HEAD commit names the profile, the tree and the time, which is what a
//    walk of the history follows to plot a series.
//
// Both live under `refs/notes/<namespace>` (`flare` unless the profile says otherwise), and are
// pushed and fetched like any notes ref: `git push origin refs/notes/flare`.
//
// The input tree is a dangling object — no commit refers to it — so `git gc` may prune it once
// it expires. The note survives (it lives in the notes ref), so a stored census is read back
// through the notes ref's own tree, never by asking git to resolve the input tree again.
object Census:
  case class Measurement
    ( profile:  Text,
      tree:     Text,
      commit:   Text,
      dirty:    Boolean,
      measured: Text,
      files:    Int,
      totals:   List[(Text, Int)],
      perFile:  List[(Text, List[(Text, Int)])] )

  // The census note, as TEL: totals first, then each file's counts, so a reader — or a chart —
  // that wants only the totals need not read the rest.
  def body(m: Measurement): Text =
    val fixed: List[Text] =
      List
        ( t"tel 1.0", t"", t"profile ${m.profile}", t"tree ${m.tree}", t"commit ${m.commit}",
          t"measured ${m.measured}", t"flare ${Flare.version}", t"files ${m.files}" )

    val head: List[Text] = if m.dirty then fixed + List(t"dirty") else fixed

    val totals: List[Text] =
      m.totals.map: (indicator: Text, total: Int) => t"total $indicator $total"

    val files: List[Text] =
      m.perFile.bind[List[Text], Text, List[Text]]: (file: Text, counts: List[(Text, Int)]) =>
        List(t"file $file") + counts.map: (indicator: Text, count: Int) => t"  $indicator $count"

    (head + List(t"") + totals + List(t"") + files).join(t"\n") + t"\n"

  // The pointer note appended to the commit: one `measurement` per profile measured.
  def pointer(m: Measurement): Text =
    val fixed: List[Text] =
      List
        ( t"measurement", t"  profile ${m.profile}", t"  tree ${m.tree}",
          t"  measured ${m.measured}" )

    val lines: List[Text] = if m.dirty then fixed + List(t"  dirty") else fixed
    lines.join(t"\n") + t"\n"

  // A census read back from the notes: the commit it was recorded on, when that commit was
  // made, the tree it measured, and its totals.
  case class Record(commit: Text, time: Long, tree: Text, files: Int, totals: List[(Text, Int)])

  // The totals of a census note's body: its `files` line and its `total <indicator> <n>`
  // lines. Each is one keyword and its atoms, so the lines are split rather than parsed as
  // TEL, whose reader exposes only a compound's first atom.
  def totalsOf(body: Text): (Int, List[(Text, Int)]) =
    val lines: List[Text] = body.cut(t"\n").map(_.trim)

    val files: Int =
      lines.filter(_.starts(t"files ")).head.let: (line: Text) => safely(line.skip(6).trim.as[Int])
      . or(0)

    val totals: List[(Text, Int)] =
      lines.filter(_.starts(t"total "))
      . bind[List[(Text, Int)], (Text, Int), List[(Text, Int)]]: (line: Text) =>
          line.skip(6).trim.cut(t" ") match
            case indicator :: count :: _ =>
              safely(count.as[Int]).lay(Nil: List[(Text, Int)]): (n: Int) => List((indicator, n))

            case _ => Nil

    (files, totals)

  // The tree a commit's pointer note names for a profile, from the `measurement` entries
  // `flare metrics` appends; `Unset` when the commit carries no pointer, or none for this
  // profile.
  def treeFor(repository: Repository, namespace: Text, profile: Text, commit: Text)
    ( using WorkingDirectory )
  :   Optional[Text] =

    repository.note(namespace, commit).let: (pointer: Text) =>
      safely(pointer.read[Tel]).let: (tel: Tel) =>
        tel.fields(t"measurement").to[List]
        . filter: (m: Tel) => m.field(t"profile").let(_.primaryAtom) == profile
        . bind[List[Text], Text, List[Text]]: (m: Tel) =>
            m.field(t"tree").lay(Nil: List[Text]): (tree: Tel) => List(tree.primaryAtom)

        . head

  // The censuses recorded for a profile along the history behind HEAD, oldest first: each of
  // the latest `limit` commits that carries a pointer note, resolved to the measurement it
  // names. A commit whose measurement has been lost is passed over.
  def history(repository: Repository, namespace: Text, profile: Text, limit: Int)
    ( using WorkingDirectory )
  :   List[Record] =

    val noted: List[Text] = repository.noted(namespace)

    val records: List[Record] =
      repository.log(limit).filter: (entry: Repository.Entry) => noted.exists(_ == entry.hash)
      . bind[List[Record], Record, List[Record]]: (entry: Repository.Entry) =>
          treeFor(repository, namespace, profile, entry.hash).let: (tree: Text) =>
            repository.storedNote(namespace, tree).let: (body: Text) =>
              val (files, totals) = totalsOf(body)
              List(Record(entry.hash, entry.time, tree, files, totals))

          . or(Nil)

    val oldestFirst: List[Record] = List.from(records.stdlib.reverse)
    oldestFirst

  // Sum the per-file counts into totals, sorted by indicator.
  def totals(perFile: List[(Text, List[(Text, Int)])]): List[(Text, Int)] =
    val all: List[(Text, Int)] =
      perFile.bind[List[(Text, Int)], (Text, Int), List[(Text, Int)]](_(1))

    val indicators: List[Text] = List.from(all.map(_(0)).distinct.stdlib.sortBy(_.s))

    indicators.map: (indicator: Text) =>
      (indicator, all.filter(_(0) == indicator).map(_(1)).stdlib.sum)

object Repository:
  case class Entry(hash: Text, time: Long)

  // The repository containing `root`, or `Unset` when it is not inside a git work tree.
  def locate(root: Text): Optional[Repository] = Notes.locate(root).let(Repository(_))

  // Flare's notes live under `refs/notes/pyrocosm/`, as every Pyrocosm tool's do, one kind per
  // namespace: the default census namespace `flare` is the kind `flare-census`, a profile's own
  // is itself, and a rule's `flare-assess/<rule>` is `flare-assess-<rule>`. Each was kept at
  // `refs/notes/<namespace>` before, which is still read, so that a census recorded by an
  // earlier flare is not lost.
  def kind(namespace: Text): Text =
    if namespace == t"flare" then t"flare-census" else namespace.sub(t"/", t"-")

  def legacy(namespace: Text): Text = t"refs/notes/$namespace"

// The git repository a project root sits in, and the operations the census needs of it, each
// Pyrocosm's `Notes`.
class Repository(notes: Notes):
  import Repository.{kind, legacy}

  private def fingerprint(hash: Text): Fingerprint = Fingerprint.unsafe(hash)

  def head(): Optional[Text] = safely(notes.head()).or(Unset).let(_.text)

  // Whether any tracked file differs from HEAD: a measurement of a dirty tree is still a
  // measurement, but the pointer note should say so.
  def dirty(): Boolean = safely(notes.dirty()).or(false)

  // The tree object of exactly `files`, written under a throwaway index so the real index and the
  // work tree are untouched.
  def inputTree(files: List[Text]): Optional[Text] = safely(notes.tree(files)).let(_.text)

  // The latest `limit` commits behind HEAD, newest first, with their commit times.
  def log(limit: Int): List[Repository.Entry] =
    safely(notes.log(t"HEAD", limit)).or(Nil).map: (commit, time) =>
      Repository.Entry(commit.text, time)

  // The objects annotated under the namespace, where it is now and where it was.
  def noted(namespace: Text): List[Text] =
    val current: List[Text] = safely(notes.noted(kind(namespace))).or(Nil).map(_.text)
    val earlier: List[Text] = safely(notes.notedAt(legacy(namespace))).or(Nil).map(_.text)
    List.from((current.stdlib ++ earlier.stdlib).distinct)

  def resolve(refspec: Text): Optional[Text] = safely(notes.resolve(refspec)).or(Unset).let(_.text)
  def objectType(hash: Text): Optional[Text] = safely(notes.objectType(hash)).or(Unset)

  // A note's body, if the object has one under the namespace: through the notes ref's own tree
  // where the annotated object has been pruned, and at the namespace's earlier ref otherwise.
  def noteData(namespace: Text, hash: Text): Optional[Data] =
    safely(notes.note(kind(namespace), fingerprint(hash))).or(Unset)
    . or(safely(notes.noteAt(legacy(namespace), fingerprint(hash))).or(Unset))

  def note(namespace: Text, hash: Text): Optional[Text] = noteData(namespace, hash).let(_.utf8)
  def storedNote(namespace: Text, hash: Text): Optional[Text] = note(namespace, hash)

  def addNote(namespace: Text, hash: Text, body: Text, force: Boolean): Boolean =
    if !force && note(namespace, hash).present then false
    else safely(notes.write(kind(namespace), fingerprint(hash), body.sysData)).present

  def appendNote(namespace: Text, hash: Text, body: Text): Boolean =
    safely(notes.append(kind(namespace), fingerprint(hash), body)).present

  // Writes `data` into the object store as a blob and returns its hash: how a definition's
  // normalised text becomes its digest.
  def writeBlob(data: Data): Optional[Text] = safely(notes.blob(data)).or(Unset).let(_.text)
  def writeBlob(text: Text): Optional[Text] = writeBlob(text.sysData)

  // A note whose body is any bytes (BinTEL), which `Notes` stages as a blob.
  def addBinaryNote(namespace: Text, target: Text, data: Data): Boolean =
    safely(notes.write(kind(namespace), fingerprint(target), data)).present
