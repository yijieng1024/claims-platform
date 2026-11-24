import { Component, model, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

const MAX_BYTES = 10 * 1024 * 1024;
const ALLOWED = ['image/jpeg', 'image/png', 'application/pdf'];

/**
 * Lets the user pick files and shows the chosen list. Checks size and type up front for quick
 * feedback; the backend checks again (extension, declared type and file content), which is what counts.
 * Usage: <app-file-picker [(files)]="selectedFiles" />
 */
@Component({
  selector: 'app-file-picker',
  imports: [MatButtonModule, MatIconModule],
  template: `
    <!-- the real <input type=file> is hidden; the Material button opens it -->
    <input #input type="file" multiple hidden accept=".jpg,.jpeg,.png,.pdf" (change)="add(input)" />
    <!-- secondary (tonal) style: the page's primary action is its submit button -->
    <button matButton="tonal" type="button" (click)="input.click()">
      <mat-icon>attach_file</mat-icon> Choose files
    </button>
    <span class="muted hint">JPEG, PNG or PDF, max 10 MB each</span>

    @for (problem of problems(); track problem) {
      <div class="error-text">{{ problem }}</div>
    }
    @for (file of files(); track file) {
      <div class="file">
        <mat-icon>description</mat-icon>
        <span>{{ file.name }}</span>
        <button matIconButton type="button" (click)="remove(file)" [attr.aria-label]="'Remove ' + file.name">
          <mat-icon>close</mat-icon>
        </button>
      </div>
    }
  `,
  styles: `
    .hint { margin-left: 12px; font-size: 13px; }
    .file {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-top: 6px;
      padding: 2px 4px 2px 12px;
      border-radius: var(--radius-field);
      background: var(--surface-2);
      max-width: 480px;
    }
    .file span { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .error-text { margin-top: 6px; font-size: 12.5px; }
  `,
})
export class FilePicker {
  // model() = a two-way bindable input: the parent reads and writes the same list
  readonly files = model<File[]>([]);
  readonly problems = signal<string[]>([]);

  add(input: HTMLInputElement): void {
    const picked = Array.from(input.files ?? []);
    input.value = ''; // so picking the same file again still fires (change)
    const problems: string[] = [];
    const ok = picked.filter((f) => {
      const problem = fileProblem(f);
      if (problem) problems.push(`${f.name}: ${problem}`);
      return !problem;
    });
    this.problems.set(problems);
    this.files.update((files) => [...files, ...ok]);
  }

  remove(file: File): void {
    this.files.update((files) => files.filter((f) => f !== file));
  }
}

export function fileProblem(file: File): string | null {
  if (!ALLOWED.includes(file.type)) return 'only JPEG, PNG and PDF files are allowed';
  if (file.size > MAX_BYTES) return 'larger than 10 MB';
  if (file.size === 0) return 'the file is empty';
  return null;
}
