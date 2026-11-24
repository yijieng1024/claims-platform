import { Component, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Logo } from '../../shared/logo';
import { AuthService } from '../../core/auth.service';
import { ErrorService } from '../../core/error.service';

@Component({
  selector: 'app-login',
  imports: [Logo, ReactiveFormsModule, RouterLink, MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule],
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

  readonly form = inject(FormBuilder).nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });

  submit(): void {
    if (this.form.invalid) return;
    this.busy.set(true);
    const { email, password } = this.form.getRawValue();
    this.auth.login(email, password).subscribe({
      next: () => this.router.navigateByUrl(this.auth.homeUrl()),
      error: (err) => {
        this.busy.set(false);
        this.errors.show(err, this.form);
      },
    });
  }
}
