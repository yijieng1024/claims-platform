import { Component, OnDestroy, inject, signal } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { DocumentControllerService, DocumentView } from '../api/claims';
import { ErrorService } from '../core/error.service';
import { saveBlob } from './download';

interface PreviewData {
  claimId: string;
  doc: DocumentView;
}

/** Only images can be shown in the pop-up; PDFs are downloaded as before. */
export function isImage(doc: DocumentView): boolean {
  return doc.contentType.startsWith('image/');
}

/** Opens the pop-up for an image document. Usage: openImagePreview(dialog, claimId, doc). */
export function openImagePreview(dialog: MatDialog, claimId: string, doc: DocumentView): void {
  dialog.open<ImagePreviewDialog, PreviewData>(ImagePreviewDialog, {
    data: { claimId, doc },
    maxWidth: '90vw',
    autoFocus: 'dialog', // focus the dialog itself, not the Download button
  });
}

/**
 * Shows an uploaded image in a dialog. The file is fetched through HttpClient (so the interceptor adds
 * the token; a plain <img src="/api/..."> would not send it) and shown from memory as an object URL,
 * which is released again when the dialog closes.
 */
@Component({
  selector: 'app-image-preview-dialog',
  imports: [MatDialogModule, MatButtonModule, MatIconModule, MatProgressBarModule],
  template: `
    <h2 mat-dialog-title class="title">{{ data.doc.originalFilename }}</h2>
    <mat-dialog-content class="content">
      @if (url(); as url) {
        <img [src]="url" [alt]="'Uploaded image ' + data.doc.originalFilename" />
      } @else if (!failed()) {
        <mat-progress-bar mode="indeterminate" />
      } @else {
        <p class="muted">The image could not be loaded.</p>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button matButton="tonal" (click)="download()" [disabled]="!blob"><mat-icon>download</mat-icon>Download</button>
      <button matButton="filled" mat-dialog-close>Close</button>
    </mat-dialog-actions>
  `,
  styles: `
    .title { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .content { display: grid; place-items: center; min-width: min(360px, 80vw); }
    img {
      display: block;
      max-width: 100%;
      max-height: 70vh;
      object-fit: contain;
      border-radius: var(--radius-field);
      background: var(--surface-2);
    }
  `,
})
export class ImagePreviewDialog implements OnDestroy {
  protected readonly data = inject<PreviewData>(MAT_DIALOG_DATA);
  private readonly errors = inject(ErrorService);

  readonly url = signal<string | null>(null);
  readonly failed = signal(false);
  protected blob: Blob | null = null;

  constructor() {
    inject(DocumentControllerService)
      .content(this.data.claimId, this.data.doc.id)
      .subscribe({
        next: (blob) => {
          this.blob = blob;
          this.url.set(URL.createObjectURL(blob));
        },
        error: (err) => {
          this.failed.set(true);
          this.errors.show(err);
        },
      });
  }

  download(): void {
    if (this.blob) saveBlob(this.blob, this.data.doc.originalFilename);
  }

  ngOnDestroy(): void {
    const url = this.url();
    if (url) URL.revokeObjectURL(url); // free the memory held by the image
  }
}
