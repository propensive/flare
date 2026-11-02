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

import java.util.concurrent as juc

import soundness.*
import dysasymptotics.linearSize
import pyrocosm.{Action, Activity, Block, Control, Event, Hints, Inline, Interface, Live, Panel}
import pyrocosm.{Timing, Tone, Tool, WebService, hints}

// The dashboard as the web front-end `Tool` serves from the daemon: launched once, for as long
// as the daemon lives, when a config says `serve`, and stopped by `flare quit`. It is served
// behind the machine's Pyrocosm hub, at `/flare/` on the hub's origin (8090), from a port of its
// own the hub reaches it by; a `port` in a config serves it on that port too. `flare serve`
// remains the interactive way to serve it, from a terminal.
object Dashboard:
  // Where the dashboard on `port` is found.
  def address(port: Int): Text = Flare.address(port)

  // Served until `stop`, as every tool's web front-end is (`WebService`): the ticker drives the
  // dashboard a few times a second for as long as the front-end runs, which is what notices a
  // project newly registered, runs a requested check, and reads a selected profile's census;
  // nothing runs on the front-end's threads.
  val web: Tool.Web = WebService(): () =>
    val dashboard = Dashboard()
    WebService.Session(dashboard.interface, dashboard.handle, () => dashboard.tick())

  // The projects the daemon has seen, by root: every invocation registers the configuration it
  // loads, so the dashboard offers each project's profiles whichever shell flare was run from,
  // and a configuration edited since is replaced by the next invocation, or by a check.
  private val projects: juc.ConcurrentHashMap[Text, Workspace.Config] = juc.ConcurrentHashMap()

  def register(config: Workspace.Config): Unit = projects.put(config.root, config)

  // Bumped whenever a census may have been recorded — by `flare metrics`, in any shell — so
  // that a dashboard showing that profile reads it again.
  private val edition0: Atomic[Long] = Atomic(0L)
  def edition: Long = edition0()
  def measured(): Unit = edition0.since(_ + 1L)

  def known: List[Workspace.Config] =
    val iterator = projects.values.nn.iterator.nn

    def recur(acc: List[Workspace.Config]): List[Workspace.Config] =
      if iterator.hasNext then recur(acc + List(iterator.next.nn)) else acc

    List.from(recur(Nil).stdlib.sortBy(_.root.s))

// The dashboard: every known project's profiles to choose from; the chosen profile's latest
// check — its findings as they arrive, then filtered by rule — and its summary: the rules that
// fired, and the census `flare metrics` has recorded for it, commit by commit.
final class Dashboard():
  // A profile in a project, as the navigation offers it.
  private case class Target(root: Text, profile: Text)

  private case class Checked
    ( config:   Workspace.Config,
      profile:  Workspace.Profile,
      outcome:  Checking.Outcome,
      finished: Long )

  private val profileActions: juc.ConcurrentHashMap[Target, Action] = juc.ConcurrentHashMap()
  private val ruleActions: juc.ConcurrentHashMap[Text, Action] = juc.ConcurrentHashMap()
  private val checkAction: Action = Action(t"check")

  @scala.caps.unsafe.untrackedCaptures
  @volatile
  private var selected: Optional[Target] = Unset

  @scala.caps.unsafe.untrackedCaptures
  @volatile
  private var filter: Optional[Text] = Unset

  @scala.caps.unsafe.untrackedCaptures
  @volatile
  private var requested: Boolean = false

  @scala.caps.unsafe.untrackedCaptures
  @volatile
  private var running: Boolean = false

  @scala.caps.unsafe.untrackedCaptures
  @volatile
  private var checked: Optional[Checked] = Unset

  // The census read for the selected profile, so that git is asked once per selection, and
  // again only when a measurement may have been recorded since.
  @scala.caps.unsafe.untrackedCaptures
  @volatile
  private var census: Optional[(Target, Long, List[Census.Record])] = Unset

  @scala.caps.unsafe.untrackedCaptures
  private var lastNavigation: List[Block] = Nil

  @scala.caps.unsafe.untrackedCaptures
  private var lastSummary: List[Block] = Nil

  val navigation: Live[List[Block]] = Live(Nil)

  val findings: Live[List[Block]] =
    Live(List(Block.paragraph(t"Choose a profile and press Check.")))

  val summary: Live[List[Block]] = Live(Nil)
  val enabled: Live[Boolean] = Live(true)

  val interface: Interface =
    Interface
      ( Inline.text(t"flare"),
        List
          ( Panel
              ( Panel.Id(t"profiles"),
                Panel.Role.Navigation,
                Inline.text(t"Profiles"),
                navigation,
                Panel.Priority.Important ),
            Panel
              ( Panel.Id(t"findings"),
                Panel.Role.Primary,
                Unset,
                findings,
                Panel.Priority.Essential,
                hints = Hints(hints.Follow) ),
            Panel
              ( Panel.Id(t"summary"),
                Panel.Role.Primary,
                Inline.text(t"Summary"),
                summary,
                Panel.Priority.Important ) ),
        controls = List(Control.Button(Inline.text(t"Check"), checkAction, enabled)) )

  private def profileAction(target: Target): Action =
    profileActions.computeIfAbsent(target, _ => Action(t"${target.root}:${target.profile}")).nn

  private def ruleAction(rule: Text): Action =
    ruleActions.computeIfAbsent(rule, _ => Action(rule)).nn

  private def keyOf[key](map: juc.ConcurrentHashMap[key, Action], action: Action): Optional[key] =
    val entries = map.entrySet.nn.iterator.nn

    def recur(): Optional[key] =
      if !entries.hasNext then Unset else
        val entry = entries.next.nn
        if entry.getValue == action then entry.getKey.nn else recur()

    recur()

  def handle(event: Event): Unit = event match
    case Event.Pressed(action) if action == checkAction =>
      if !running then requested = true

    case Event.Pressed(action) =>
      keyOf(profileActions, action) match
        case target: Target =>
          selected = target
          filter = Unset
          showFindings()
          refresh()

        case _ =>
          keyOf(ruleActions, action).let: (rule: Text) =>
            filter = if filter == rule then Unset else rule
            showFindings()
            refresh()

    case _ =>
      ()

  // The serving loop's beat: a selection where there is none yet, a requested check, the
  // selection's census, and whatever the navigation and summary must now show.
  def tick(): Unit =
    if selected.absent then
      Dashboard.known.head.let: (config: Workspace.Config) =>
        config.profiles.head.let: (profile: Workspace.Profile) =>
          selected = Target(config.root, profile.name)

    if requested && !running then
      requested = false
      selected.let(check(_))

    selected.let: (target: Target) =>
      val edition: Long = Dashboard.edition

      val current: Boolean =
        census.lay(false): (entry: (Target, Long, List[Census.Record])) =>
          entry(0) == target && entry(1) == edition

      if !current then census = (target, edition, history(target))

    refresh()

  // Rebuilds the navigation and the summary, assigning each only when it has changed, so a
  // quiet dashboard sends nothing.
  private def refresh(): Unit = synchronized:
    val navigation0 = navigationBlocks()

    if navigation0 != lastNavigation then
      lastNavigation = navigation0
      navigation() = navigation0

    val summary0 = summaryBlocks()

    if summary0 != lastSummary then
      lastSummary = summary0
      summary() = summary0

  private def failure(message: Text): List[Block] =
    List(Block.Paragraph(List(Inline.Toned(Tone.Failure, Inline.text(message)))))

  // The check in flight, as the interface's one activity: its gauge, captioned by the stage.
  private def progress(target: Target, gauge: pyrocosm.Status, caption: Text): Unit =
    val label = Inline.text(t"Checking ${target.profile}")
    interface.activities() = List(Activity(t"check", label, gauge, state = Inline.text(caption)))

  // Checks the profile, the findings scrolling into the primary panel as they are found under
  // a gauge in the masthead, then shows the whole outcome at the head of the findings. The
  // configuration is reloaded for the check, so an edit since the project was registered is
  // honoured.
  private def check(target: Target): Unit =
    running = true
    enabled() = false
    filter = Unset
    findings() = Nil
    progress(target, pyrocosm.Status.Indeterminate(), t"Starting")

    try
      Workspace.load(target.root) match
        case Workspace.Outcome.Loaded(config) =>
          Dashboard.register(config)

          config.profile(target.profile) match
            case profile: Workspace.Profile =>
              val plugin = config.pluginConfig(profile)

              if !plugin.errors.nil then
                val heading = Inline.text(t"Profile `${profile.name}` could not be used")
                val reasons = plugin.errors.map(Block.paragraph)
                findings() = List(Block.Notice(Tone.Failure, heading, reasons))
              else
                val files = safely(Sources.expand(config.root, profile, Nil)).or(Nil)

                val sink: Checking.Sink = new Checking.Sink:
                  def aborted: Boolean = false

                  def gauge(status0: pyrocosm.Status, caption: Text): Unit =
                    progress(target, status0, caption)

                  def found(finding: Report.Finding, text: Text): Unit =
                    findings.append(Report.excerpt(config.root, finding, text))

                val outcome = Checking.run(profile, plugin.config, files, sink)
                checked = Checked(config, profile, outcome, java.lang.System.currentTimeMillis)
                showFindings()

            case _ =>
              findings() =
                failure(t"The profile `${target.profile}` is no longer defined in ${target.root}")

        case Workspace.Outcome.Missing =>
          findings() = failure(t"No .pyrocosm/flare/config.tel was found at ${target.root}")

        case Workspace.Outcome.Invalid(file, errors) =>
          val heading = Inline.text(t"$file could not be used")
          findings() = List(Block.Notice(Tone.Failure, heading, errors.map(Block.paragraph)))

    catch case error: Exception =>
      val reason = Optional(error.getMessage).let(_.tt).or(error.getClass.getName.nn.tt)
      findings() = failure(t"The check failed: $reason")

    finally
      interface.activities() = Nil
      running = false
      enabled() = true

  // The findings of the latest check, every one or those under the filtering rule, headed by
  // the outcome in a line: how many findings, in how many files, of which profile, when.
  private def showFindings(): Unit = checked.let: (last: Checked) =>
    val texts: Map[Text, Text] = last.outcome.texts
    val all: List[Report.Finding] = last.outcome.all
    val shown: List[Report.Finding] = filter.lay(all): (rule: Text) => all.filter(_.rule == rule)

    val listed: List[Block] =
      if !shown.nil then
        shown.map: (finding: Report.Finding) =>
          Report.excerpt(last.config.root, finding, texts.at(finding.path).or(t""))
      else
        val message: Text =
          filter.lay(t"No violations in ${last.outcome.files.show} files"): (rule: Text) =>
            t"Nothing under the rule $rule"

        List(Block.Paragraph(List(Inline.Toned(Tone.Success, Inline.text(message)))))

    findings() = outcome(last) :: listed

  private def outcome(last: Checked): Block =
    val all: List[Report.Finding] = last.outcome.all

    val tone: Tone =
      if last.outcome.errors then Tone.Failure else if all.nil then Tone.Success else Tone.Warning

    val verdict: Text =
      if all.nil then t"No violations" else if last.outcome.errors then t"Errors" else t"Warnings"

    Block.Paragraph
      ( List
          ( Inline.Toned(tone, Inline.text(verdict)),
            Inline.Textual(t"  "),
            Inline.Numeral(all.size.toDouble, 0),
            Inline.Textual(t" findings in "),
            Inline.Numeral(last.outcome.files.toDouble, 0),
            Inline.Textual(t" files · ${last.profile.name} · ${when(last.finished)}") ) )

  private def history(target: Target): List[Census.Record] =
    given WorkingDirectory = () => target.root

    val none: List[Census.Record] = Nil

    Dashboard.known.seek(_.root == target.root)
    . lay(none): (config: Workspace.Config) =>
        config.profile(target.profile).lay(none): (profile: Workspace.Profile) =>
          Repository.locate(config.root).lay(none): (repository: Repository) =>
            Census.history(repository, profile.notes.or(t"flare"), profile.name, 200)

  // When something happened, as every tool says it (`Timing.ago`).
  private def when(millis: Long): Text = Timing.ago(Instant.of[Unix](millis))
  private def dated(millis: Long): Text = when(millis)

  private def marked(chosen: Boolean, label: Text): List[Inline] =
    if chosen then List(Inline.Toned(Tone.Accent, List(Inline.Emphasis(Inline.text(label)))))
    else Inline.text(label)

  // Every project's profiles, the selected one marked, then the rules the latest check
  // found, each with its count, the filtering one marked.
  private def navigationBlocks(): List[Block] =
    val known: List[Workspace.Config] = Dashboard.known

    val projects: List[Block] =
      if known.nil
      then List(Block.paragraph(t"No project yet: run flare in one, and its profiles appear here."))
      else known.bind[List[Block], Block, List[Block]]: (config: Workspace.Config) =>
        val name: Text = Text(config.root.s.substring(config.root.s.lastIndexOf('/') + 1).nn)

        val items: List[Block.Item] =
          config.profiles.map: (profile: Workspace.Profile) =>
            val target = Target(config.root, profile.name)
            val label = marked(selected == target, profile.name)
            Block.Item(List(Block.Paragraph(label)), profileAction(target))

        List(Block.Heading(3, Inline.text(name)), Block.Listing(false, items))

    val rules: List[Block] = checked.lay(Nil: List[Block]): (last: Checked) =>
      val all: List[Report.Finding] = last.outcome.all

      val tallies =
        all.map(_.rule).distinct.map: (rule: Text) => (rule, all.filter(_.rule == rule).size)

      val counts: List[(Text, Int)] = List.from(tallies.stdlib.sortBy(-_(1)))

      if counts.nil then Nil else
        val items: List[Block.Item] =
          counts.map: (rule: Text, count: Int) =>
            val tally = Inline.Toned(Tone.Muted, List(Inline.Numeral(count.toDouble, 0)))
            val spaced: List[Inline] = List(Inline.Textual(t" "), tally)
            val label: List[Inline] = marked(filter == rule, rule) + spaced

            Block.Item(List(Block.Paragraph(label)), ruleAction(rule))

        List(Block.Heading(3, Inline.text(t"Rules")), Block.Listing(false, items))

    projects + rules

  // The latest check's summary — the rules that fired, tabled and charted — and the census
  // recorded for the selected profile.
  private def summaryBlocks(): List[Block] =
    val report: List[Block] = checked.lay(Nil: List[Block]): (last: Checked) =>
      val all: List[Report.Finding] = last.outcome.all
      def frequency(rule: Text): Int = all.filter(_.rule == rule).size

      val rules: List[Text] =
        List.from(all.map(_.rule).distinct.stdlib.sortBy { rule => -frequency(rule) })

      // The findings in the rules' order, so that the table and the chart both lead with
      // the rule that fired most, as the navigation does.
      val ordered: List[Report.Finding] =
        rules.bind[List[Report.Finding], Report.Finding, List[Report.Finding]]: (rule: Text) =>
          all.filter(_.rule == rule)

      val chart: List[Block] =
        if rules.nil then Nil else
          val series: List[Block.Series] =
            rules.map: (rule: Text) =>
              val hits = all.filter(_.rule == rule)
              val tone = if hits.exists(_.error) then Tone.Failure else Tone.Warning
              Block.Series(Inline.text(rule), List(hits.size.toDouble), tone)

          List(Block.Chart(Block.Chart.Kind.Bars, series))

      Report.summary(ordered, last.outcome.files) :: chart

    val measured: List[Block] = selected.lay(Nil: List[Block]): (target: Target) =>
      val records: List[Census.Record] =
        census.lay(Nil: List[Census.Record]): (entry: (Target, Long, List[Census.Record])) =>
          if entry(0) == target then entry(2) else Nil

      Block.Heading(3, Inline.text(t"Census")) :: censusBlocks(target, records)

    report + measured

  // The census, oldest first, as a sparkline per indicator and a table of every measurement,
  // newest first.
  private def censusBlocks(target: Target, records: List[Census.Record]): List[Block] =
    if records.nil then
      val absent = Inline.text(t"No census is recorded for ${target.profile}; ")

      List
        ( Block.Paragraph
            ( List
                ( Inline.Toned(Tone.Muted, absent),
                  Inline.Reference(t"flare metrics"),
                  Inline.Toned(Tone.Muted, Inline.text(t" records one.")) ) ) )
    else
      val named: List[Text] = records.bind[List[Text], Text, List[Text]](_.totals.map(_(0)))
      val indicators: List[Text] = List.from(named.distinct.stdlib.sortBy(_.s))

      def total(record: Census.Record, indicator: Text): Int =
        record.totals.seek(_(0) == indicator).lay(0)(_(1))

      val series: List[Block.Series] =
        indicators.map: (indicator: Text) =>
          val values = records.map: (record: Census.Record) => total(record, indicator).toDouble
          Block.Series(Inline.text(indicator), values)

      val chart: Block = Block.Chart(Block.Chart.Kind.Sparkline, series)

      val headings: List[Block.Column] =
        List(Block.Column(Inline.text(t"Commit")), Block.Column(Inline.text(t"Committed")))

      val measures: List[Block.Column] =
        indicators.map: (indicator: Text) =>
          Block.Column(Inline.text(indicator), Block.Alignment.End, numeric = true)

      val columns: List[Block.Column] = headings + measures

      val newestFirst: List[Census.Record] = List.from(records.stdlib.reverse)

      val rows: List[Block.Row] =
        newestFirst.map: (record: Census.Record) =>
          val commit = Block.Cell(List(Inline.Reference(record.commit.keep(7))))
          val date = Block.Cell(Inline.text(dated(record.time*1000L)))

          val counts =
            indicators.map: (indicator: Text) =>
              Block.Cell(List(Inline.Numeral(total(record, indicator).toDouble, 0)))

          Block.Row(List(commit, date) + counts)

      val caption: List[Inline] =
        if records.size == 1 then Inline.text(t"1 measurement over the latest 200 commits")
        else Inline.text(t"${records.size.show} measurements over the latest 200 commits")

      List(chart, Block.Table(columns, rows, caption))
