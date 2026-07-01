# How I used AI on this project

The brief encourages AI use as long as I'm transparent about it, so here's an
honest summary. I drove the work and made the design decisions; I used AI to
move faster on research, boilerplate, and first drafts.

## What I decided (and why)

1. **Java + Angular in two Docker containers.** I weighed Python/React and a
   single combined image, and chose Java + Angular to show a clear, structured
   setup. I kept the backend and frontend as separate services because it's
   closer to a real deployment, while still starting with one `docker compose up`.

2. **I match records on a score, not a single rule.** No one field is reliable,
   so I combined several signals (time, people, owner, title) and required the
   two records to be on the same day. Signals that don't apply (like an internal
   meeting with no client) are skipped so they don't skew the result.

3. **I group records instead of matching only in pairs.** This was my key call
   for the Pinnacle case, where one CRM record lines up with two calendar entries
   that are duplicates of each other. Grouping makes all three collapse into one
   meeting cleanly.

4. **I show conflicts and never silently pick a winner.** The app is meant to
   help a person decide, so it always keeps both systems' values and flags the
   disagreement.

5. **I handled the time-zone case honestly.** One calendar time is in UTC. Once
   converted to Eastern it lands an hour off from the CRM, so I let the app
   report that as a real conflict rather than tweaking things to force a match.

6. **I avoided false alarms.** "confirmed" vs "completed" isn't flagged, but
   "cancelled" vs "confirmed" is.

## Where AI helped

- **Spotting the data quirks faster.** I had it list the tricky cases in the two
  files, then verified each against the raw data myself before deciding how to
  handle them.
- **Boilerplate and first drafts.** It scaffolded the Spring Boot and Angular
  setup and drafted initial versions of some code, which I reviewed and adjusted
  while testing against the real data.
- **Docker config and a first pass at the docs.**

## What I rejected or changed

- An AI suggestion to **auto-pick a "winning" value** for each conflict, I turned
  it down, because that defeats the purpose of the tool.
- **Exact time matching**, too strict for data that intentionally has small time
  differences; I used a tolerance window and flag larger gaps.
- A heavier **fuzzy text-distance** approach for titles, simple word overlap was
  enough and easier to reason about.
- A hard-coded UTC offset for Eastern time, I used the real time zone so daylight
  saving is handled correctly.

## How I checked it

I didn't just take the generated code on faith. I traced the matching rules
against the records I expected to match and the conflicts I expected to see, and
the results lined up: 24 combined meetings from 20 CRM + 22 calendar records,
with the duplicate collapsed and the expected conflicts showing. Those expected
outcomes are documented in the README so they can be checked against the live app.
