import { Component, computed, inject, input, OnInit, signal } from '@angular/core';
import { DatePipe, TitleCasePipe } from '@angular/common';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { firstValueFrom, forkJoin } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
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
import { ErrorService } from '../../core/error.service';
import { saveBlob } from '../../shared/download';
import { FilePicker } from '../../shared/file-picker';
import { MoneyPipe } from '../../shared/money.pipe';
import { StatusChip } from '../../shared/status-chip';
import { Timeline } from '../../shared/timeline';
import { uploadFiles, UploadError } from './upload-files';

/** Everything the page shows, loaded together. */
interface ClaimData {
  claim: ClaimDetail;
  history: HistoryEntry[];
  documents: DocumentView[];
  requests: InformationRequestView[];
}

@Component({
  selector: 'app-claim-detail',
  imports: [
    ReactiveFormsModule, RouterLink, DatePipe, TitleCasePipe, MatButtonModule, MatCardModule, MatFormFieldModule,
    MatIconModule, MatInputModule, MatProgressBarModule, MatTableModule, FilePicker, MoneyPipe, StatusChip, Timeline,
  ],
  templateUrl: './claim-detail.html',
  styleUrl: './claim-detail.scss',
})
export class ClaimDetailPage implements OnInit {
  private readonly claimsApi = inject(ClaimControllerService);
  private readonly documentsApi = inject(DocumentControllerService);
  private readonly errors = inject(ErrorService);

  /** The :id from the URL (route parameters become inputs, see withComponentInputBinding in app.config.ts). */
  readonly id = input.required<string>();

  readonly data = signal<ClaimData | null>(null);
  readonly busy = signal(false);
  readonly documentColumns = ['originalFilename', 'uploadedAt', 'request', 'download'];

  readonly claim = computed(() => this.data()?.claim);
  readonly openRequest = computed(() => this.claim()?.openInformationRequest);
  readonly closed = computed(() => ['SETTLED', 'REJECTED'].includes(this.claim()?.status ?? ''));

  // answer to the open information request
  readonly responseText = new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(4000)] });
  readonly responseFiles = signal<File[]>([]);

  // "upload more documents"
  readonly extraFiles = signal<File[]>([]);

  ngOnInit(): void {
    // inputs are set before ngOnInit, not yet in the constructor
    this.load();
  }

  /** (Re)reads the claim from claims-service. Called after every action so the page is always current. */
  load(): void {
    const id = this.id();
    forkJoin({
      claim: this.claimsApi.get(id),
      history: this.claimsApi.history(id),
      documents: this.documentsApi.list(id),
      requests: this.claimsApi.informationRequests(id),
    }).subscribe({
      next: (data) => this.data.set(data),
      error: (err) => this.errors.show(err),
    });
  }

  /** "Answer to: <question>" for documents uploaded with a response. */
  requestQuestion(requestId: string | null | undefined): string | undefined {
    return this.data()?.requests.find((r) => r.id === requestId)?.question;
  }

  /** Upload the files first (linked to the request); only if all succeed, send the text response. */
  async respond(request: InformationRequestView): Promise<void> {
    if (this.responseText.invalid) return;
    this.busy.set(true);
    try {
      await uploadFiles(this.documentsApi, this.id(), this.responseFiles, request.id);
      await firstValueFrom(this.claimsApi.respond(this.id(), request.id, { responseText: this.responseText.value }));
      this.responseText.reset();
      this.errors.snack('Your response was sent.');
    } catch (e) {
      // files that did upload are already removed from the list, so "Send" again only retries the rest
      this.showFailure(e, 'Your response was not sent.');
    }
    this.busy.set(false);
    this.load();
  }

  async uploadMore(): Promise<void> {
    this.busy.set(true);
    try {
      await uploadFiles(this.documentsApi, this.id(), this.extraFiles);
      this.errors.snack('Documents uploaded.');
    } catch (e) {
      this.showFailure(e);
    }
    this.busy.set(false);
    this.load();
  }

  download(doc: DocumentView): void {
    this.documentsApi.content(this.id(), doc.id).subscribe({
      next: (blob) => saveBlob(blob, doc.originalFilename),
      error: (err) => this.errors.show(err),
    });
  }

  private showFailure(e: unknown, suffix = ''): void {
    if (e instanceof UploadError) {
      const reason = this.errors.messageFor(e.httpError);
      if (reason === null) return; // session expired: already on the login page
      this.errors.snack(`"${e.file.name}" could not be uploaded: ${reason} ${suffix}`.trim());
    } else {
      this.errors.show(e);
    }
  }
}
