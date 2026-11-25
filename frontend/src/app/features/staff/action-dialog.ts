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
  amount?: { label: string; currency: string; sumInsured: number; min: number; initial?: number | null };
  text: { label: string; required: boolean; maxLength: number };
  confirmLabel: string;
  /** Styles the confirm button as destructive (FRONTEND-DESIGN.md §7). */
  destructive?: boolean;
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
          <p class="warning" [class.danger]="data.destructive"><mat-icon aria-hidden="true">warning_amber</mat-icon>{{ data.warning }}</p>
        }
        @if (data.amount; as a) {
          <p class="muted sum-insured">
            Sum insured (maximum payable): <strong class="num">{{ a.sumInsured | money: a.currency }}</strong>
          </p>
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>{{ a.label }}</mat-label>
            <span matTextPrefix class="muted">{{ a.currency }}&nbsp;</span>
            <input matInput class="num" type="number" formControlName="amount" cdkFocusInitial />
            @if (form.controls.amount.hasError('max')) {
              <mat-error>Can't be more than the sum insured</mat-error>
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
          [matButton]="data.destructive ? 'tonal' : 'filled'"
          [class.btn-destructive]="data.destructive"
          type="submit"
          [disabled]="form.invalid"
        >
          {{ data.confirmLabel }}
        </button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `
    .warning {
      display: flex;
      gap: 8px;
      align-items: flex-start;
      padding: 12px 14px;
      border-radius: var(--radius-field);
      background: var(--warning-soft);
      color: var(--warning);
      margin: 0 0 16px;
    }
    /* destructive actions (reject) use the negative colour; a final-but-normal step (settle) the warning one */
    .warning.danger { background: var(--negative-soft); color: var(--negative); }
    .sum-insured { margin: 0 0 12px; }
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
    return [Validators.required, min, Validators.max(a.sumInsured), decimals(currencyDigits(a.currency))];
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
