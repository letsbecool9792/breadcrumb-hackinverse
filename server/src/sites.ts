/**
 * The sites a memory's links point to, named as people say them, so "that
 * link from Instagram" finds an instagram.com link the way it finds a share
 * from the Instagram app: a source is an app or a site.
 */

/** Registrable domains people name differently from their first label. */
const NAMED: Record<string, string> = {
  "instagram.com": "Instagram",
  "instagr.am": "Instagram",
  "youtube.com": "YouTube",
  "youtu.be": "YouTube",
  "twitter.com": "X",
  "x.com": "X",
  "t.co": "X",
  "linkedin.com": "LinkedIn",
  "lnkd.in": "LinkedIn",
  "facebook.com": "Facebook",
  "fb.com": "Facebook",
  "fb.me": "Facebook",
  "whatsapp.com": "WhatsApp",
  "wa.me": "WhatsApp",
  "reddit.com": "Reddit",
  "redd.it": "Reddit",
  "github.com": "GitHub",
  "t.me": "Telegram",
  "telegram.me": "Telegram",
  "amzn.to": "Amazon",
  "amzn.in": "Amazon",
  "pin.it": "Pinterest",
  "tiktok.com": "TikTok",
};

/** Second levels under a country code: "bbc.co.uk", "flipkart.co.in", "abc.net.au". */
const SECOND_LEVELS = new Set(["co", "com", "net", "org", "gov", "edu", "ac", "nic", "res", "gen", "firm", "ind", "ltd", "plc"]);

/** The part of a host someone registered: "m.youtube.com" is youtube.com. */
export function registrableDomain(host: string): string {
  const labels = host.toLowerCase().replace(/\.$/, "").split(".").filter(Boolean);
  if (labels.length <= 2) return labels.join(".");
  const top = labels[labels.length - 1] ?? "";
  const second = labels[labels.length - 2] ?? "";
  const take = top.length === 2 && SECOND_LEVELS.has(second) ? 3 : 2;
  return labels.slice(-take).join(".");
}

/**
 * A site's name: a known one ("YouTube", "X"), else the registrable domain's
 * first label, capitalised ("medium.com" is "Medium"). Null for an address
 * with no name to give: an IP, localhost.
 */
export function siteName(host: string): string | null {
  const domain = registrableDomain(host);
  if (!domain.includes(".") || /^[\d.]+$/.test(domain)) return null;
  const named = NAMED[domain];
  if (named) return named;
  const first = domain.split(".")[0] ?? "";
  return first ? first.charAt(0).toUpperCase() + first.slice(1) : null;
}

const URL_PATTERN = /(?:https?:\/\/|\bwww\.)[^\s"'<>]+/gi;

/** The named sites of every link in [text], once each, in order. */
export function linkSites(text: string | null | undefined): string[] {
  if (!text) return [];
  const sites = new Set<string>();
  for (const match of text.match(URL_PATTERN) ?? []) {
    const host = match
      .replace(/^https?:\/\//i, "")
      .split(/[/?#]/)[0]
      ?.replace(/^[^@]*@/, "")
      .replace(/:\d+$/, "");
    const name = host ? siteName(host) : null;
    if (name) sites.add(name);
  }
  return [...sites];
}
