# Source-publication audit

2026-10-05. Public repository:
https://github.com/suisui0601a-pixel/XingChen

- Original internal Git history is retained; public history begins with a clean,
  audited source snapshot. No unrelated repository was overwritten.
- Initial public commit `a255d6bfd62e0cc15a01ba4db86d8baefe194b0c` has the exact
  same Git tree as internal `147d3d511ae32d3d284f3b1fd1975649f3e9f0c4`.
  Windows archive line endings and shell executable bits were normalized before
  this equality check, not silently accepted as differing content.
- 485 tracked files and 2,007 reachable internal objects were inspected at that
  seal. No strong private-key/provider/GitHub/AWS token pattern candidates or
  unsafe tracked credential/database/backup paths were found. This does not claim
  that pattern scanning alone can prove absence of every possible secret.
- The only tracked dependency binary is the Gradle Wrapper, 47,623 bytes,
  SHA-256 `238e777fcddd7e34f9708186085def2abd6e08e658505b38718d79d74c21abd5`.
  No tracked file exceeded 1 MiB. Third-party license scope is documented.
- Real production credentials were never read, uploaded, committed, passed to
  automated login, or recorded in these documents. Databases, backups, runtime
  secrets, node_modules, generated bundles and test output are excluded.
- CI uses only synthetic fixtures, with no production server/QQ/model access.
  First Linux run exposed test assets using container-default `/data`; the CI-only
  sticker root is now an explicit workspace fixture. No permissions guard or
  production source was disabled to accommodate CI.

Current production HTTPS infrastructure is active. QQ and Model configuration
remain user-managed. At the requested QR-login handoff, Docker contained no
SnowLuma/QQ instance and neither 5099 nor 6081 was listening. No usable QR could
be captured; a separately authorized independent Gateway deployment is required.
Do not restore retired artifacts or obtain a fake QR through XingChen Console.

Final Phase 5 completion remains separate from this source publication: CI outcome,
post-restart manual admin acceptance and any new Gateway installation authority
must be reported honestly. No final PDF or next-phase work is started here.

## Subsequent production update

After the QR-login handoff described above, the operator later reported that a
separate Gateway was available and that the deployed Core completed real QQ /
DeepSeek tool round-trip acceptance. See the dated update in
[PHASE5_PRODUCTION_GO_LIVE.md](PHASE5_PRODUCTION_GO_LIVE.md). This superseding
operator report does not claim that this repository-sync run independently
repeated the live test. Production domain, host address, and credentials remain
omitted.
