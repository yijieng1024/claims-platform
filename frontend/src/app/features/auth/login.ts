import { Component, inject, input, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { Logo } from '../../shared/logo';
import { AuthService } from '../../core/auth.service';
import { ErrorService } from '../../core/error.service';

@Component({
  selector: 'app-login',
  imports: [Logo, ReactiveFormsModule, RouterLink, MatCardModule, MatFormFieldModule, MatIconModule, MatInputModule, MatButtonModule],
  templateUrl: './login.html',
  styleUrl: './auth-form.scss',
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly errors = inject(ErrorService);
  private readonly router = inject(Router);

  /** ?message=... from the URL, e.g. "Session expired" or "Account created" (bound by withComponentInputBinding). */
  readonly message = input<string>();
  readonly busy = signal(false);
  /** Eye button in the password field: false = dots, true = readable text. */
  readonly showPassword = signal(false);

  readonly form = inject(FormBuilder).nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });

  constructor() {
    // After a wrong login both fields are marked. Editing either one means "trying again", so clear the
    // mark on both; otherwise the untouched field would stay red and keep the Log in button disabled.
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      for (const control of [this.form.controls.email, this.form.controls.password]) {
        if (control.hasError('credentials') || control.hasError('server')) {
          control.updateValueAndValidity({ emitEvent: false }); // re-runs the normal validators only
        }
      }
    });
  }

  submit(): void {
    if (this.form.invalid) return;
    this.busy.set(true);
    const { email, password } = this.form.getRawValue();
    this.auth.login(email, password).subscribe({
      next: () => this.router.navigateByUrl(this.auth.homeUrl()),
      error: (err) => {
        this.busy.set(false);
        if (err instanceof HttpErrorResponse && err.status === 401) {
          // Wrong email or password: show the backend's message under the password field and outline both
          // fields in red. The backend never says which one was wrong (on purpose), so both are marked.
          // These errors clear by themselves as soon as the user edits a field (validators run again).
          this.form.controls.email.setErrors({ credentials: true });
          this.form.controls.password.setErrors({ server: this.errors.messageFor(err) });
          this.form.markAllAsTouched();
          return;
        }
        // anything else (e.g. a service is down) is not about the fields: snackbar as usual
        this.errors.show(err, this.form);
      },
    });
  }
}
