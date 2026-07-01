import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ReconciliationStats, UnifiedMeeting } from './models';

@Injectable({ providedIn: 'root' })
export class MeetingService {
  private readonly base = '/api';

  constructor(private readonly http: HttpClient) {}

  getMeetings(): Observable<UnifiedMeeting[]> {
    return this.http.get<UnifiedMeeting[]>(`${this.base}/meetings`);
  }

  getStats(): Observable<ReconciliationStats> {
    return this.http.get<ReconciliationStats>(`${this.base}/stats`);
  }
}
