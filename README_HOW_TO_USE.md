# How to use these files

This set of files is a prompt-engineering roadmap for building **iTantra** with a coding assistant, phase by phase — the same pattern used for your other multi-phase project roadmaps.

## Files
- `00_MASTER.md` — the constitution. Product spec, tech stack, architecture, file structure, wire protocol, design system, and the rules every phase must obey. Keep this attached/pinned in every phase conversation (or commit it into the repo as `ARCHITECTURE.md` and just reference it).
- `PHASE_0_SETUP.md` through `PHASE_7_POLISH_DEMO.md` — one prompt per phase, run in order.

## Workflow
1. Start a fresh conversation with your coding agent (Claude Code, Android Studio's Gemini, Cursor, etc.).
2. Paste `00_MASTER.md` first, then the current phase file, e.g. `PHASE_0_SETUP.md`.
3. Let it implement the phase. Check off that phase's **Acceptance Criteria** yourself against the actual running app (mock flavor at minimum; prod flavor with two devices where noted) before moving on.
4. Commit that phase's work as its own commit/PR, per the master doc's rule 7.
5. Start the next phase in a new conversation (or continue the same one) with `00_MASTER.md` + the next `PHASE_N_*.md` file — the agent doesn't need the full history replayed, the master doc plus the existing codebase is enough context.

## Why this order
Setup → static UI → real connectivity → real speech → real messaging → the SOS safety feature → reliability hardening → polish. Connectivity and speech are built and proven independently (Phases 2 and 3) before being wired together (Phase 4), so if something breaks you know immediately which layer to blame. SOS is deliberately its own phase (5) after basic messaging works, since it's the highest-stakes feature and shouldn't be rushed alongside general chat plumbing. Reliability (6) and polish (7) come last because they're cross-cutting — they only make sense once there's a real app underneath them to harden and polish.

## Quick reference: what "mock mode" means throughout
Every phase from 0 onward keeps a fully working `mock` build flavor that never touches real radios, real speech models, or the internet. It's not a debug toggle bolted on at the end — it's a parallel Hilt binding set from Phase 0, so at every single point in this roadmap you have a demoable app, even with no second phone in the room and no connectivity.
