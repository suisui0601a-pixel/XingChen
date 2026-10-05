# Stickers and slang

Sticker assets are local files. The database stores path, SHA-256, tags, note, usage, source and timestamps only; it never stores image BLOBs. Collection accepts an inbox basename, resolves the canonical path under the inbox, enforces an image extension and size cap, hashes the file, and deduplicates. Sending revalidates the stored asset under the library directory and applies per-turn, cooldown and repetition rules.

Slang submissions are offline `CANDIDATE` records with bounded source/evidence fields. Search excludes rejected records. No web research or automatic confirmation is enabled. Repository and service contracts are local; broader moderation UI remains out of scope.
