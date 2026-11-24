import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { FormGroup } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';

/** The backend's error body (RFC 7807 ProblemDetail). `errors` is only present on validation failures. */
interface ProblemDetail {
  title?: string;
  detail?: string;
  errors?: Record<string, string>;
}

/**
 * The one place that turns a failed HTTP call into something the user sees:
 * validation errors appear under the matching form fields, everything else in a snackbar.
 */
@Injectable({ providedIn: 'root' })
export class ErrorService {
  private readonly snackBar = inject(MatSnackBar);

  /** Shows the error: on the form's fields if the backend named them, otherwise in a snackbar. */
  show(err: unknown, form?: FormGroup): void {
    const errors = problemOf(err).errors;
    if (form && errors && this.showFieldErrors(form, errors)) return;
    const message = this.messageFor(err);
    if (message) this.snack(message);
  }

  /** A user-facing sentence for the error, or null if nothing should be shown. */
  messageFor(err: unknown): string | null {
    if (!(err instanceof HttpErrorResponse)) {
      console.error(err);
      return 'Something went wrong.';
    }
    // A 401 on any call except login has already sent the user to the login page ("Session expired").
    if (err.status === 401 && !err.url?.endsWith('/api/auth/login')) return null;
    // 0 = no answer at all; 502/504 = the dev proxy could not reach the service
    if ([0, 502, 503, 504].includes(err.status)) return 'A service is temporarily unavailable, please try again.';
    const problem = problemOf(err);
    return problem.detail ?? problem.title ?? `Request failed (${err.status}).`;
  }

  snack(message: string): void {
    this.snackBar.open(message, 'Close', { duration: 8000 });
  }

  /** Puts each backend field message on the form control with the same name. True if all fields matched. */
  private showFieldErrors(form: FormGroup, errors: Record<string, string>): boolean {
    let allMatched = true;
    for (const [field, message] of Object.entries(errors)) {
      const control = form.get(field);
      if (control) {
        control.setErrors({ server: message });
        control.markAsTouched();
      } else {
        allMatched = false;
      }
    }
    return allMatched;
  }
}

function problemOf(err: unknown): ProblemDetail {
  // a failed Blob download carries its error body as a Blob, which can't be read synchronously: no details then
  const body = err instanceof HttpErrorResponse ? err.error : null;
  return body && typeof body === 'object' && !(body instanceof Blob) ? body : {};
}
