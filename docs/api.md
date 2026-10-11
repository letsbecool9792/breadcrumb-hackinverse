# The phone–server API

The contract between the Android app and the server. The server's types are in
[`server/src/api.ts`](../server/src/api.ts); the phone's are hand-written Kotlin data classes
matching them. **Change all three together.**

In development the phone reaches the server as `http://localhost:3000` through
`adb reverse tcp:3000 tcp:3000`. Everything is JSON, requests and answers. No authentication yet:
the server listens on 127.0.0.1 only (see CLAUDE.md, "Also in V1").

## Conventions

- **Ids** are the phone's own UUIDs, generated at save time. Every write is keyed on them, so
  sending the same memory twice replaces it rather than duplicating it.
- **Times** go to the server as epoch milliseconds, and come back as ISO 8601 strings.
- **Unknown fields are dropped**, never stored. In particular `localUri` (where the original lives
  on the phone) can never reach the cloud, whatever the phone sends (architecture rule 1).
- **Status codes**, and what the phone does with each:

| Code | Means | The phone |
|---|---|---|
| 200 | Done | marks it done (SYNCED, deleted, copied) |
| 400 | The request is wrong | does not send it again as it is: a memory goes to FAILED |
| 501 | Not built yet | tries again later |
| 502 | A model refused outright | shows the failure; does not retry at once |
| 503 | Try again later: a model was rate-limited or overloaded | keeps it queued, with backoff |
| 404 | No such path | (JSON, never an HTML page) |

**A model call that may succeed later answers 503, never 200.** A 200 marks a memory synced on the
phone, and nothing enriches it afterwards: the phone sending it again *is* the retry.

## GET /health

```json
{ "service": "breadcrumb", "status": "ok" }
```

Touches no database: it is a liveness check. The phone checks `service`, so another dev server
holding port 3000 is not mistaken for this one.

## POST /memories

New and changed memories, as an array of 1 to 50 (the phone sends 10). A single object is
accepted too, for curl, and answered with a single result.

```json
[
  {
    "id": "6f1c0e4e-…",
    "type": "LINK",
    "hasLink": true,
    "capturedAt": 1767225600000,
    "contentCreatedAt": null,
    "sourceApp": "com.whatsapp",
    "sourceAppLabel": "WhatsApp",
    "title": "Software Engineering Intern, Qualcomm",
    "rawText": "https://www.linkedin.com/jobs/view/4120938817",
    "extractedText": "Apply by April 30. Bengaluru. …",
    "note": "Priya says apply early",
    "updatedAt": 1767225600000
  }
]
```

| Field | Required | Notes |
|---|---|---|
| `id` | yes | non-empty, at most 200 characters |
| `type` | yes | `TEXT`, `LINK`, `IMAGE`, `PDF` or `AUDIO` |
| `capturedAt` | yes | when it was saved |
| `hasLink` | no | true when the shared text carries a URL, whatever the type |
| `contentCreatedAt` | no | when a picture was taken, when known |
| `updatedAt` | no | the phone's last change; defaults to `capturedAt` |
| `sourceApp`, `sourceAppLabel` | no | package and name of the app it was shared from |
| `title`, `rawText`, `extractedText`, `note` | no | strings or null; blank becomes null |

Answer, 200 or 503 (503 when any result is `retryable`; every memory is stored either way):

```json
{ "results": [ { "id": "6f1c0e4e-…", "enriched": true, "embedded": true } ] }
```

A result may carry `reason` (why the model work did not finish) and `retryable: true`. A 400
carries `{ "error": "invalid memory", "invalid": [ { "index": 2, "errors": ["…"] } ] }`.

What the server does: stores every memory, even when the model call fails (`enrichmentError`);
reads a batch's text with **one** Gemini call; embeds it; derives `datedAt` and `linkSites`. A
re-send whose text has not changed costs no model call. The `note` is embedded and word-indexed,
but never given to the model to describe.

## POST /memories/images

Pictures, for memories the server already holds, sent only when on-device OCR could barely read
them (under 80 characters). An array of 1 to 8 (the phone sends 4), each downscaled by the phone
(long side 1,500 px, JPEG):

```json
[ { "id": "6f1c0e4e-…", "mimeType": "image/jpeg", "data": "<base64>" } ]
```

`mimeType` is `image/jpeg`, `image/png` or `image/webp`. Answered like `POST /memories`
(`{ "results": [...] }`, 200 or 503). An id the server does not hold comes back with
`reason: "no such memory"`. The picture is **never written anywhere**, not even a temp file; the
server keeps only what the model read (`readText`), and the same bytes are never read twice.

## POST /memories/enrichment

What the model made of memories the phone has sent, so the phone keeps a copy: a memory opened
from the home screen shows its summary without a search, and is found by those words offline.

```json
{ "ids": ["6f1c0e4e-…", "…"] }
```

1 to 100 ids (the phone asks 50 at a time). Answer:

```json
{ "results": [ { "id": "6f1c0e4e-…", "summary": "LinkedIn post: Qualcomm SWE internship, apply by April 30", "kind": "job posting", "readText": null } ] }
```

An id the server does not hold is absent. One held but not enriched yet comes back with nulls,
which is an answer too: the phone asks again only after it next sends that memory.

## POST /memories/delete

Memories deleted on the phone. A delete that stayed on the phone would leave the text and its
vector in the cloud.

```json
{ "ids": ["6f1c0e4e-…"] }
```

1 to 100 ids. Answer `{ "deleted": 1 }`. Idempotent: an id already gone, or never sent, is simply
not counted, so the phone can send the same delete again after a failure.

## POST /memories/forget

Deletes every memory the server holds. The phone calls it when the person turns the server off.

```json
{}
```

Answer:

```json
{ "deleted": 42 }
```

Idempotent: returns `{ "deleted": 0 }` when nothing remains.

## POST /parse

Takes a search phrase and candidate source app names, returning only the parsed interpretation
without embedding the query or running an Atlas search. Reuses the once-a-day cache per phrase
and source list.

```json
{
  "phrase": "internship screenshot from april",
  "sources": ["WhatsApp", "LinkedIn"]
}
```

Answer (200, 502 or 503):

```json
{
  "interpretation": {
    "query": "internship",
    "types": ["IMAGE"],
    "from": "2026-04-01",
    "to": "2026-04-30",
    "sourceApp": null
  }
}
```

A missing or bad phrase (not 1 to 500 characters) or bad `sources` (not an array of strings)
answers 400. A model rate limit or transient failure answers 503; a model refusal answers 502.

## GET /search?q=…&limit=…

A ranked list of memories, never an answer: each result is a way back to the original on the
phone. `q` is 1 to 500 characters; `limit` is 1 to 50, default 20.

```json
{
  "query": "that internship screenshot from april",
  "interpretation": { "query": "internship", "types": ["IMAGE"], "from": "2026-04-01", "to": "2026-04-30", "sourceApp": null },
  "results": [
    {
      "id": "6f1c0e4e-…",
      "score": 0.0325,
      "ranks": { "vector": 1, "text": 2 },
      "type": "IMAGE",
      "hasLink": false,
      "capturedAt": "…",
      "datedAt": "…",
      "sourceAppLabel": "WhatsApp",
      "title": null,
      "rawText": null,
      "extractedText": "Qualcomm is hiring … apply by April 30",
      "note": null,
      "readText": null,
      "summary": "LinkedIn post: Qualcomm SWE internship, apply by April 30",
      "kind": "job posting"
    }
  ]
}
```

- `interpretation` is how the phrase was taken apart: what is left to search by meaning and
  words, and the filters it asked for. It is null, with `interpretationError` saying why, when
  parsing failed; the search then ran on the raw phrase, unfiltered. A failed parse never fails
  the search.
- `score` orders results and means nothing across searches: about 0.016 is first place in one
  half, about 0.033 first in both. It is null when the phrase was all filter ("screenshots from
  April"), and results are then newest first.
- `ranks` says where a result placed by meaning (`vector`) and by words (`text`): the most a result
  says about why it matched.
- A `LINK` filter matches anything carrying a link (`type = LINK` or `hasLink`).
- The text comes back whole; the vector never leaves the server.

Errors: 400 `{ "error": "…" }` for a missing or bad `q` or `limit`; 503
`{ "error": "could not read the search", "reason": "…", "retryable": true }` when asking again
shortly may work; 502 when the embedding model refused outright. The phone falls back to its own
word search while the server is out of reach.

## What the phone sends, in what order

The upload queue (sync workstream), on each run:

1. Pending deletes.
2. Unread links' pages, read **on the phone** (never by the server).
3. Memories, oldest first, ten at a time. Held back for now: those whose capture sheet is still
   open, and unread pictures and PDFs while on-device OCR is still working through them.
4. Pictures OCR could barely read, four at a time, once each, after their memory is stored.
5. Enrichment, for synced memories whose copy is stale.

The server never logs a search's query string.
