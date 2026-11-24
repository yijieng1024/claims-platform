import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { Logo } from '../../shared/logo';
import { AuthControllerService } from '../../api/auth';
import { ErrorService } from '../../core/error.service';

/** Only existing policyholders can register: email must match the policy's holder email (design §4). */
@Component({
  selector: 'app-register',
  imports: [Logo, ReactiveFormsModule, RouterLink, MatCardModule, MatFormFieldModule, MatIconModule, MatInputModule, MatButtonModule],
  templateUrl: './register.html',
  styleUrl: './auth-form.scss',
})
export class Register {
  private readonly api = inject(AuthControllerService);
  private readonly errors = inject(ErrorService);
  private readonly router = inject(Router);

  readonly busy = signal(false);
  /** Eye button in the password field: false = dots, true = readable text. */
  readonly showPassword = signal(false);

  // same limits as the backend's RegisterRequest, so most mistakes are caught before sending
  readonly form = inject(FormBuilder).nonNullable.group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
    policyNumber: ['', [Validators.required, Validators.maxLength(30)]],
  });

  submit(): void {
    if (this.form.invalid) return;
    this.busy.set(true);
    this.api.register(this.form.getRawValue()).subscribe({
      next: () =>
        this.router.navigate(['/login'], { queryParams: { message: 'Account created. You can log in now.' } }),
      error: (err) => {
        this.busy.set(false);
        // a wrong policy number or email gives one generic message on purpose; it is shown as is
        this.errors.show(err, this.form);
      },
    });
  }
}
