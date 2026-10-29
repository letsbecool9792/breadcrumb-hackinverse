# Export and import

Roadmap 4. Everything kept lives on one phone (architecture rule 1), so a lost or replaced phone
loses it all. A backup is everything kept, in one file the person keeps wherever they like (a
drive, a computer); a restore brings it back in, on a new phone or the same one. Nothing goes
anywhere the person didn't pick.

## The file

`breadcrumb-2026-10-27.zip`:

```
breadcrumb.json          first: every memory, its vector, the note said over it,
                         and what the importer has taken in (BackupManifest, format 1)
originals/<name>         every original, by its name in the store; a link's preview picture;
                         a spoken note's recording (<memory id>.spoken.m4a). Stored, not deflated
```

The manifest has its own field names (`backup/BackupFormat`), so renaming a field of `Memory`
doesn't break old backups. A memory's original is named, not pathed: a restore puts it wherever
this phone keeps originals. Vectors are base64.

## Steps

- [x] **B1. Out and in** (Suparno, 2026-10-27)
  - `backup/Backups`: `save` writes the manifest, then every file; `restore` reads the manifest,
    keeps the memories the phone doesn't have (by id), takes in only their files, and only by
    names the store accepts and holds nothing under, then writes their rows, vectors, spoken
    notes and the importer's keys in one transaction. A memory already here is left as it is;
    nothing is replaced or deleted. A restore that stops halfway deletes the files it wrote and
    writes no row. One backup or restore at a time, on the application's scope.
  - Restored, a memory is read, heard and embedded only where it wasn't on the old phone: its
    text and vector come with it, and a vector made by another model is embedded again.
  - Not read: anything without the manifest first, a newer format, a kind this app doesn't know.
    Android 14 and later refuse a zip whose names climb out (`..`) outright.
  - Tests: `BackupFormatTest` (JVM); `BackupsTest` on the phone, between two in-memory databases
    and two stores in the cache.
- [x] **B2. Where it is** (Suparno, 2026-10-27)
  - The masthead's count ends in "BACK UP"; a tap opens "Everything kept" (design §20): how much
    there is, "Save a backup" (the system's save dialog) and "Restore a backup" (its file
    picker). The import panel offers "A breadcrumb backup" too: a new phone starts there.
  - The backup's line over the search field, in the import bar's pill.
  - Tests: `MosaicTest` (the count offers it).
  - *Check:* tap "N KEPT · BACK UP", "Save a backup", save it to Downloads: the bar says "backing up
    · N of M", then "backed up N · X MB". Restore that same file: "nothing new · N already here",
    and nothing changes. On a second phone (or after uninstalling, with the file kept): +, "A
    breadcrumb backup", pick it: every memory comes back with its pictures, PDFs, voice notes and
    spoken notes, and search finds them by meaning at once (their vectors came along).
    Checked 2026-10-29 by Suparno: backed up, uninstalled the debug build, installed v1.0 and
    restored; everything came back.

## Rules

- **A restore only adds.** Never a write over a row or a file that is there, never a delete.
- **Rows last**: the files first, then the rows in one transaction, so a failure leaves nothing.
- **The format is the manifest's**: change `BackedUpMemory` only by adding fields with defaults;
  anything else is format 2, and this app must still read format 1.
- **A test never backs up or restores the app's own database.**
