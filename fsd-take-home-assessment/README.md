# Event Sync Service

This is a small app for a sales team. It pulls meeting data from two different
systems, a **CRM** and a **Calendar**, figures out which records are actually
talking about the *same meeting*, and shows one clean, combined list.

The important part: the two systems often disagree (different times, different
locations, "Zoom" vs "In-Person", etc.). Instead of hiding those disagreements,
this app **shows them clearly** and tells you which system each value came from.

- **Backend:** Java 17 + Spring Boot
- **Frontend:** Angular (served by nginx)
- **Runs with:** one Docker command

---

## 1. How to run it

### What you need
Just **Docker Desktop** (with Docker Compose, which is included). You do **not**
need Java, Maven, Node, or Angular installed, everything is built inside Docker.

### Steps

1. Open a terminal **on Windows** (PowerShell or Command Prompt). If you use
   WSL, make sure Docker Desktop's WSL integration is turned on, otherwise the
   WSL terminal may not have network access to download images.

2. Go to the project folder (the one that has `docker-compose.yml` in it):
   ```bash
   cd path/to/fsd-take-home-assessment
   ```

3. Build and start everything with one command:
   ```bash
   docker compose up --build
   ```
   The first run downloads some base images and dependencies, so give it a few
   minutes. Later runs are much faster.

4. Wait until you see this line in the logs:
   ```
   Started EventSyncApplication ...
   ```
   That means the backend is ready.

5. Open your browser at:
   **http://localhost:8080**

That's it. You should see the unified meeting list.

### How to stop it
Press `Ctrl + C` in the terminal, then run:
```bash
docker compose down
```

### Handy links

| What you want | Open this |
|---------------|-----------|
| The web app | http://localhost:8080 |
| All meetings (raw API) | http://localhost:8081/api/meetings |
| Just the summary counts | http://localhost:8081/api/stats |
| One meeting by id | http://localhost:8081/api/meetings/M-001 |
| Only meetings that have conflicts | http://localhost:8081/api/meetings?conflictsOnly=true |

> Port **8080** is the website. Port **8081** is the backend API on its own
> (handy for poking at the raw data).

---

## 2. What you'll see in the app

- **Summary cards** at the top (total meetings, matched, CRM-only, etc.).
  **Click any card to filter the list** to just those meetings, click it again
  to go back to all.
- A list of meetings. Each row shows the title, date/time, and small **badges**
  telling you which system(s) it came from (CRM / CALENDAR), plus a red badge if
  there's a conflict.
- **Click a meeting** to expand it. You'll see:
  - a summary,
  - a **conflicts** section (where the two systems disagree),
  - a **field-by-field table** showing the CRM value next to the Calendar value,
    so you can see exactly where each piece of data came from,
  - any **data-quality notes** (e.g. a date that had to be fixed).

---

## 3. How it works (the approach)

The whole thing runs in three steps, which happen automatically when the backend
starts:

### Step 1: Read the data
The app reads both JSON files (`crm_events.json` and `calendar_events.json`).
The files are copied inside the backend image, so nothing external is needed.

### Step 2: Clean it up
Real data is messy, so before comparing anything the app fixes it up and **makes
a note of every fix** (these notes show up in the UI):
- Dates in a weird format (like `03-15/2025`) are corrected to a normal date.
- Calendar times marked with a "Z" are in UTC and get converted to the office's
  local time zone (US Eastern) so they line up with the CRM times.
- Broken emails like `raj.patel[at]atlasvc.com` are repaired.
- Missing fields are flagged instead of causing errors.

### Step 3: Match and combine
The two systems don't share an ID, so the app can't just join them on a key.
Instead it scores how likely two records are the same meeting, based on:
- **same day** (required, this is the strongest signal),
- how close the **start times** are,
- whether the **people** line up (e.g. the CRM company "Meridian Capital"
  matches the email domain `meridiancap.com`, or "David Park" matches
  `david.park@...`),
- whether the **owner/organizer** is the same person,
- how similar the **titles** are.

If the score is high enough, the records are linked. The app also spots
**duplicates inside the same system** and merges them. When records are
combined, it keeps **both systems' values** for every field and marks anything
they disagree on.

---

## 4. Key decisions

- **Show conflicts, don't "fix" them.** This is a tool to help a person decide,
  not to declare one system the winner. So both values are always kept and the
  disagreement is shown.
- **Same day is required for a match.** Two records on different days are never
  treated as the same meeting. Other signals (time, people, title) are combined
  into a score.
- **Group with a graph, not just pairs.** Records are linked into groups, so a
  tricky case like "one CRM record matches two Calendar copies of the same
  meeting" collapses neatly into a single entry.
- **Be honest about time zones.** One calendar record is stored in UTC. After
  converting it to Eastern time it lands an hour away from what the CRM says,
  so the app flags that as a real time conflict instead of pretending it matches.
- **Don't flag harmless differences.** Statuses like "confirmed" vs "completed"
  aren't treated as conflicts, but "cancelled" vs "confirmed" is.

### Settings you can tweak
In `backend/src/main/resources/application.yml`:
- `event-sync.local-zone` – the office time zone used for converting UTC times.
- `event-sync.match.time-window-minutes` – how far apart two start times can be
  and still count as the same meeting (default 150).
- `event-sync.match.min-score` – how confident a match must be to link records
  (default 0.55).

---

## 5. Running it for development (optional, without Docker)

You only need this if you want to change the code and see it live. It needs
**JDK 17** and **Node 18+** installed.

Backend:
```bash
cd backend
mvn spring-boot:run        # runs on http://localhost:8080
```

Frontend (in a second terminal):
```bash
cd frontend
npm install
npm start                  # runs on http://localhost:4200
```
In dev mode the frontend talks to the backend through a built-in proxy.

---

## 6. Project layout

```
fsd-take-home-assessment/
├── docker-compose.yml        # starts both services together
├── README.md                 # this file
├── AI_COLLABORATION.md        # how AI was used while building this
├── data/                     # the original data files
├── backend/                  # Spring Boot API (ingest + reconcile)
└── frontend/                 # Angular web app
```

---

## 7. A note on tests

I didn't add automated tests, the brief said test coverage isn't being judged.
The matching logic is the part most worth testing, and it's written in small,
self-contained pieces so tests could be added easily. With more time I'd add
unit tests for the date/email cleanup and the matching score, plus a "needs
review" view for borderline matches.

---

## 8. Time spent

About **3.5 hours**: roughly 30 minutes understanding the data and its tricky
cases, 1.5 hours on the backend matching logic, 1 hour on the Angular UI, and
30 minutes on Docker setup and this README.
