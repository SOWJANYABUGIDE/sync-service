export type MeetingSource = 'CRM' | 'CALENDAR';

export interface FieldProvenance {
  field: string;
  value: unknown;
  crmValue: unknown;
  calendarValue: unknown;
  conflict: boolean;
}

export interface Conflict {
  field: string;
  crmValue: unknown;
  calendarValue: unknown;
}

export interface UnifiedMeeting {
  id: string;
  title: string;
  date: string | null;
  startTime: string | null;
  endTime: string | null;
  organizer: string | null;
  clientName: string | null;
  clientCompany: string | null;
  location: string | null;
  meetingType: string | null;
  status: string | null;
  recurring: boolean | null;
  participants: string[];
  sources: MeetingSource[];
  crmId: string | null;
  calendarIds: string[];
  matchScore: number;
  fields: Record<string, FieldProvenance>;
  conflicts: Conflict[];
  dataQualityNotes: string[];
}

export interface ReconciliationStats {
  totalMeetings: number;
  matchedBothSources: number;
  crmOnly: number;
  calendarOnly: number;
  meetingsWithConflicts: number;
  duplicateRecordsCollapsed: number;
  crmRecordsIngested: number;
  calendarRecordsIngested: number;
}
