# Media assets (Phase 4B-4b)

## Sticker storage boundary

- Product default root: `/data/assets/stickers/` (`xingchen.sticker.root`). Local and E2E profiles override this to an isolated temporary directory.
- Binary files stay on the filesystem. SQLite stores path-relative metadata only; no image BLOB columns are added.
- Import copies a validated asset into `library/<sha256>.<extension>`. The original scan source is retained. Repeated content returns the existing record and does not create a second library copy.
- Console upload uses a random inbox filename and then imports into the managed library. It does not use the user-supplied name as a destination path.
- Backups should include the entire sticker root as well as the XingChen database. Restoring only the database leaves metadata pointing at missing binaries.

## Limits and supported inspection

| Constraint | Value |
| --- | --- |
| Per-file import size | 10 MiB |
| Scan candidates per batch | 200 |
| Accepted bytes per scan batch | 100 MiB |
| Decoded raster width/height | at most 8192 px each |
| Tag count / tag length | at most 32 / 40 characters |
| Note length | at most 500 characters |
| Admin page size | at most 100 records |

PNG, JPEG, GIF and WebP are recognized by file signature and checked against the filename extension. ImageIO reads dimensions for PNG/JPEG/GIF; animated status is known only for GIF. WebP dimensions and animation stay unknown because no WebP decoder is installed. Preview uses the detected MIME, not the upload-provided MIME.

The OneBot adapter sends through the existing `image` message-segment operation using a managed local asset path. The remote gateway decides which files it accepts. Admin test-send remains unavailable: the only existing safe route is an Agent turn scoped to its active conversation and durable outbound ledger. An UNKNOWN delivery is not retried.

## Path and content safety

Scanning is recursive but bounded; symbolic links are skipped. Imports reject absolute paths, drive/UNC syntax, empty or dot segments, traversal, non-regular files, unsupported extensions, content-signature mismatch, oversized files, and paths resolving outside the configured root. Preview accepts a sticker ID only, then validates the metadata path beneath the managed `library/` directory. Responses are authenticated, private-cacheable for five minutes with a SHA-256 ETag, use an allow-listed image MIME, and set `X-Content-Type-Options: nosniff`.

The scan/import service is shared with `StickerService`, so tags, notes, source, enabled state, hash, dimensions, and animation metadata update the existing Agent catalog. Disabled entries remain in the admin page but are removed from Agent list/search results.

## Slang and Voice boundaries

Slang source/evidence strings are existing runtime metadata and do not identify a structured actor/conversation event. Console list views expose short sanitized excerpts and safe source labels; audit rows never copy message/evidence text. Manual entries use `ADMIN_MANUAL`; web research remains off. Only confirmed terms are returned to Agent search.

Voice currently recognizes a `record` segment in conversation diagnostics only. No audio asset, transcription, TTS, voice send, provider, or rate-setting subsystem exists. The UI exposes this exact limitation rather than implying those features are configured.
