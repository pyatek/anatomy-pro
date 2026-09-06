# Claude Design Prompt — Anatomy Pro Prototype Screens

> **How to use:** copy everything below the line into Claude (claude.ai or Claude Code)
> to generate the prototype. It is written to be self-contained — no other context needed.

---

## Brief

Design the prototype screens for **Anatomy Pro**, a mobile study application for
medical students. It presents an interactive 3D model of the human body and generates
quizzes from it.

Produce a **single self-contained HTML artifact** presenting every screen as a
phone-sized frame (390 × 844), laid out in a scrollable, labelled gallery grouped by
flow. Static mockups — no working 3D, no real interactivity required beyond what makes
the screens readable. Inline all CSS. Use placeholder shapes or simple SVG silhouettes
where the 3D body would appear.

## Who it is for

Medical students, mostly aged 19–25, studying for anatomy examinations where they must
identify a structure and name it **in Latin**. They use this on a train, in a library,
and in the twenty minutes before a viva. They are stressed, time-poor, and studying
something genuinely difficult.

**Design consequence:** this is a serious professional instrument, not a casual game.
Gamification exists (streaks, a leaderboard) but must feel like a training log, not a
mobile game. No confetti, no cartoon mascots, no aggressive reward animation. Earn
trust through precision and clarity.

## Art direction

**Dark-first.** The 3D body is the product, and anatomical structures read best against
a dark, neutral ground — this is why every serious 3D anatomy tool is dark. Provide a
light theme for the reading-heavy screens (structure detail, settings) but the atlas and
quiz screens are dark, always.

- **Ground:** deep neutral, very slightly cool. Not pure black — the model needs to sit
  on something with depth.
- **Accent:** one confident colour for selection and highlight. Anatomical rather than
  corporate — think oxygenated tissue, not SaaS blue. Use it sparingly; when everything
  is accented, the highlighted structure stops reading as highlighted.
- **Highlighting must never rely on hue alone.** Selected structures get an outline plus
  a luminance shift, so the design works for colour-blind users. Show this explicitly.
- **Typography:** Latin anatomical terms are the hero content. Give them a distinct,
  slightly more formal treatment than UI chrome — the terminology should feel like it
  comes from a reference work. Ensure long terms such as *musculus sternocleidomastoideus*
  fit without truncation or awkward wrapping.
- **Restraint over decoration.** Chrome recedes; anatomy and terminology advance.

## Trilingual requirement

Every structure carries three names: **Latin (Terminologia Anatomica), Polish, and
English.** Latin is canonical.

Design a clear, repeatable hierarchy for showing them — Latin primary with the learner's
language secondary — and apply it consistently wherever a structure is named. More
languages will be added later, so the treatment must not assume exactly three.

The UI language and the quiz language are **independent settings**: a student may read
the interface in Polish while being quizzed in Latin. Reflect this in Settings.

## Screens to design

**Onboarding**
1. Language selection — UI language and study language, shown as distinct choices
2. Goal setting — which systems they are currently studying
3. First pack download — skeletal system, free, with progress

**Atlas (the core)**

4. **Atlas viewer** — the hero screen. 3D body dominant on dark ground. Needs, without
   crowding: system layer controls (skin → muscle → bone), search access, reset camera,
   and a bottom sheet for the selected structure. Show a structure selected.
5. **Structure detail** — full names in all languages, definition, position in the
   hierarchy, related structures, and a "quiz me on this region" action.
6. **Search** — searching across all three languages simultaneously, with results
   showing which language matched.
7. **Layer / system panel** — toggling visibility per system, and isolating a single
   structure with neighbours ghosted.

**Quiz**

8. **Topic selection** — systems and regions as a grid, each showing mastery progress
9. **Quiz: tap the structure** — prompt shows a name, user taps the body. Show the
   moment *before* answering.
10. **Quiz: name the highlighted structure** — one structure glows, four options below.
11. **Answer feedback — correct**
12. **Answer feedback — incorrect**, showing the right structure and the mistaken one
    distinguished from each other. This is the highest-value learning moment in the
    entire app; design it with more care than the correct state.
13. **Session summary** — score, time, which structures need review

**Daily quiz & competition**

14. **Home / dashboard** — today's quiz as the primary call to action, current streak,
    continue studying
15. **Daily quiz lobby** — same questions for everyone today, one attempt, timed
16. **Leaderboard** — today's global ranking plus the user's own position; also a
    streak-based view
17. **Profile** — streak calendar, mastery by system, download management

**Commercial & system**

18. **Paywall** — skeletal system is free forever; other systems by subscription.
    Persuade by demonstrating value, not by pressuring.
19. **Pack manager** — downloaded, available, and sizes, with delete
20. **Settings** — UI language, study language, accessibility options
21. **Accessibility: structure tree mode** — the screen-reader alternative to the 3D
    canvas, where the anatomical hierarchy is navigable as a list. Design this as a
    genuine equivalent experience, not a degraded fallback.

## Accessibility requirements

Non-negotiable, and part of what you are being asked to demonstrate:

- Text contrast meets WCAG AA against the dark ground
- Touch targets at least 44 × 44 pt
- No information conveyed by colour alone, anywhere
- Layouts survive large dynamic type without clipping — show at least one screen at an
  enlarged type size
- Quiz timers can be disabled; design the untimed state
- Screen 21 exists because a 3D canvas is invisible to screen readers

## What to deliver

1. The HTML artifact with all 21 screens, grouped and labelled by flow.
2. A short design-rationale section covering the colour system (with hex values), the
   type scale, the spacing system, and the trilingual naming treatment.
3. A component inventory of the repeated elements — structure card, quiz option,
   progress indicator, system toggle, language pair.

## Constraint worth knowing

This will be built in **Compose Multiplatform** for Android and iOS. Favour patterns
that translate cleanly: standard bottom sheets, list and grid layouts, straightforward
navigation. Avoid effects that would be disproportionately expensive to reproduce
natively — heavy backdrop blur behind moving 3D content, elaborate scroll-linked
animation, or custom text layout.

Design for a real, buildable app.
