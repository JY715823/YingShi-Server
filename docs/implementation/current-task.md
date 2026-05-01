# Current Task - Stage 11B-5 Backend Stability for Real Mode

## Goal
Stabilize backend support for Android REAL mode integration and smoke testing.

## Scope
- retain Chinese seed data
- retain same-space comment edit/delete policy
- integration smoke coverage check
- auth / post / media / comment / upload / trash stability
- minimal API bug fixes if needed
- minimal docs update

## Product intent
- Backend should support Android REAL mode reliably.
- Dev data should remain natural Chinese.
- Same-space members may edit/delete comments.
- Smoke scripts should catch major integration regressions.

## Do not do
- no real OSS
- no real Android system media delete
- no real notification implementation
- no complex role system
- no broad refactor

## Done when
- Server starts
- Tests pass
- Integration smoke is updated and runnable
- REAL mode APIs remain compatible with Android