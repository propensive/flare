# Rule reference

Every rule is identified by a **bolt code**, the form in which every Propensive
project names its diagnostics: `↯/c.`, the number of the principle the rule
derives from, and the rule's number within that principle. A rule with several
distinct failures adds a third number for each: `↯/c.2.2.1`, `↯/c.2.9.3`. `c` is
the letter by which bolt codes name Consequent Style, and the principles are
numbered in the order Part I of [the standard](../consequent-style.md) sets them
out, from `↯/c.1`, The Frame, to `↯/c.9`, Soundness.

Numbering within a principle follows the order of Part II of the standard. A
number carries no meaning beyond identity, and is permanent: a rule keeps its
code even if the section order changes, and a code is never given to another
rule. The record of every code is `c.tel` in the
[bolt](https://github.com/propensive/bolt) registry.

A checker reports a violation under a code of its own which carries the rule's:
flare writes `[↯/f.21.1/c.2.2.1]`, its violation of a style rule, carrying the
rule `↯/c.2.2.1`. A project's configuration names a rule, or a whole principle,
by its code without the `↯/`: `omit c.8.4`, `strict c.9`.

## `↯/c.1` — The Frame

*The fixed shape every file sits in.*

| Rule | |
| --- | --- |
| [`↯/c.1.1`](c.1.1.md) | Licence-header block comment |
| [`↯/c.1.2`](c.1.2.md) | `package` declaration on line `header + 1` |
| [`↯/c.1.3`](c.1.3.md) | Blank line after `package` |
| [`↯/c.1.4`](c.1.4.md) | Line length |
| [`↯/c.1.5`](c.1.5.md) | Tab characters |
| [`↯/c.1.6`](c.1.6.md) | Indent width must be even |
| [`↯/c.1.7`](c.1.7.md) | Trailing whitespace |
| [`↯/c.1.8`](c.1.8.md) | Block-comment usage |

## `↯/c.2` — Anchoring

*Where a continuation line begins.*

| Rule | |
| --- | --- |
| [`↯/c.2.1`](c.2.1.md) | Continuation indent |
| [`↯/c.2.2`](c.2.2.md) | Keyword-sequence layout |
| [`↯/c.2.3`](c.2.3.md) | Type-annotation anchor |
| [`↯/c.2.4`](c.2.4.md) | Anchor of a heavy argument block |
| [`↯/c.2.5`](c.2.5.md) | Indented scope body |
| [`↯/c.2.6`](c.2.6.md) | Signature `=` placement |
| [`↯/c.2.7`](c.2.7.md) | `given` continuation alignment |
| [`↯/c.2.8`](c.2.8.md) | Multi-line interpolated-string layout |
| [`↯/c.2.9`](c.2.9.md) | Quote and splice layout |

## `↯/c.3` — Density

*How much to fit on one line.*

| Rule | |
| --- | --- |
| [`↯/c.3.1`](c.3.1.md) | Unnecessary line break |
| [`↯/c.3.2`](c.3.2.md) | Lambda bracketing |

## `↯/c.4` — Continuation Marking

*Showing that a line is unfinished.*

| Rule | |
| --- | --- |
| [`↯/c.4.1`](c.4.1.md) | `.method` continuation lines |
| [`↯/c.4.2`](c.4.2.md) | Symbolic infix operator continuation |
| [`↯/c.4.3`](c.4.3.md) | Continuation indents after `=>` and heavy `:` |

## `↯/c.5` — Balance

*Symmetry of spacing around a token.*

| Rule | |
| --- | --- |
| [`↯/c.5.1`](c.5.1.md) | Operator spacing |
| [`↯/c.5.2`](c.5.2.md) | Assignment spacing |
| [`↯/c.5.3`](c.5.3.md) | Single space between specific tokens |
| [`↯/c.5.4`](c.5.4.md) | Multi-line block bracket spacing |
| [`↯/c.5.5`](c.5.5.md) | Bracket inner spacing |

## `↯/c.6` — Proximity

*Blank lines and gaps as grouping.*

| Rule | |
| --- | --- |
| [`↯/c.6.1`](c.6.1.md) | Spacing around commas |
| [`↯/c.6.2`](c.6.2.md) | Blank-line spacing between sibling declarations |
| [`↯/c.6.3`](c.6.3.md) | Too many consecutive blank lines |
| [`↯/c.6.4`](c.6.4.md) | Blank line after heavy-signature return type |
| [`↯/c.6.5`](c.6.5.md) | Blank line after imports |
| [`↯/c.6.6`](c.6.6.md) | Annotations and following declarations |

## `↯/c.7` — Tabulation

*Vertical alignment across a run of lines.*

| Rule | |
| --- | --- |
| [`↯/c.7.1`](c.7.1.md) | `=>` alignment in case run |
| [`↯/c.7.2`](c.7.2.md) | `using`-clause alignment |
| [`↯/c.7.3`](c.7.3.md) | `for`-comprehension layout |

## `↯/c.8` — Locatability

*Finding a definition from its name.*

| Rule | |
| --- | --- |
| [`↯/c.8.1`](c.8.1.md) | Top-level import rules |
| [`↯/c.8.2`](c.8.2.md) | File naming conventions |
| [`↯/c.8.3`](c.8.3.md) | `object` must precede `class`/`trait`/`enum` of same name |
| [`↯/c.8.4`](c.8.4.md) | Definition not exported to the umbrella package |
| [`↯/c.8.5`](c.8.5.md) | Extension method not exported to the umbrella package |

## `↯/c.9` — Soundness

*What the code claims about itself.*

Unlike the other principles, these rules are about meaning rather than
layout, and they are inert until the project declares a gate — the token type
whose `using` parameter marks a definition as bypassing a guarantee, and the
prefix such a definition's name must carry.

| Rule | |
| --- | --- |
| [`↯/c.9.1`](c.9.1.md) | Unsafe naming |

## Suppressing a rule

There is no per-site suppression. A rule that is wrong for a project is left
out of its style with `omit` (`omit c.8.4`, or a whole principle with `omit c.8`),
and a rule that depends on a project parameter — the licence header, the
column limit, the umbrella package — is configured through the `style`
stanza of `.pyrocosm/flare/config.tel`, described in the
[README](../../.github/readme.md).

A project can also define rules of its own — named detections over the parse
tree, such as `rule cast` with `invokes asInstanceOf` — and enforce them
alongside the style; those are documented in the README rather than here,
since each project's are its own.

## Migrating from the letter identifiers

Before bolt codes, each rule was identified by the letter of its principle and
its number, `A2.1`; the principle letters were `F`, `A`, `D`, `C`, `B`, `P`, `T`,
`L` and `S`, in the order of the principles' numbers now. A configuration which
names a rule by its letter identifier, in `omit` or `strict`, must name it by its
code instead.

| Was | Is |
| --- | --- |
| `F1` | [`↯/c.1.1`](c.1.1.md) |
| `F2` | [`↯/c.1.2`](c.1.2.md) |
| `F3` | [`↯/c.1.3`](c.1.3.md) |
| `F4` | [`↯/c.1.4`](c.1.4.md) |
| `F5` | [`↯/c.1.5`](c.1.5.md) |
| `F6` | [`↯/c.1.6`](c.1.6.md) |
| `F7` | [`↯/c.1.7`](c.1.7.md) |
| `F8` | [`↯/c.1.8`](c.1.8.md) |
| `F8.1` | [`↯/c.1.8.1`](c.1.8.md) |
| `F8.2` | [`↯/c.1.8.2`](c.1.8.md) |
| `A1` | [`↯/c.2.1`](c.2.1.md) |
| `A2` | [`↯/c.2.2`](c.2.2.md) |
| `A2.1` | [`↯/c.2.2.1`](c.2.2.md) |
| `A2.2` | [`↯/c.2.2.2`](c.2.2.md) |
| `A3` | [`↯/c.2.3`](c.2.3.md) |
| `A4` | [`↯/c.2.4`](c.2.4.md) |
| `A5` | [`↯/c.2.5`](c.2.5.md) |
| `A6` | [`↯/c.2.6`](c.2.6.md) |
| `A7` | [`↯/c.2.7`](c.2.7.md) |
| `A8` | [`↯/c.2.8`](c.2.8.md) |
| `A8.1` | [`↯/c.2.8.1`](c.2.8.md) |
| `A8.2` | [`↯/c.2.8.2`](c.2.8.md) |
| `A8.3` | [`↯/c.2.8.3`](c.2.8.md) |
| `A8.4` | [`↯/c.2.8.4`](c.2.8.md) |
| `A9` | [`↯/c.2.9`](c.2.9.md) |
| `A9.1` | [`↯/c.2.9.1`](c.2.9.md) |
| `A9.2` | [`↯/c.2.9.2`](c.2.9.md) |
| `A9.3` | [`↯/c.2.9.3`](c.2.9.md) |
| `A9.4` | [`↯/c.2.9.4`](c.2.9.md) |
| `A9.5` | [`↯/c.2.9.5`](c.2.9.md) |
| `A9.6` | [`↯/c.2.9.6`](c.2.9.md) |
| `D1` | [`↯/c.3.1`](c.3.1.md) |
| `D2` | [`↯/c.3.2`](c.3.2.md) |
| `D2.1` | [`↯/c.3.2.1`](c.3.2.md) |
| `D2.2` | [`↯/c.3.2.2`](c.3.2.md) |
| `D2.3` | [`↯/c.3.2.3`](c.3.2.md) |
| `D2.4` | [`↯/c.3.2.4`](c.3.2.md) |
| `C1` | [`↯/c.4.1`](c.4.1.md) |
| `C1.1` | [`↯/c.4.1.1`](c.4.1.md) |
| `C1.2` | [`↯/c.4.1.2`](c.4.1.md) |
| `C2` | [`↯/c.4.2`](c.4.2.md) |
| `C2.1` | [`↯/c.4.2.1`](c.4.2.md) |
| `C2.2` | [`↯/c.4.2.2`](c.4.2.md) |
| `C2.3` | [`↯/c.4.2.3`](c.4.2.md) |
| `C3` | [`↯/c.4.3`](c.4.3.md) |
| `B1` | [`↯/c.5.1`](c.5.1.md) |
| `B2` | [`↯/c.5.2`](c.5.2.md) |
| `B3` | [`↯/c.5.3`](c.5.3.md) |
| `B4` | [`↯/c.5.4`](c.5.4.md) |
| `B5` | [`↯/c.5.5`](c.5.5.md) |
| `P1` | [`↯/c.6.1`](c.6.1.md) |
| `P1.1` | [`↯/c.6.1.1`](c.6.1.md) |
| `P1.2` | [`↯/c.6.1.2`](c.6.1.md) |
| `P2` | [`↯/c.6.2`](c.6.2.md) |
| `P3` | [`↯/c.6.3`](c.6.3.md) |
| `P4` | [`↯/c.6.4`](c.6.4.md) |
| `P5` | [`↯/c.6.5`](c.6.5.md) |
| `P6` | [`↯/c.6.6`](c.6.6.md) |
| `P6.1` | [`↯/c.6.6.1`](c.6.6.md) |
| `P6.2` | [`↯/c.6.6.2`](c.6.6.md) |
| `T1` | [`↯/c.7.1`](c.7.1.md) |
| `T1.1` | [`↯/c.7.1.1`](c.7.1.md) |
| `T1.2` | [`↯/c.7.1.2`](c.7.1.md) |
| `T1.3` | [`↯/c.7.1.3`](c.7.1.md) |
| `T2` | [`↯/c.7.2`](c.7.2.md) |
| `T3` | [`↯/c.7.3`](c.7.3.md) |
| `T3.1` | [`↯/c.7.3.1`](c.7.3.md) |
| `T3.2` | [`↯/c.7.3.2`](c.7.3.md) |
| `T3.3` | [`↯/c.7.3.3`](c.7.3.md) |
| `T3.4` | [`↯/c.7.3.4`](c.7.3.md) |
| `L1` | [`↯/c.8.1`](c.8.1.md) |
| `L1.1` | [`↯/c.8.1.1`](c.8.1.md) |
| `L1.2` | [`↯/c.8.1.2`](c.8.1.md) |
| `L1.3` | [`↯/c.8.1.3`](c.8.1.md) |
| `L2` | [`↯/c.8.2`](c.8.2.md) |
| `L3` | [`↯/c.8.3`](c.8.3.md) |
| `L4` | [`↯/c.8.4`](c.8.4.md) |
| `L5` | [`↯/c.8.5`](c.8.5.md) |
| `S1` | [`↯/c.9.1`](c.9.1.md) |
| `S1.1` | [`↯/c.9.1.1`](c.9.1.md) |
| `S1.2` | [`↯/c.9.1.2`](c.9.1.md) |

## Migrating from the `SN-` identifiers

Before the letter identifiers, these rules were numbered in the `SN-nnn`
scheme, in which one number could span several unrelated rules. The old
identifiers map onto the codes as follows.

| Was | Is |
| --- | --- |
| `SN-799` | [`↯/c.1.1`](c.1.1.md) |
| `SN-131` | [`↯/c.1.2`](c.1.2.md) |
| `SN-658` | [`↯/c.1.3`](c.1.3.md) |
| `SN-230` | [`↯/c.1.4`](c.1.4.md) |
| `SN-135` | [`↯/c.1.5`](c.1.5.md) |
| `SN-926` | [`↯/c.1.6`](c.1.6.md) |
| `SN-015` | [`↯/c.1.7`](c.1.7.md) |
| `SN-162` | [`↯/c.1.8`](c.1.8.md) |
| `SN-162.1` | [`↯/c.1.8.1`](c.1.8.md) |
| `SN-162.2` | [`↯/c.1.8.2`](c.1.8.md) |
| `SN-473.1` | [`↯/c.2.1`](c.2.1.md) |
| `SN-833` | [`↯/c.2.2`](c.2.2.md) |
| `SN-833.1` | [`↯/c.2.2.1`](c.2.2.md) |
| `SN-833.2` | [`↯/c.2.2.2`](c.2.2.md) |
| `SN-833.3` | [`↯/c.2.3`](c.2.3.md) |
| `SN-833.4` | [`↯/c.2.4`](c.2.4.md) |
| `SN-473.8` | [`↯/c.2.5`](c.2.5.md) |
| `SN-473.9` | [`↯/c.2.6`](c.2.6.md) |
| `SN-140` | [`↯/c.2.7`](c.2.7.md) |
| `SN-560` | [`↯/c.2.8`](c.2.8.md) |
| `SN-560.1` | [`↯/c.2.8.1`](c.2.8.md) |
| `SN-560.2` | [`↯/c.2.8.2`](c.2.8.md) |
| `SN-560.3` | [`↯/c.2.8.3`](c.2.8.md) |
| `SN-560.4` | [`↯/c.2.8.4`](c.2.8.md) |
| `SN-473.2` | [`↯/c.2.9.1`](c.2.9.md) |
| `SN-473.3` | [`↯/c.2.9.2`](c.2.9.md) |
| `SN-473.4` | [`↯/c.2.9.3`](c.2.9.md) |
| `SN-473.5` | [`↯/c.2.9.4`](c.2.9.md) |
| `SN-473.6` | [`↯/c.2.9.5`](c.2.9.md) |
| `SN-473.7` | [`↯/c.2.9.6`](c.2.9.md) |
| `SN-247` | [`↯/c.3.1`](c.3.1.md) |
| `SN-312` | [`↯/c.3.2`](c.3.2.md) |
| `SN-312.1` | [`↯/c.3.2.1`](c.3.2.md) |
| `SN-312.2` | [`↯/c.3.2.2`](c.3.2.md) |
| `SN-312.3` | [`↯/c.3.2.3`](c.3.2.md) |
| `SN-312.4` | [`↯/c.3.2.4`](c.3.2.md) |
| `SN-163` | [`↯/c.4.1`](c.4.1.md) |
| `SN-163.1` | [`↯/c.4.1.1`](c.4.1.md) |
| `SN-163.2` | [`↯/c.4.1.2`](c.4.1.md) |
| `SN-616` | [`↯/c.4.2`](c.4.2.md) |
| `SN-616.1` | [`↯/c.4.2.1`](c.4.2.md) |
| `SN-616.2` | [`↯/c.4.2.2`](c.4.2.md) |
| `SN-616.3` | [`↯/c.4.2.3`](c.4.2.md) |
| `SN-444` | [`↯/c.4.3`](c.4.3.md) |
| `SN-376` | [`↯/c.5.1`](c.5.1.md) |
| `SN-376.1` | [`↯/c.5.2`](c.5.2.md) |
| `SN-013` | [`↯/c.5.3`](c.5.3.md) |
| `SN-811` | [`↯/c.5.4`](c.5.4.md) |
| `SN-402` | [`↯/c.5.5`](c.5.5.md) |
| `SN-529` | [`↯/c.6.1`](c.6.1.md) |
| `SN-529.1` | [`↯/c.6.1.1`](c.6.1.md) |
| `SN-529.2` | [`↯/c.6.1.2`](c.6.1.md) |
| `SN-315` | [`↯/c.6.2`](c.6.2.md) |
| `SN-783` | [`↯/c.6.3`](c.6.3.md) |
| `SN-677` | [`↯/c.6.4`](c.6.4.md) |
| `SN-441` | [`↯/c.6.5`](c.6.5.md) |
| `SN-551` | [`↯/c.6.6`](c.6.6.md) |
| `SN-551.1` | [`↯/c.6.6.1`](c.6.6.md) |
| `SN-551.2` | [`↯/c.6.6.2`](c.6.6.md) |
| `SN-326` | [`↯/c.7.1`](c.7.1.md) |
| `SN-946` | [`↯/c.7.2`](c.7.2.md) |
| `SN-924` | [`↯/c.7.3`](c.7.3.md) |
| `SN-924.1` | [`↯/c.7.3.1`](c.7.3.md) |
| `SN-924.2` | [`↯/c.7.3.2`](c.7.3.md) |
| `SN-924.3` | [`↯/c.7.3.3`](c.7.3.md) |
| `SN-924.4` | [`↯/c.7.3.4`](c.7.3.md) |
| `SN-302` | [`↯/c.8.1`](c.8.1.md) |
| `SN-302.1` | [`↯/c.8.1.1`](c.8.1.md) |
| `SN-302.2` | [`↯/c.8.1.2`](c.8.1.md) |
| `SN-302.3` | [`↯/c.8.1.3`](c.8.1.md) |
| `SN-847` | [`↯/c.8.2`](c.8.2.md) |
| `SN-398` | [`↯/c.8.3`](c.8.3.md) |
| `SN-742` | [`↯/c.8.4`](c.8.4.md) |
| `SN-742.1` | [`↯/c.8.5`](c.8.5.md) |
| `R33-multiline-pattern-arrow-position` | [`↯/c.7.1.1`](c.7.1.md) |
| `R33-multiline-pattern-body-newline` | [`↯/c.7.1.2`](c.7.1.md) |
| `R33-multiline-case-arrow-space` | [`↯/c.7.1.3`](c.7.1.md) |
