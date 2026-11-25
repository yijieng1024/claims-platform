// The backend sends and expects plain dates as "YYYY-MM-DD" (no time, no time zone).
// `new Date("2026-11-24")` would read that as UTC midnight, which is the previous day in negative-offset
// time zones, so these helpers build and read the date in the user's local time instead.

export function parseLocalDate(isoDate: string): Date {
  const [y, m, d] = isoDate.split('-').map(Number);
  return new Date(y, m - 1, d);
}

export function toIsoDate(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

export function today(): Date {
  const now = new Date();
  return new Date(now.getFullYear(), now.getMonth(), now.getDate());
}

/** How long ago `iso` was, coarse: "45 min", "5 h", "3 d". Used for the queue's waiting time. */
export function waitingTime(iso: string, now = Date.now()): string {
  const minutes = Math.max(0, Math.floor((now - Date.parse(iso)) / 60_000));
  if (minutes < 60) return `${minutes} min`;
  const hours = Math.floor(minutes / 60);
  return hours < 24 ? `${hours} h` : `${Math.floor(hours / 24)} d`;
}
