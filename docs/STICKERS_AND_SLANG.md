# Stickers and slang

Sticker assets are local files. The database stores path, SHA-256, tags, note, usage, source and timestamps only; it never stores image BLOBs. Collection accepts an inbox basename, resolves the canonical path under the inbox, enforces an image extension and size cap, hashes the file, and deduplicates. Sending revalidates the stored asset under the library directory and applies per-turn, cooldown and repetition rules.

For authenticated, service-managed batch uploads, follow [Sticker bulk import](STICKER_BULK_IMPORT.md). The upload API is the supported import path because it validates content, computes the digest, and persists tags and metadata together. Do not write directly to the managed library or database.

Slang submissions are offline `CANDIDATE` records with bounded source/evidence fields. Search excludes rejected records. No web research or automatic confirmation is enabled. Repository and service contracts are local; broader moderation UI remains out of scope.
