# Third-party notices and publication scope

The XingChen community license covers original XingChen material only. It does
not relicense, restrict or remove rights in independently licensed dependencies.
Preserve their licenses, copyright and NOTICE files when distributing binaries.

## Source snapshot

The only tracked dependency binary is `gradle/wrapper/gradle-wrapper.jar`.
Gradle and its generated wrapper scripts retain their upstream notices; the
unmodified distribution license and NOTICE are reproduced in
`third-party/gradle/`. Gradle's license file also describes components in its
distribution; that is not a claim that every such component is inside wrapper.jar.

The public source snapshot excludes node_modules, compiled frontend bundles,
application jars/images, databases, real media archives and proprietary Gateway
components. No external font/icon/image pack is tracked. The tiny test PNG is
generated from a synthetic fixture. Operator-supplied media remains the operator's
responsibility and is not covered by the XingChen license.

## Dependency evidence

The reviewed npm lock contains 158 packages with license metadata present for
each: MIT, MIT-0, Apache-2.0, BSD-2/3-Clause, MPL-2.0, BlueOak-1.0.0, CC0-1.0 and ISC.
Runtime React, router, form and Zod dependencies have MIT metadata. Development
dependencies are not thereby relicensed and retain upstream terms.

The audited bootJar contains 46 unmodified runtime dependency jars. Embedded
license/NOTICE files were enumerated. Five jars without such files were checked
against Maven POM metadata (including the Logback parent POM): HikariCP and
SnakeYAML use Apache-2.0; LatencyUtils uses CC0; Logback 1.5.34 offers
EPL-2.0 or LGPL-2.1-only. See the exact inventory in
`docs/THIRD_PARTY_INVENTORY.md`.

Logback's upstream licensing statement is at
https://logback.qos.ch/license.html. Do not apply XingChen's commercial restriction
to Logback or any other dependency. No dependency source was modified here.
Before separately publishing a compiled jar/image or bundled JavaScript release,
include applicable full license texts, notices and required source availability
information for that artifact. This source publication is not a binary release.

## Boundaries

SnowLuma and QQ are separately obtained products under their own terms. Their
proprietary components and credentials are not included. Referencing a supported
Gateway API does not grant redistribution rights to that Gateway.

The audit checks tracked files, dependency evidence and generated asset sources;
it is not a guarantee of all historical provenance or a substitute for legal
review before commercial licensing. Contributors must identify external material.
