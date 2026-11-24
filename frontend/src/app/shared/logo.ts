import { Component, input } from '@angular/core';

/** The logo mark (FRONTEND-DESIGN.md §4): a white bold "C" on a navy gradient rounded square. */
@Component({
  selector: 'app-logo',
  template: `<span class="logo" [class.large]="large()" aria-hidden="true">C</span>`,
  styles: `
    .logo {
      display: inline-grid;
      place-items: center;
      width: 32px;
      height: 32px;
      border-radius: 10px;
      background: linear-gradient(135deg, var(--brand), var(--brand-light));
      color: var(--on-brand);
      font-weight: 800;
      font-size: 17px;
    }
    .large {
      width: 44px;
      height: 44px;
      border-radius: 14px;
      font-size: 22px;
    }
  `,
})
export class Logo {
  readonly large = input(false);
}
