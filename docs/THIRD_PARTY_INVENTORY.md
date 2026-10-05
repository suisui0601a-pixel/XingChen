# Third-party dependency inventory

Source-publication audit, 2026-10-05. Dependency metadata is evidence, not a replacement for upstream license texts. No node_modules, bootJar, image or proprietary Gateway package is distributed in this source snapshot.

## Frontend locked runtime packages

| Package | Version | License metadata |
| --- | --- | --- |
| @hookform/resolvers | 5.9.1 | MIT |
| @standard-schema/utils | 0.3.0 | MIT |
| @types/react | 19.3.0 | MIT |
| cookie | 1.1.1 | MIT |
| csstype | 3.2.3 | MIT |
| react | 19.3.0 | MIT |
| react-dom | 19.3.0 | MIT |
| react-hook-form | 7.89.0 | MIT |
| react-router | 7.18.4 | MIT |
| react-router-dom | 7.18.4 | MIT |
| scheduler | 0.28.0 | MIT |
| set-cookie-parser | 2.7.2 | MIT |
| zod | 4.6.5 | MIT |

All 158 lock packages have metadata; development packages retain their original licenses, including MPL-2.0. They are not covered by the non-commercial project license.

## Backend bootJar inspection

46 runtime jars were checked. Embedded notices remain in the unmodified jar files; Maven metadata fills the five missing embedded-notice cases. Logback licenses come from its exact 1.5.34 parent POM and its official licensing statement.

| Artifact | Evidence |
| --- | --- |
| HdrHistogram-2.2.2.jar | Public Domain, per Creative Commons CC0 / BSD-2-Clause |
| HikariCP-6.3.3.jar | The Apache Software License, Version 2.0 |
| LatencyUtils-2.0.3.jar | Public Domain, per Creative Commons CC0 |
| flyway-core-11.7.2.jar | META-INF/LICENSE.txt |
| jackson-annotations-2.21.jar | The Apache Software License, Version 2.0 |
| jackson-core-2.21.4.jar | The Apache Software License, Version 2.0 |
| jackson-databind-2.21.4.jar | The Apache Software License, Version 2.0 |
| jackson-dataformat-toml-2.21.4.jar | The Apache Software License, Version 2.0 |
| jackson-datatype-jdk8-2.21.4.jar | META-INF/LICENSE, META-INF/NOTICE |
| jackson-datatype-jsr310-2.21.4.jar | META-INF/LICENSE, META-INF/NOTICE |
| jackson-module-parameter-names-2.21.4.jar | META-INF/LICENSE, META-INF/NOTICE |
| jakarta.annotation-api-2.1.1.jar | EPL 2.0 / GPL2 w/ CPE |
| jul-to-slf4j-2.0.18.jar | META-INF/LICENSE.txt |
| log4j-api-2.24.3.jar | Apache-2.0 |
| log4j-to-slf4j-2.24.3.jar | Apache-2.0 |
| logback-classic-1.5.34.jar | Parent POM: EPL-2.0 OR LGPL-2.1-only |
| logback-core-1.5.34.jar | Parent POM: EPL-2.0 OR LGPL-2.1-only |
| micrometer-commons-1.15.12.jar | The Apache Software License, Version 2.0 |
| micrometer-core-1.15.12.jar | The Apache Software License, Version 2.0 |
| micrometer-jakarta9-1.15.12.jar | The Apache Software License, Version 2.0 |
| micrometer-observation-1.15.12.jar | The Apache Software License, Version 2.0 |
| slf4j-api-2.0.18.jar | META-INF/LICENSE.txt |
| snakeyaml-2.4.jar | Apache License, Version 2.0 |
| spring-aop-6.2.19.jar | Apache License, Version 2.0 |
| spring-beans-6.2.19.jar | Apache License, Version 2.0 |
| spring-boot-3.5.15.jar | Apache License, Version 2.0 |
| spring-boot-actuator-3.5.15.jar | Apache License, Version 2.0 |
| spring-boot-actuator-autoconfigure-3.5.15.jar | Apache License, Version 2.0 |
| spring-boot-autoconfigure-3.5.15.jar | Apache License, Version 2.0 |
| spring-context-6.2.19.jar | Apache License, Version 2.0 |
| spring-core-6.2.19.jar | Apache License, Version 2.0 |
| spring-expression-6.2.19.jar | Apache License, Version 2.0 |
| spring-jcl-6.2.19.jar | Apache License, Version 2.0 |
| spring-jdbc-6.2.19.jar | Apache License, Version 2.0 |
| spring-security-config-6.5.11.jar | Apache License, Version 2.0 |
| spring-security-core-6.5.11.jar | Apache License, Version 2.0 |
| spring-security-crypto-6.5.11.jar | Apache License, Version 2.0 |
| spring-security-web-6.5.11.jar | Apache License, Version 2.0 |
| spring-tx-6.2.19.jar | Apache License, Version 2.0 |
| spring-web-6.2.19.jar | Apache License, Version 2.0 |
| spring-webmvc-6.2.19.jar | Apache License, Version 2.0 |
| sqlite-jdbc-3.50.3.0.jar | The Apache Software License, Version 2.0 |
| tomcat-embed-core-10.1.55.jar | Apache License, Version 2.0 |
| tomcat-embed-el-10.1.55.jar | Apache License, Version 2.0 |
| tomcat-embed-websocket-10.1.55.jar | Apache License, Version 2.0 |
| spring-boot-jarmode-tools-3.5.15.jar | META-INF/LICENSE.txt, META-INF/NOTICE.txt |

The only tracked third-party binary is the Gradle Wrapper; upstream license/NOTICE copies are retained in third-party/gradle. No third-party font/icon/image archive was found in the tracked snapshot. Synthetic test media is generated locally. Any later binary release needs its own complete attribution/source-availability packaging review. See THIRD_PARTY_NOTICES.md.
