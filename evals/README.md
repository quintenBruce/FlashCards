# Bilingual release evaluation

`cases.json` contains 100 candidate grading cases: regional alternatives in both
directions plus accent, negation, tense, agreement, false-friend and ambiguity
contrasts. These are reference drafts, not a completed human review. Several
one-word prompts deliberately require context; a bilingual reviewer must adjust
expected verdicts before approving the set. Record reviewer/date and set every
case's `reviewStatus` to `approved` after checking it.

The runner refuses paid evaluation unless `RUN_PAID_EVAL=YES` is supplied. Keep the
OpenAI key in your shell/environment, never in this repo. It defaults to Luna and
100 cases, at most $2 in conservatively reserved estimated charges. Optional Sol
generation evaluates natural usage and complete proposals through the app's
chat acceptance checks; inspect those outputs manually instead of using the
model's own opinions as ground truth.

User-operated command (not run by the implementation agent):

```powershell
$env:RUN_PAID_EVAL='YES'
$env:OPENAI_API_KEY='<private key>'
node evals/run.mjs
```

Results are written under ignored `verification-results/`. Require at least 95%
agreement on the approved set and manually inspect all accent/negation/meaning
errors. Do not claim a passing score until the reviewed set has actually run.
