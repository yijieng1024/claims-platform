import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { DatePipe, TitleCasePipe } from '@angular/common';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatRadioModule } from '@angular/material/radio';
import { ClaimControllerService, DocumentControllerService, Policy, PolicyControllerService } from '../../api/claims';
import { ErrorService } from '../../core/error.service';
import { parseLocalDate, toIsoDate, today } from '../../shared/dates';
import { FilePicker } from '../../shared/file-picker';
import { currencyDigits } from '../../shared/money.pipe';
import { uploadFiles, UploadError } from './upload-files';

@Component({
  selector: 'app-new-claim',
  imports: [
    ReactiveFormsModule, RouterLink, DatePipe, TitleCasePipe, MatButtonModule, MatCardModule, MatDatepickerModule,
    MatFormFieldModule, MatIconModule, MatInputModule, MatProgressBarModule, MatRadioModule, FilePicker,
  ],
  templateUrl: './new-claim.html',
  styleUrl: './new-claim.scss',
})
export class NewClaim {
  private readonly claimsApi = inject(ClaimControllerService);
  private readonly documentsApi = inject(DocumentControllerService);
  private readonly errors = inject(ErrorService);
  private readonly router = inject(Router);

  readonly policies = signal<Policy[] | null>(null); // null = still loading
  readonly files = signal<File[]>([]);
  readonly busy = signal(false);

  readonly form = inject(FormBuilder).nonNullable.group({
    policyId: ['', Validators.required],
    incidentDate: [null as Date | null, Validators.required],
    incidentLocation: ['', [Validators.required, Validators.maxLength(255)]],
    description: ['', [Validators.required, Validators.maxLength(4000)]],
    claimedAmount: [null as number | null, [Validators.required, Validators.min(0.01), (c: AbstractControl) => this.checkDecimals(c)]],
  });

  // toSignal turns the form control's stream of values into a signal, so computed() can react to it
  readonly policyId = toSignal(this.form.controls.policyId.valueChanges, { initialValue: '' });
  readonly policy = computed(() => this.policies()?.find((p) => p.id === this.policyId()));

  // the incident date must be inside the policy's cover period and not in the future (design §5)
  readonly minDate = computed(() => (this.policy() ? parseLocalDate(this.policy()!.startDate) : null));
  readonly maxDate = computed(() => {
    const p = this.policy();
    if (!p) return null;
    const end = parseLocalDate(p.endDate);
    return end < today() ? end : today();
  });

  constructor() {
    inject(PolicyControllerService).myPolicies().subscribe({
      // cancelled policies can't be claimed on (the backend refuses them), so they aren't offered
      next: (all) => this.policies.set(all.filter((p) => p.status !== 'CANCELLED')),
      error: (err) => {
        this.policies.set([]);
        this.errors.show(err);
      },
    });
    this.form.controls.policyId.valueChanges.subscribe(() => {
      // a date picked for the previous policy may be outside the new one's cover period
      this.form.controls.incidentDate.reset();
      this.form.controls.claimedAmount.updateValueAndValidity();
    });
  }

  submit(): void {
    if (this.form.invalid) return;
    this.busy.set(true);
    const v = this.form.getRawValue();
    this.claimsApi
      .open({
        policyId: v.policyId,
        incidentDate: toIsoDate(v.incidentDate!),
        incidentLocation: v.incidentLocation,
        description: v.description,
        claimedAmount: v.claimedAmount!,
      })
      .subscribe({
        next: (claim) => this.uploadThenOpen(claim.id, claim.claimNumber),
        error: (err) => {
          this.busy.set(false);
          this.errors.show(err, this.form);
        },
      });
  }

  /** The claim exists now. Upload any attachments; if one fails, still go to the claim, where it can be retried. */
  private async uploadThenOpen(claimId: string, claimNumber: string): Promise<void> {
    try {
      await uploadFiles(this.documentsApi, claimId, this.files);
      this.errors.snack(`Claim ${claimNumber} submitted.`);
    } catch (e) {
      const err = e as UploadError;
      this.errors.snack(
        `Claim ${claimNumber} was submitted, but "${err.file.name}" could not be uploaded: ` +
          `${this.errors.messageFor(err.httpError)} You can upload it again on this page.`,
      );
    }
    this.router.navigate(['/claims', claimId]);
  }

  /** IDR and VND amounts have no decimals; the others at most 2. */
  private checkDecimals(control: AbstractControl): ValidationErrors | null {
    // `?.`: this validator first runs while the form is being built, before `policy` exists
    const currency = this.policy?.()?.currency;
    const value = control.value as number | null;
    if (value == null || !currency) return null;
    const decimals = String(value).split('.')[1]?.length ?? 0;
    const allowed = currencyDigits(currency);
    return decimals > allowed ? { decimals: allowed } : null;
  }
}
