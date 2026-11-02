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

import scala.collection.immutable as sci

import soundness.*
import alphabets.hexLowerCase
import dysasymptotics.linearSize
import providers.javaBaseProvider
import pyrocosm.Filing

// One assessment, as `flare assess` runs it: the compiler over a profile's files, the candidate
// extractor over each unit, then — for every definition without a valid judgement already in
// the notes — the model, in batches; then scores, bands and the ranking, recorded on the input
// tree. Judgements are keyed by the definition's digest (the blob hash of its normalised text),
// so a definition is judged once for as long as its text, the rubric, the criteria and the
// model stand.
object Assessing:
  private type Found = List[Candidates.Candidate]

  case class Scored
    ( candidate:  Candidates.Candidate,
      digest:     Text,
      judgement:  Verdicts.Judgement,
      score:      Int,
      band:       Text,
      duplicates: Int )

  case class Outcome
    ( assessment:  Verdicts.Assessment,
      scored:      List[Scored],
      candidates:  List[(Candidates.Candidate, Text)],
      fresh:       Int,
      reused:      Int,
      unjudged:    Int,
      parseErrors: List[flare.Frontend.Diagnostic],
      recorded:    Optional[Boolean] )

  // The extractor's filters, from the rule: an unset list keeps the extractor's default.
  def filters(rule: Workspace.Assessment): Candidates.Filters =
    val defaults = Candidates.Filters()

    def admission(kind: Text): Optional[Workspace.Admission] =
      rule.admissions.filter(_.kind == kind).head

    def bound(kind: Text): Option[Int] =
      admission(kind).let { (a: Workspace.Admission) => a.bound.or(0) }.option

    val gates =
      if rule.admissions.nil then defaults.gates
      else
        Candidates.Gates
          ( conversion = admission(t"conversion").present,
            ladder     = bound(t"ladder"),
            roundTrip  = admission(t"round-trip").present,
            adapter    = bound(t"adapter"),
            verb       = bound(t"verb") )

    val kinds: sci.Set[String] =
      if rule.kinds.nil then defaults.kinds else rule.kinds.map(_.s).stdlib.toSet

    val names: sci.List[String] =
      if rule.excludeNames.nil then defaults.excludeNames else rule.excludeNames.map(_.s).stdlib

    val bodies: sci.List[String] =
      if rule.excludeBodies.nil then defaults.excludeBodies else rule.excludeBodies.map(_.s).stdlib

    Candidates.Filters(rule.lines, kinds, names, bodies, rule.noUsing || rule.admissions.nil, gates)

  // The criteria, digested: a changed weight or wording re-judges as a changed rubric does.
  def criteriaDigest(rule: Workspace.Assessment): Text =
    val lines: List[Text] =
      rule.criteria.map: (c: Workspace.Criterion) => t"${c.id} ${c.weight.show} ${c.text}"

    lines.join(t"\n").digest[Sha2[256]].serialize[Hex]

  // Whether a `calls` signal's value — `1`, `2`, `4+` — describes the candidate's call count.
  private def calls(value: Text, count: Int): Boolean =
    if value.ends(t"+") then safely(value.keep(value.length - 1).as[Int]).lay(false)(count >= _)
    else safely(value.as[Int]).lay(false)(count == _)

  // The points the driver adds for what it can see itself.
  def signals(rule: Workspace.Assessment, candidate: Candidates.Candidate, duplicates: Int): Int =
    val points: List[Int] = rule.signals.map: (signal: Workspace.Signal) =>
      val applies: Boolean = signal.kind match
        case t"local" | t"private" | t"protected" | t"public" | t"conversion" =>
          candidate.kind.word.tt == signal.kind

        case t"calls"     => signal.value.lay(false)(calls(_, candidate.calls))
        case t"duplicate" => duplicates > 0
        case t"one-liner" => candidate.oneLiner
        case _            => false

      if applies then signal.points else 0

    points.stdlib.sum

  def score
    ( rule:       Workspace.Assessment,
      judgement:  Verdicts.Judgement,
      candidate:  Candidates.Candidate,
      duplicates: Int )
  :   Int =

    val answered: List[Int] = judgement.signs.filter(_.value).map: sign => rule.weight(sign.id)
    answered.stdlib.sum + signals(rule, candidate, duplicates)

  // A verdict the model gave, checked against the rule: every configured criterion must be
  // answered (a missing one is `false`, and marks the judgement incomplete), and the
  // replacement must come from the vocabulary (`none` otherwise, marked likewise).
  def judgement
    ( rule:      Workspace.Assessment,
      model:     Workspace.Model,
      rubric:    Rubric,
      criteria:  Text,
      measured:  Text,
      candidate: Candidates.Candidate,
      digest:    Text,
      verdict:   Oracle.Verdict )
  :   Verdicts.Judgement =

    val answered: Map[Text, Boolean] = Map.from(verdict.signs.map { s => (s.id, s.value) }.stdlib)
    val missing: Boolean = rule.criteria.exists: c => answered.at(c.id).absent

    val signs: List[Verdicts.Sign] = rule.criteria.map: c =>
      Verdicts.Sign(c.id, answered.at(c.id).or(false))

    val allowed: Boolean =
      verdict.replacement == t"none" || rule.vocabulary.has(verdict.replacement)

    Verdicts.Judgement
      ( rule.name, model.model, rubric.digest, criteria, Flare.version, measured, digest,
        candidate.kind.word.tt, candidate.calls, candidate.oneLiner, signs,
        if allowed then verdict.replacement else t"none", verdict.reason, missing || !allowed )

  // The lines the command prints: one per scored candidate, worst first; the stored form; a
  // candidate a dry run found; band totals; per-criterion counts; the closing summary.
  def line(s: Scored): Text =
    val locus = s.candidate.locus.tt
    t"${s.score.show}\t${s.band}\t$locus\t${s.judgement.replacement}\t${s.judgement.reason}"

  def line(r: Verdicts.Ranked): Text =
    t"${r.score.show}\t${r.band}\t${r.locus}\t${r.replacement}\t${r.digest.keep(12)}"

  def line(candidate: Candidates.Candidate, digest: Text): Text =
    t"${candidate.kind.word.tt}\t${digest.keep(12)}\t${candidate.locus.tt}"

  def heading(a: Verdicts.Assessment): Text =
    val rubric = a.rubric.keep(12)
    t"rule ${a.rule}; model ${a.model}; rubric $rubric; measured ${a.measured}; tree ${a.tree}"

  def bandLines(a: Verdicts.Assessment): List[Text] =
    a.bands.map: (count: Verdicts.Count) => t"${count.band}\t${count.count.show}"

  def criterionLines(rule: Workspace.Assessment, scored: List[Scored]): List[Text] =
    rule.criteria.map: (criterion: Workspace.Criterion) =>
      def affirmed(sign: Verdicts.Sign): Boolean = sign.id == criterion.id && sign.value
      val trues: Int = scored.filter(_.judgement.signs.exists(affirmed(_))).size
      t"${criterion.id}\t${trues.show}\t${criterion.text}"

  def summary(rule: Workspace.Assessment, outcome: Outcome, rubric: Rubric): Text =
    val counts =
      t"${outcome.fresh.show} judged now, ${outcome.reused.show} reused, " +
        t"${outcome.unjudged.show} unjudged"

    val rubricDigest = rubric.digest.keep(12)
    t"flare: ${rule.name}: ${outcome.scored.size.show} scored ($counts); rubric $rubricDigest"

  def run
    ( config:     Workspace.Config,
      profile:    Workspace.Profile,
      rule:       Workspace.Assessment,
      model:      Workspace.Model,
      rubric:     Rubric,
      repository: Repository,
      files:      List[Text],
      oracle:     (Text -> Text) => Oracle.Oracle,
      limit:      Optional[Int],
      force:      Boolean,
      dryRun:     Boolean,
      progress:   Text => Unit )
    ( using WorkingDirectory )
  :   Outcome =

    val criteria: Text = criteriaDigest(rule)
    val measured: Text = java.time.Instant.now().nn.toString.tt
    val admit = filters(rule)
    val result = flare.Frontend.run(files, profile.languages, t"parser"): (phase: String) => ()

    def relative(path: Text): Text = Filing.relative(config.root, path)

    // Every candidate in every unit, its path made root-relative so a locus is portable.
    val all: List[Candidates.Candidate] =
      result.units.bind[Found, Candidates.Candidate, Found]: (unit: flare.Frontend.Parsed) =>
        val path = relative(unit.path).s
        List.from(Candidates.collect(path, unit.text.s, unit.tree, unit.source, admit))

    progress(t"${all.size.show} candidates in ${result.units.size.show} files")

    val twins: Map[String, Int] = Map.from(Candidates.duplicates(all.stdlib))

    // The digests: each distinct normalised text written once as a blob.
    val texts: List[String] = List.from(all.map(_.normalised).stdlib.distinct)

    val digestOf: Map[String, Text] =
      Map.from:
        texts.stdlib.flatMap: text =>
          repository.writeBlob(text.tt).option.map: digest => (text, digest)

    def digest(candidate: Candidates.Candidate): Text = digestOf.at(candidate.normalised).or(t"")
    def duplicates(candidate: Candidates.Candidate): Int = twins.at(candidate.normalised).or(0)

    // Trials: the candidates with the highest driver score first.
    val chosen: List[Candidates.Candidate] = limit.lay(all): (n: Int) =>
      List.from(all.stdlib.sortBy { c => -signals(rule, c, duplicates(c)) }.take(n))

    val withDigests: List[(Candidates.Candidate, Text)] =
      chosen.map { c => (c, digest(c)) }.filter(_(1) != t"")

    // Judgements already in the notes, by digest — unless forced.
    val stored: Map[Text, Verdicts.Judgement] =
      if force then Map()
      else
        Map.from:
          withDigests.map(_(1)).stdlib.distinct.flatMap: (d: Text) =>
            val found =
              Recording.judgement(repository, rule.name, d, rubric.digest, criteria, model.model)

            found.option.map: j => (d, j)

    val pending: List[Candidates.Candidate] =
      val unknown = withDigests.filter: (c, d) => stored.at(d).absent
      List.from(unknown.stdlib.distinctBy(_(1)).map(_(0)))

    progress(t"${stored.size.show} judgements reused; ${pending.size.show} definitions to judge")

    val batches: List[List[Candidates.Candidate]] =
      List.from(pending.stdlib.grouped(model.batch.max(1)).map(List.from(_)))

    val judged: Map[Text, Verdicts.Judgement] =
      if dryRun || pending.nil then Map()
      else
        val outcomes = oracle { text => digestOf.at(text.s).or(t"") }.judge(batches)

        Map.from:
          outcomes.stdlib.flatMap: (outcome: Oracle.Outcome) =>
            outcome.result match
              case answer: Oracle.Answer =>
                val verdicts: Map[Text, Oracle.Verdict] =
                  Map.from(answer.verdicts.map { v => (v.id, v) }.stdlib)

                outcome.batch.stdlib.flatMap: (candidate: Candidates.Candidate) =>
                  val d = digest(candidate)

                  verdicts.at(d).option.map: (verdict: Oracle.Verdict) =>
                    (d, judgement(rule, model, rubric, criteria, measured, candidate, d, verdict))

              case error: Llm.Error =>
                progress(t"a batch failed: ${error.message}")
                Nil

    val recordedJudgements: Boolean =
      dryRun || judged.values.stdlib.forall: j => Recording.record(repository, j)

    val known: Map[Text, Verdicts.Judgement] = Map.from(stored.stdlib ++ judged.stdlib)

    val unsorted: List[Scored] = withDigests.bind: (candidate, d) =>
      known.at(d).lay(Nil: List[Scored]): (j: Verdicts.Judgement) =>
        val total = score(rule, j, candidate, duplicates(candidate))
        List(Scored(candidate, d, j, total, rule.band(total), duplicates(candidate)))

    val scored: List[Scored] =
      List.from(unsorted.stdlib.sortBy { s => (-s.score, s.candidate.locus) })

    val unjudged: Int = withDigests.size - scored.size

    val bands: List[Verdicts.Count] =
      List.from:
        scored.stdlib.groupBy(_.band).toList.sortBy(_(0).s).map: (band, entries) =>
          Verdicts.Count(band, entries.length)

    val ranked: List[Verdicts.Ranked] = scored.map: (s: Scored) =>
      Verdicts.Ranked
        ( s.candidate.locus.tt, s.candidate.path.tt, s.candidate.line, s.candidate.name.tt,
          s.candidate.kind.word.tt, s.digest, s.score, s.band, s.judgement.replacement,
          s.duplicates )

    val tree: Text = repository.inputTree(files).or(t"")
    val head: Text = repository.head().or(t"")

    val assessment =
      Verdicts.Assessment
        ( rule.name, model.model, rubric.digest, criteria, Flare.version, measured, tree, head,
          repository.dirty(), files.size, withDigests.size, batches.size, Verdicts.Usage(0, 0, 0),
          bands, ranked )

    val recorded: Optional[Boolean] =
      if dryRun || tree == t"" then Unset
      else
        val treeNote = Recording.record(repository, assessment)
        val namespace = Recording.namespace(rule.name)

        val pointer =
          if head == t"" then true
          else repository.appendNote(namespace, head, Recording.pointer(assessment))

        recordedJudgements && treeNote && pointer

    Outcome
      ( assessment, scored, withDigests, judged.size, stored.size, unjudged,
        result.diagnostics.filter(_.error), recorded )
