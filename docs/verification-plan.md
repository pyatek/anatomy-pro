# Verification plan

- **Date:** 2026-10-06
- **Answers:** design spec §17 risk 1 — content verification capacity, the item most likely to
  stall the release.
- **Status:** decisions made; throughput is assumed, not measured, until §5's calibration runs.
  The numbers in §4 describe the packs as they were before the change it asks for.

## 1. Decisions

Made by the owner on 2026-10-06.

| Question | Decision |
|---|---|
| Reviewer | One reviewer, 10 or more hours a week |
| Quiz eligibility | A structure may be a quiz answer only when every locale is verified |
| First content | The whole-body skeleton |
| Polish | Polish interface at first release; Polish structure names follow later |

The second and fourth rows only fit together one way, and this plan reads them that way:
**"every locale" means every locale the app ships names in.** At first release that is Latin
and English. Polish names are then released pack by pack, already verified, so adding Polish
never takes a quizzable structure away. If the intent was instead that nothing is quizzable
until Polish exists, the first release is blocked on a Polish source that has not been found
(§7), and this plan's dates do not hold.

## 2. What one verification is

Two different checks, which the spec has so far counted as one:

- **Identity** — does this mesh show the structure the name claims, on the side it claims?
  Per structure, visual, and where the real risk lies: Latin and English come from TA2 and
  are authoritative as terms, but they reach a mesh through a join on Z-Anatomy's English
  object name. A wrong join is a correct name on the wrong bone.
- **Name** — is this the right term in this language? Per term, per locale. Left and right
  share a term, so this is counted in terms, not structures.

One review card therefore covers one term: its meshes highlighted (both sides together), and
its name in each shipped locale. The reviewer verifies, edits or disputes.

## 3. How much there is

Counted from the packs in `pipeline/build`, generated 2026-09-08 to 09-09.

| Pack | Structures | of which groups | Unique terms | Scope |
|---|---|---|---|---|
| `skeletal-body` | 347 | 69 | **215** | Whole body — the first milestone |
| `skeletal-trunk` | 115 | 29 | 88 | Trunk |
| `muscular-trunk` | 247 | 42 | 147 | Trunk |
| `joints-trunk` | 105 | 0 | 82 | Trunk; predates group synthesis |
| `visceral-trunk` | 50 | 0 | 44 | Trunk; predates group synthesis |

**The whole atlas is nearer 2,400 terms than 16,000 decisions.** State-of-play's figure
multiplied ~3,758 structures by three locales. But names are reviewed per term, and in the
packs above terms are 62–67% of structures, which puts the atlas at roughly 2,400 terms.
That is an extrapolation: only the skeleton has been counted for the whole body, and the
other systems need a pipeline run against the Z-Anatomy source, which is not on the machine
this was written on.

Two things will move these numbers. The pipeline drops about 950 vessel and nerve objects
(state-of-play, known defects); fixing that adds terms to two systems. And a group shares its
term with a leaf in 14 cases in the skeleton, so cards slightly outnumber terms.

## 4. Definitions cannot stay inside the verification

**As built, verifying a name means approving a Wikipedia article.** `PackIngest` hashes the
name together with the definition, and §24.1 makes that hash what a verification approves.
The definitions Z-Anatomy carries are whole articles:

- The skeleton has 144 distinct definitions totalling 116,040 words; the median is 533 words
  and the longest is 7,216.
- The femur's is 2,001 words and includes arthropod leg segments.
- The same English text is stored on every locale's row, so it is hashed into the Latin
  verification too.

Reading that critically is 13 hours or more for the skeleton alone, most of it irrelevant to
whether `Femur` is the femur. And any edit to an article silently un-verifies the name.

So this plan assumes **a verification covers identity and name only.** That change was made
on 2026-10-06 (design spec §31): definitions are separate content, shown attributed and
marked unreviewed, and cut by the pipeline to a lead of at most 120 words — 9,632 words for
the skeleton instead of 116,040. Quiz answers are names; nothing in §8 asks a definition.

## 5. Throughput

Nothing has been measured: there is no reviewer tool. These are assumptions to be replaced.

| | Optimistic | Expected | Pessimistic |
|---|---|---|---|
| Time per card | 30 s | 60 s | 3 min |
| Cards needing follow-up, at 10 min each | 5% | 10% | 20% |
| Skeleton, ~229 cards | 4 h | 8 h | 19 h |
| Whole atlas, ~2,400 terms | 40 h | 80 h | 200 h |
| Polish pass, later, names only | 15 h | 30 h | 70 h |

At 10 hours a week the skeleton is one to two weeks of review and the atlas two to five
months. The capacity risk is real but it is weeks and months, not years.

**Calibration comes first.** The reviewer times the first 50 skeleton cards before any date
below is treated as a commitment. If the measured time per card is outside the table's
range, this section is rewritten rather than the schedule stretched.

## 6. Order and dates

| Step | Target | Depends on |
|---|---|---|
| Definitions separated from verification (§4) | Done 2026-10-06 | — |
| Calibration: 50 skeleton cards timed | 2026-10-26 | A way to see a structure beside its names; the atlas screen and a term list are enough |
| Reviewer tool usable (§7 of the spec) | 2026-11-09 — assumed, not planned | Its own spec and plan, which do not exist |
| **Skeleton verified, Latin and English** | **2026-11-23** | The two rows above |
| Joints, then muscles, then viscera | Dated when each is counted for the whole body | A pipeline run per system |
| Vessels and nerves | After the others | The curve-object defect fixed |
| Whole atlas, Latin and English | 2027-02 to 2027-04 on the expected and pessimistic rows | Everything above |
| Polish names, pack by pack | Not dated | A licensed Polish source |

The skeleton goes first because it is the only system counted for the whole body, its
hierarchy gives the hard quiz tier real sibling sets (§25.1), and it is small enough to prove
the review flow. Joints follow because they name what the skeleton's bones form.

Since §29 the skeleton is no longer "the free pack": the atlas is free for every system and
browsable unverified. What verification gates is which structures the paid quiz may ask.
The first quiz release is therefore a skeleton quiz.

## 7. Open

- **Whole-body counts for every system but the skeleton.** Needed before their dates mean
  anything.
- **A Polish source.** None is identified; its licence matters as much as its coverage.
- **One reviewer is one opinion.** `DISPUTED` cards have nobody to go to. Who resolves them —
  a second student, a paid anatomist for an hour a month — is undecided.
- **The reviewer tool's design.** Its speed sets every number in §5.
- **Whether the reviewer is paid**, and what happens to the schedule in exam season.
