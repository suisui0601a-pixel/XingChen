# Contributing

Read LICENSE and SECURITY.md before contributing. This project is source-available
for permitted non-commercial use, not generally licensed for commercial use.
Contributions must be your own work or accompanied by compatible license and
attribution evidence. Do not copy proprietary Gateway components or assets.

Use JDK 21, Node 24.19.0 and npm 11.17.0. Run the Gradle backend suite, frontend
tests, typecheck, bootJar and isolated browser E2E before proposing a change.
Use fake fixtures only; never connect CI to production QQ, a paid provider or
the production server. Keep generated artifacts and personal state out of Git.

Describe the safety boundary, tests and migration/rollback impact in changes.
Avoid broad refactors that obscure a focused fix. Do not disable CSRF, path
guards, authentication or the remote-bind guard to make a test pass.

Small, meaningful architecture comments may identify anwaning; do not add a
mechanical watermark to every file or change vendor code to insert a signature.
