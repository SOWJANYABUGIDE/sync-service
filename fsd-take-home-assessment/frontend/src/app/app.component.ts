import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MeetingService } from './meeting.service';
import { ReconciliationStats, UnifiedMeeting } from './models';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule],
  template: `
    <header class="topbar">
      <h1>Event Sync Service</h1>
      <p class="subtitle">
        Unified meeting view reconciled from the CRM and Calendar systems.
        Every field is tagged with its source, and disagreements between the two
        systems are flagged.
      </p>
    </header>

    <main class="container">
      <section class="stats" *ngIf="stats as s">
        <button type="button" class="stat" [class.active]="activeFilter === 'all'" (click)="setFilter('all')">
          <span class="num">{{ s.totalMeetings }}</span><span class="label">Unified meetings</span></button>
        <button type="button" class="stat" [class.active]="activeFilter === 'matched'" (click)="setFilter('matched')">
          <span class="num">{{ s.matchedBothSources }}</span><span class="label">Matched (both sources)</span></button>
        <button type="button" class="stat" [class.active]="activeFilter === 'crmOnly'" (click)="setFilter('crmOnly')">
          <span class="num">{{ s.crmOnly }}</span><span class="label">CRM only</span></button>
        <button type="button" class="stat" [class.active]="activeFilter === 'calendarOnly'" (click)="setFilter('calendarOnly')">
          <span class="num">{{ s.calendarOnly }}</span><span class="label">Calendar only</span></button>
        <button type="button" class="stat warn" [class.active]="activeFilter === 'conflicts'" (click)="setFilter('conflicts')">
          <span class="num">{{ s.meetingsWithConflicts }}</span><span class="label">With conflicts</span></button>
        <button type="button" class="stat" [class.active]="activeFilter === 'duplicates'" (click)="setFilter('duplicates')">
          <span class="num">{{ s.duplicateRecordsCollapsed }}</span><span class="label">Duplicates collapsed</span></button>
      </section>

      <div class="toolbar">
        <button [class.active]="activeFilter === 'all'" (click)="setFilter('all')">All meetings</button>
        <button [class.active]="activeFilter === 'conflicts'" (click)="setFilter('conflicts')">Conflicts only</button>
        <span class="hint">Showing: {{ filterLabel() }} · {{ visibleMeetings().length }} of {{ meetings.length }}</span>
      </div>

      <p *ngIf="loading" class="msg">Loading meetings…</p>
      <p *ngIf="error" class="msg err">{{ error }}</p>

      <ul class="meeting-list" *ngIf="!loading && !error">
        <li class="meeting" *ngFor="let m of visibleMeetings()"
            [class.has-conflict]="m.conflicts.length > 0">
          <div class="meeting-head" (click)="toggle(m.id)">
            <div class="title-block">
              <span class="caret">{{ expanded[m.id] ? '▾' : '▸' }}</span>
              <span class="title">{{ m.title || '(untitled)' }}</span>
            </div>
            <div class="meta">
              <span class="when">{{ formatDateTime(m.startTime) }}</span>
              <span class="badge" *ngFor="let src of m.sources" [ngClass]="src.toLowerCase()">{{ src }}</span>
              <span class="badge conflict" *ngIf="m.conflicts.length > 0">
                {{ m.conflicts.length }} conflict{{ m.conflicts.length > 1 ? 's' : '' }}
              </span>
              <span class="badge quality" *ngIf="m.dataQualityNotes.length > 0" title="Data-quality notes">
                ⚑ {{ m.dataQualityNotes.length }}
              </span>
            </div>
          </div>

          <div class="meeting-body" *ngIf="expanded[m.id]">
            <div class="summary-grid">
              <div><span class="k">Date</span><span class="v">{{ m.date || '—' }}</span></div>
              <div><span class="k">Location</span><span class="v">{{ m.location || '—' }}</span></div>
              <div><span class="k">Type</span><span class="v">{{ m.meetingType || '—' }}</span></div>
              <div><span class="k">Status</span><span class="v">{{ m.status || '—' }}</span></div>
              <div><span class="k">Organizer</span><span class="v">{{ m.organizer || '—' }}</span></div>
              <div><span class="k">Match score</span><span class="v">{{ m.sources.length > 1 ? m.matchScore : 'n/a (single source)' }}</span></div>
              <div><span class="k">CRM id</span><span class="v">{{ m.crmId || '—' }}</span></div>
              <div><span class="k">Calendar id(s)</span><span class="v">{{ m.calendarIds.length ? m.calendarIds.join(', ') : '—' }}</span></div>
            </div>

            <div class="conflicts" *ngIf="m.conflicts.length > 0">
              <h4>Conflicts between sources</h4>
              <ul>
                <li *ngFor="let c of m.conflicts">
                  <strong>{{ fieldLabel(c.field) }}:</strong>
                  <span class="src-crm">CRM = {{ format(c.crmValue) }}</span>
                  <span class="vs">vs</span>
                  <span class="src-cal">Calendar = {{ format(c.calendarValue) }}</span>
                </li>
              </ul>
            </div>

            <h4>Field provenance</h4>
            <table class="prov">
              <thead>
                <tr><th>Field</th><th>CRM value</th><th>Calendar value</th></tr>
              </thead>
              <tbody>
                <tr *ngFor="let key of fieldOrder"
                    [class.conflict-row]="m.fields[key]?.conflict">
                  <td class="field">
                    {{ fieldLabel(key) }}
                    <span class="flag" *ngIf="m.fields[key]?.conflict">conflict</span>
                  </td>
                  <td [class.muted]="isEmpty(m.fields[key]?.crmValue)">{{ format(m.fields[key]?.crmValue) }}</td>
                  <td [class.muted]="isEmpty(m.fields[key]?.calendarValue)">{{ format(m.fields[key]?.calendarValue) }}</td>
                </tr>
              </tbody>
            </table>

            <div class="quality" *ngIf="m.dataQualityNotes.length > 0">
              <h4>Data-quality notes</h4>
              <ul>
                <li *ngFor="let note of m.dataQualityNotes">{{ note }}</li>
              </ul>
            </div>
          </div>
        </li>
      </ul>
    </main>
  `,
  styles: [`
    :host { display: block; font-family: system-ui, -apple-system, Segoe UI, Roboto, sans-serif; color: #1f2933; background: #f5f7fa; min-height: 100vh; }
    .topbar { background: #1f2933; color: #fff; padding: 1.25rem 1.5rem; }
    .topbar h1 { margin: 0 0 .25rem; font-size: 1.4rem; }
    .subtitle { margin: 0; max-width: 70ch; color: #cbd2d9; font-size: .9rem; }
    .container { max-width: 980px; margin: 0 auto; padding: 1.25rem; }
    .stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: .75rem; margin-bottom: 1.25rem; }
    .stat { background: #fff; border: 1px solid #e4e7eb; border-radius: 8px; padding: .75rem; display: flex; flex-direction: column; text-align: left; font: inherit; cursor: pointer; transition: border-color .15s, box-shadow .15s; }
    .stat:hover { border-color: #9aa5b1; }
    .stat.active { border-color: #1f2933; box-shadow: 0 0 0 2px rgba(31,41,51,.15); }
    .stat.warn.active { border-color: #b44d12; box-shadow: 0 0 0 2px rgba(180,77,18,.15); }
    .stat .num { font-size: 1.5rem; font-weight: 700; }
    .stat .label { font-size: .75rem; color: #616e7c; }
    .stat.warn .num { color: #b44d12; }
    .toolbar { display: flex; align-items: center; gap: .5rem; margin-bottom: 1rem; }
    .toolbar button { border: 1px solid #cbd2d9; background: #fff; padding: .4rem .8rem; border-radius: 6px; cursor: pointer; font-size: .85rem; }
    .toolbar button.active { background: #1f2933; color: #fff; border-color: #1f2933; }
    .toolbar .hint { font-size: .8rem; color: #616e7c; margin-left: auto; }
    .msg { color: #616e7c; } .msg.err { color: #cf1124; }
    .meeting-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: .6rem; }
    .meeting { background: #fff; border: 1px solid #e4e7eb; border-radius: 8px; overflow: hidden; }
    .meeting.has-conflict { border-left: 4px solid #e6a23c; }
    .meeting-head { display: flex; justify-content: space-between; align-items: center; gap: 1rem; padding: .75rem 1rem; cursor: pointer; }
    .meeting-head:hover { background: #f9fafb; }
    .title-block { display: flex; align-items: center; gap: .5rem; }
    .caret { color: #9aa5b1; width: 1ch; }
    .title { font-weight: 600; }
    .meta { display: flex; align-items: center; gap: .4rem; flex-wrap: wrap; justify-content: flex-end; }
    .when { font-size: .8rem; color: #616e7c; margin-right: .25rem; }
    .badge { font-size: .68rem; font-weight: 700; padding: .15rem .45rem; border-radius: 999px; letter-spacing: .02em; }
    .badge.crm { background: #def7ec; color: #03543f; }
    .badge.calendar { background: #e1effe; color: #1e429f; }
    .badge.conflict { background: #fde8e8; color: #9b1c1c; }
    .badge.quality { background: #fdf6b2; color: #723b13; }
    .meeting-body { padding: .25rem 1rem 1rem; border-top: 1px solid #f0f2f5; }
    .summary-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: .4rem .9rem; margin: .75rem 0; }
    .summary-grid .k { display: block; font-size: .68rem; text-transform: uppercase; color: #9aa5b1; letter-spacing: .04em; }
    .summary-grid .v { font-size: .9rem; }
    h4 { margin: 1rem 0 .4rem; font-size: .85rem; text-transform: uppercase; letter-spacing: .04em; color: #52606d; }
    .conflicts ul { margin: 0; padding-left: 1rem; font-size: .85rem; }
    .conflicts li { margin-bottom: .25rem; }
    .src-crm { color: #03543f; } .src-cal { color: #1e429f; } .vs { color: #9aa5b1; margin: 0 .35rem; }
    table.prov { width: 100%; border-collapse: collapse; font-size: .82rem; }
    table.prov th, table.prov td { text-align: left; padding: .4rem .5rem; border-bottom: 1px solid #f0f2f5; vertical-align: top; }
    table.prov th { color: #9aa5b1; font-size: .7rem; text-transform: uppercase; letter-spacing: .04em; }
    table.prov td.field { font-weight: 600; white-space: nowrap; }
    table.prov .muted { color: #9aa5b1; }
    .conflict-row { background: #fffbeb; }
    .flag { display: inline-block; margin-left: .4rem; font-size: .6rem; font-weight: 700; color: #9b1c1c; background: #fde8e8; padding: .05rem .35rem; border-radius: 4px; text-transform: uppercase; }
    .quality ul { margin: 0; padding-left: 1rem; font-size: .8rem; color: #723b13; }
    .quality li { margin-bottom: .2rem; }
  `]
})
export class AppComponent implements OnInit {
  meetings: UnifiedMeeting[] = [];
  stats?: ReconciliationStats;
  loading = true;
  error = '';
  activeFilter: 'all' | 'matched' | 'crmOnly' | 'calendarOnly' | 'conflicts' | 'duplicates' = 'all';
  expanded: Record<string, boolean> = {};

  readonly fieldOrder = ['title', 'startTime', 'location', 'meetingType', 'status', 'organizer', 'participants', 'notes'];

  private readonly labels: Record<string, string> = {
    title: 'Title',
    startTime: 'Start time',
    location: 'Location',
    meetingType: 'Meeting type',
    status: 'Status',
    organizer: 'Organizer / owner',
    participants: 'Participants',
    notes: 'Notes'
  };

  constructor(private readonly service: MeetingService) {}

  ngOnInit(): void {
    this.service.getStats().subscribe({
      next: (s) => (this.stats = s),
      error: () => {}
    });
    this.service.getMeetings().subscribe({
      next: (m) => { this.meetings = m; this.loading = false; },
      error: () => { this.error = 'Could not load meetings from the API.'; this.loading = false; }
    });
  }

  visibleMeetings(): UnifiedMeeting[] {
    switch (this.activeFilter) {
      case 'matched':
        return this.meetings.filter((m) => m.sources.length > 1);
      case 'crmOnly':
        return this.meetings.filter((m) => m.sources.length === 1 && m.sources.includes('CRM'));
      case 'calendarOnly':
        return this.meetings.filter((m) => m.sources.length === 1 && m.sources.includes('CALENDAR'));
      case 'conflicts':
        return this.meetings.filter((m) => m.conflicts.length > 0);
      case 'duplicates':
        return this.meetings.filter((m) => this.isCollapsed(m));
      default:
        return this.meetings;
    }
  }

  setFilter(filter: AppComponent['activeFilter']): void {
    this.activeFilter = this.activeFilter === filter ? 'all' : filter;
  }

  filterLabel(): string {
    const labels: Record<AppComponent['activeFilter'], string> = {
      all: 'All meetings',
      matched: 'Matched (both sources)',
      crmOnly: 'CRM only',
      calendarOnly: 'Calendar only',
      conflicts: 'With conflicts',
      duplicates: 'Duplicates collapsed'
    };
    return labels[this.activeFilter];
  }

  private isCollapsed(m: UnifiedMeeting): boolean {
    return m.calendarIds.length > 1
      || m.dataQualityNotes.some((n) => n.startsWith('Collapsed'));
  }

  toggle(id: string): void {
    this.expanded[id] = !this.expanded[id];
  }

  fieldLabel(key: string): string {
    return this.labels[key] ?? key;
  }

  isEmpty(value: unknown): boolean {
    return value === null || value === undefined
      || (Array.isArray(value) && value.length === 0)
      || value === '';
  }

  format(value: unknown): string {
    if (this.isEmpty(value)) {
      return '—';
    }
    if (Array.isArray(value)) {
      return value.join(', ');
    }
    return String(value);
  }

  formatDateTime(iso: string | null): string {
    if (!iso) {
      return 'No date';
    }
    const d = new Date(iso);
    if (isNaN(d.getTime())) {
      return iso;
    }
    return d.toLocaleString(undefined, {
      weekday: 'short', month: 'short', day: 'numeric',
      hour: '2-digit', minute: '2-digit'
    });
  }
}
