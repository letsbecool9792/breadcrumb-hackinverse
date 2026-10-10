/**
 * Days on the server's calendar. "From April" means April where the server
 * is, not in UTC: at UTC midnight a screenshot taken at 3 a.m. in India is
 * still in March.
 */

/** YYYY-MM-DD of [date] in the server's time zone. */
export function localDay(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

/** Midnight at the start of a YYYY-MM-DD day, or null when it is no real day ("2026-02-31"). */
export function startOfDay(day: string): Date | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(day);
  if (!match) return null;
  const [year, month, date] = [Number(match[1]), Number(match[2]), Number(match[3])];
  const start = new Date(year, month - 1, date);
  if (start.getFullYear() !== year || start.getMonth() !== month - 1 || start.getDate() !== date) return null;
  return start;
}

/** Midnight at the start of the day after a YYYY-MM-DD day: the end of a range, exclusive. */
export function startOfNextDay(day: string): Date | null {
  const start = startOfDay(day);
  return start && new Date(start.getFullYear(), start.getMonth(), start.getDate() + 1);
}
