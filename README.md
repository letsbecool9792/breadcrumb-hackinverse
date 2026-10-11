# Breadcrumb

**A search engine for your own memory.**

Save anything in one tap. Find it later by describing it the way you actually remember it.

---

## The problem

We save information everywhere and find it nowhere.

Screenshots, links, PDFs, photos, recommendations: they end up scattered across a dozen apps.
Later you remember *what* you saved and roughly the context around it, but not *where* you put
it. So you search your gallery, then your bookmarks, then WhatsApp, and eventually give up.

Research on re-finding information bears this out: people fall back on bookmarking (~80%),
searching again from scratch (~54%), re-typing URLs (~51%), even emailing things to themselves
(~36%), and no single method covers everything they need. Existing tools each solve a slice.
None unify personal information retrieval.

## The idea

Breadcrumb is two flows and nothing else:

> **Save → Forget about it → Search → Find**

**Saving is frictionless.** Share sheet or a Quick Settings tile. No folders, no tags, no titles,
no organizing. You are never asked to do work at save time.

**Retrieval is the product.** One search bar. You type what you remember:

- *"that internship screenshot from April"*
- *"the restaurant somebody recommended"*
- *"that guy from the hackathon who worked at Qualcomm"*
- *"the PDF my professor sent"*

It finds the thing even when your words don't match the original content, because it
understands meaning and context, not just keywords.

## Reference memory, not action memory

- **Action memory**: *"I need to **do** something later."* Pay a bill, submit an assignment.
  Calendars, reminders and task managers already do this well. Breadcrumb doesn't compete here.
- **Reference memory**: *"I need to **remember** something later."* A restaurant rec, a useful
  article, an internship posting, a person you met at an event. Nothing needs to happen with
  these. You just want to find them again.

Breadcrumb is only the second one. It is **not** a notes app, a bookmark manager, a task manager,
or a second brain. It should feel like a personal search engine, not a digital notebook.

## How it works

**Saving**

```
share / tile  →  saved on the phone, instantly  →  background work
                                                         ↓
                                     on-device OCR, then one Gemini Flash Lite pass
                                     per batch (summary, kind, entities, dates)
                                                         ↓
                                         embedding → Atlas Vector Search
```

Saving never waits on the network. The original file never permanently leaves your phone: only
extracted text and embeddings go to the cloud.

**Retrieving**

```
"that internship screenshot from April"
              ↓
    query parsing → { words: internship, type: image, date: April }
              ↓
    hybrid search (vector + keyword, fused by rank, date-filtered)
              ↓
    ranked memories → tap to open the original
```

Hybrid rather than pure semantic search, because the queries people actually type are full of
proper nouns: "Qualcomm" needs keyword matching while the rest of the sentence needs meaning.

## Stack

| | |
|---|---|
| **Mobile** | Native Android: Kotlin, Jetpack Compose, Room, WorkManager |
| **Backend** | Node + Express + TypeScript |
| **Database** | MongoDB Atlas + Atlas Vector Search (hybrid: meaning and words, fused by reciprocal rank) |
| **AI** | Gemini Flash Lite (ingest and query parsing), `gemini-embedding-001` |
| **On-device** | ML Kit Text Recognition |

Native Android rather than a cross-platform framework: every capture surface is an Android
platform surface, and every save is a cold start, so a native capture path is the difference
between saving feeling free and saving feeling like a wait.

## Layout

```
android/    Android app (build it with ./gradlew from this folder)
server/     Express API
docs/       the API contract, and one brief per workstream
tools/      generate_icons.py, which draws the launcher and splash icons
```

## Team

Built by Suparno, Ayushi, Dhruba and Aritra. Who builds what:
[`CLAUDE.md`](CLAUDE.md), "Team and workstreams".

## Status

Capture, reading on the phone, the server and the upload queue are in: a save is read, sent,
understood and searchable. The search screens are next. [`CLAUDE.md`](CLAUDE.md) tracks where each
workstream stands.

## Why "Breadcrumb"

From leaving breadcrumbs while walking through a forest. You don't know when you'll want to
return somewhere, so you leave a trail as you go. You drop small pieces of information as you
move through your digital life, then follow the trail back when you need something.
