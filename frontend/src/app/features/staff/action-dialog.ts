import { Component, inject } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { currencyDigits, MoneyPipe } from '../../shared/money.pipe';

/** What the dialog asks for. One dialog serves request info, assess, settle and reject. */
export interface ActionDialogData {
  title: string;
  /** Shown above the fields, e.g. the consequence of rejecting. */
  warning?: string;
  /** Amount field (assess, settle). The client checks min/max/decimals for quick feedback; the backend checks again. */
  amount?: { label: string; currency: string; sumInsured: number; remainingCover: number; min: number; initial?: number | null };
  text: { label: string; required: boolean; maxLength: number };
  confirmLabel: string;
  /** Confirm button style (FRONTEND-DESIGN.md §7): primary navy (default), positive green (settle) or destructive red (reject). */
  tone?: 'positive' | 'destructive';
}

export interface ActionDialogResult {
  amount: number | null;
  text: string;
}

/**
 * Opened with MatDialog.open(ActionDialog, { data }). Closes with an ActionDialogResult, or undefined on Cancel.
 * The dialog only collects input; the page sends the request, so backend errors appear on the page.
 */
@Component({
  selector: 'app-action-dialog',
  imports: [ReactiveFormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatIconModule, MatInputModule, MoneyPipe],
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <form [formGroup]="form" (ngSubmit)="confirm()">
      <mat-dialog-content>
        @if (data.warning) {
          <!-- destructive: a red box; otherwise plain text with an info icon (a caution, not an error or a success) -->
          <p class="notice" [class.danger]="data.tone === 'destructive'">
            <mat-icon aria-hidden="true">{{ data.tone === 'destructive' ? 'warning_amber' : 'info_outline' }}</mat-icon>{{ data.warning }}
          </p>
        }
        @if (data.amount; as a) {
          <!-- Q31: below the sum insured only on a PROPERTY policy with other settled claims -->
          <p class="muted cover">
            @if (a.remainingCover < a.sumInsured) {
              Remaining cover (maximum payable): <strong class="num">{{ a.remainingCover | money: a.currency }}</strong>
              of {{ a.sumInsured | money: a.currency }} sum insured
            } @else {
              Sum insured (maximum payable): <strong class="num">{{ a.sumInsured | money: a.currency }}</strong>
            }
          </p>
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>{{ a.label }}</mat-label>
            <span matTextPrefix class="muted">{{ a.currency }}&nbsp;</span>
            <input matInput class="num" type="number" formControlName="amount" cdkFocusInitial />
            @if (form.controls.amount.hasError('max')) {
              <mat-error>Can't be more than the remaining cover</mat-error>
            } @else if (form.controls.amount.hasError('min')) {
              <mat-error>Must be {{ a.min === 0 ? '0 or more' : 'more than 0' }}</mat-error>
            } @else if (form.controls.amount.errors?.['decimals'] === 0) {
              <mat-error>Whole {{ a.currency }} only</mat-error>
            } @else if (form.controls.amount.hasError('decimals')) {
              <mat-error>At most 2 decimals</mat-error>
            }
          </mat-form-field>
        }
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>{{ data.text.label }}</mat-label>
          <textarea matInput formControlName="text" rows="4" [maxlength]="data.text.maxLength"></textarea>
        </mat-form-field>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button matButton type="button" mat-dialog-close>Cancel</button>
        <button
          [matButton]="data.tone === 'destructive' ? 'tonal' : 'filled'"
          [class.btn-destructive]="data.tone === 'destructive'"
          [class.btn-positive]="data.tone === 'positive'"
          type="submit"
          [disabled]="form.invalid"
        >
          {{ data.confirmLabel }}
        </button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `
    .notice {
      display: flex;
      gap: 8px;
      align-items: flex-start;
      border-radius: var(--radius-field);
      margin: 0 0 16px;
    }
    .notice mat-icon { color: var(--text-muted); flex-shrink: 0; }
    .notice.danger { padding: 12px 14px; background: var(--negative-soft); color: var(--negative); }
    .notice.danger mat-icon { color: inherit; }
    .cover { margin: 0 0 12px; }
    mat-dialog-content { min-width: min(440px, 80vw); }
  `,
})
export class ActionDialog {
  protected readonly data = inject<ActionDialogData>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<ActionDialog, ActionDialogResult>);

  protected readonly form = inject(FormBuilder).group({
    amount: [this.data.amount?.initial ?? null, this.amountValidators()],
    text: ['', [...(this.data.text.required ? [Validators.required] : []), Validators.maxLength(this.data.text.maxLength)]],
  });

  confirm(): void {
    if (this.form.invalid) return;
    const v = this.form.getRawValue();
    this.ref.close({ amount: v.amount, text: (v.text ?? '').trim() });
  }

  private amountValidators() {
    const a = this.data.amount;
    if (!a) return [];
    // reserve must be > 0 (backend @Positive); a settlement may be 0 (@PositiveOrZero)
    const min = a.min === 0 ? Validators.min(0) : Validators.min(0.01);
    return [Validators.required, min, Validators.max(a.remainingCover), decimals(currencyDigits(a.currency))];
  }
}

/** IDR and VND amounts have no decimals; the others at most 2. */
function decimals(allowed: number) {
  return (control: AbstractControl): ValidationErrors | null => {
    if (control.value == null) return null;
    const used = String(control.value).split('.')[1]?.length ?? 0;
    return used > allowed ? { decimals: allowed } : null;
  };
}
