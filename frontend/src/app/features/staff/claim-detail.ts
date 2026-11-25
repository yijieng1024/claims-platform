import { Component, computed, inject, input, OnInit, signal } from '@angular/core';
import { DatePipe, TitleCasePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Observable, forkJoin } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import {
  ClaimControllerService,
  ClaimDetail,
  DocumentControllerService,
  DocumentView,
  HistoryEntry,
  InformationRequestView,
} from '../../api/claims';
import { AuthService } from '../../core/auth.service';
import { ErrorService } from '../../core/error.service';
import { saveBlob } from '../../shared/download';
import { MoneyPipe } from '../../shared/money.pipe';
import { StatusChip } from '../../shared/status-chip';
import { Timeline } from '../../shared/timeline';
import { ActionDialog, ActionDialogData, ActionDialogResult } from './action-dialog';
import { allowedActions } from './staff-actions';

interface ClaimData {
  claim: ClaimDetail;
  history: HistoryEntry[];
  documents: DocumentView[];
  requests: InformationRequestView[];
}

/**
 * Claim detail for staff, shared by officers (/officer/claims/:id) and managers (/manager/claims/:id).
 * Managers get the same page read-only: allowedActions() returns nothing for them.
 */
@Component({
  selector: 'app-staff-claim-detail',
  imports: [
    RouterLink, DatePipe, TitleCasePipe, MatButtonModule, MatCardModule, MatIconModule, MatProgressBarModule,
    MatTableModule, MoneyPipe, StatusChip, Timeline,
  ],
  templateUrl: './claim-detail.html',
  // the claimant detail's layout classes (title, amounts, facts, ...) plus a few staff-only ones
  styleUrls: ['../claimant/claim-detail.scss', './claim-detail.scss'],
})
export class StaffClaimDetail implements OnInit {
  private readonly claimsApi = inject(ClaimControllerService);
  private readonly documentsApi = inject(DocumentControllerService);
  private readonly errors = inject(ErrorService);
  private readonly dialog = inject(MatDialog);
  protected readonly auth = inject(AuthService);

  /** The :id from the URL. */
  readonly id = input.required<string>();

  readonly data = signal<ClaimData | null>(null);
  readonly busy = signal(false);
  /** The first load failed (e.g. 404: another market's claim). Stops the progress bar; the reason is in the snackbar. */
  readonly loadFailed = signal(false);
  readonly documentColumns = ['originalFilename', 'uploadedAt', 'download'];

  readonly claim = computed(() => this.data()?.claim);
  /** Back to the officer's own list or the manager's All claims. */
  readonly backUrl = computed(() => `${this.auth.homeUrl()}/claims`);

  // The UI only hides buttons the user can't use; the backend enforces every rule again (permission,
  // assignment, state machine) and its 403/409 is shown if they ever disagree.
  readonly actions = computed(() => {
    const claim = this.claim();
    return claim ? allowedActions(claim, this.auth.user()) : [];
  });
  readonly assignedToMe = computed(() => !!this.claim() && this.claim()!.assignedOfficerId === this.auth.user()?.id);

  ngOnInit(): void {
    this.load();
  }

  /** (Re)reads the claim from claims-service; called after every action so the page shows the real state (design §3). */
  load(): void {
    const id = this.id();
    forkJoin({
      claim: this.claimsApi.get(id),
      history: this.claimsApi.history(id),
      documents: this.documentsApi.list(id),
      requests: this.claimsApi.informationRequests(id),
    }).subscribe({
      next: (data) => this.data.set(data),
      error: (err) => {
        this.loadFailed.set(true);
        this.errors.show(err);
      },
    });
  }

  pickUp(): void {
    this.run(this.claimsApi.assign(this.id()), 'Claim picked up.');
  }

  requestInfo(): void {
    this.ask({
      title: 'Request information',
      text: { label: 'Question for the claimant', required: true, maxLength: 2000 },
      confirmLabel: 'Send request',
    }, (r) => this.run(this.claimsApi.requestInformation(this.id(), { question: r.text }), 'Information requested.'));
  }

  assess(claim: ClaimDetail): void {
    this.ask({
      title: claim.status === 'ASSESSED' ? 'Update reserve' : 'Assess claim',
      amount: { label: 'Reserve amount', currency: claim.currency, sumInsured: claim.sumInsured, min: 0.01, initial: claim.reserveAmount },
      text: { label: 'Note (optional)', required: false, maxLength: 2000 },
      confirmLabel: 'Save assessment',
    }, (r) => this.run(this.claimsApi.assess(this.id(), { reserveAmount: r.amount!, note: r.text || null }), 'Assessment saved.'));
  }

  settle(claim: ClaimDetail): void {
    this.ask({
      title: 'Settle claim',
      warning: 'Settling closes the claim. This can’t be undone.',
      amount: { label: 'Settled amount', currency: claim.currency, sumInsured: claim.sumInsured, min: 0, initial: claim.reserveAmount },
      text: { label: 'Note (optional)', required: false, maxLength: 2000 },
      confirmLabel: 'Settle claim',
    }, (r) => this.run(this.claimsApi.settle(this.id(), { settledAmount: r.amount!, note: r.text || null }), 'Claim settled.'));
  }

  /** Destructive: the dialog is the confirmation step (§7), with the consequence spelled out. */
  reject(): void {
    this.ask({
      title: 'Reject claim',
      warning: 'Rejecting closes the claim and the claimant is told the reason. This can’t be undone.',
      text: { label: 'Reason', required: true, maxLength: 2000 },
      confirmLabel: 'Reject claim',
      destructive: true,
    }, (r) => this.run(this.claimsApi.reject(this.id(), { reason: r.text }), 'Claim rejected.'));
  }

  download(doc: DocumentView): void {
    this.documentsApi.content(this.id(), doc.id).subscribe({
      next: (blob) => saveBlob(blob, doc.originalFilename),
      error: (err) => this.errors.show(err),
    });
  }

  /** Opens the action dialog; calls `then` only if the user confirmed. */
  private ask(data: ActionDialogData, then: (result: ActionDialogResult) => void): void {
    if (this.dialog.openDialogs.length) return; // a quick double click must not open two dialogs
    this.dialog
      .open<ActionDialog, ActionDialogData, ActionDialogResult>(ActionDialog, { data, autoFocus: 'first-tabbable' })
      .afterClosed()
      .subscribe((result) => result && then(result));
  }

  /** Sends an action, then re-reads the claim whether it worked or not (a 409 means our copy was stale). */
  private run(request: Observable<unknown>, done: string): void {
    this.busy.set(true);
    request.subscribe({
      next: () => {
        this.errors.snack(done);
        this.busy.set(false);
        this.load();
      },
      error: (err) => {
        this.errors.show(err);
        this.busy.set(false);
        this.load();
      },
    });
  }
}
