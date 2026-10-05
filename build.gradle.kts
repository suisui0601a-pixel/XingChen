plugins {
    java
    id("org.springframework.boot") version "3.5.15"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "online.wanan.xingchen"
version = providers.gradleProperty("XINGCHEN_VERSION").orElse("0.1.0-SNAPSHOT").get()

val sourceRevision = providers.gradleProperty("XINGCHEN_GIT_COMMIT").orNull ?: try {
    val process = ProcessBuilder("git", "rev-parse", "--short=12", "HEAD").directory(projectDir).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().use { it.readText().trim() }
    if (process.waitFor() == 0) output else "unavailable"
} catch (_: Exception) { "unavailable" }

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

repositories { mavenCentral() }

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.flywaydb:flyway-core")
    implementation("org.xerial:sqlite-jdbc:3.50.3.0")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.assertj:assertj-core")
}

val frontendDir = layout.projectDirectory.dir("frontend")
val frontendOutput = layout.buildDirectory.dir("generated-resources/frontend")
val npmCommand = if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) "npm.cmd" else "npm"

tasks.register<Exec>("frontendInstall") {
    group = "build"
    description = "Installs the locked Admin Console frontend dependencies."
    workingDir(frontendDir)
    doFirst {
        val nodeVersion = ProcessBuilder("node", "--version").redirectErrorStream(true).start().inputStream.bufferedReader().readText().trim()
        val npmVersion = ProcessBuilder(npmCommand, "--version").redirectErrorStream(true).start().inputStream.bufferedReader().readText().trim()
        if (nodeVersion != "v24.19.0" || npmVersion != "11.17.0")
            throw GradleException("Admin Console build requires Node v24.19.0 and npm 11.17.0; found $nodeVersion / $npmVersion")
    }
    commandLine(npmCommand, "ci", "--no-audit", "--no-fund", "--cache", "./.npm-cache")
    inputs.files(frontendDir.file("package.json"), frontendDir.file("package-lock.json"))
    outputs.dir(frontendDir.dir("node_modules"))
}

tasks.register<Exec>("frontendTypecheck") {
    group = "verification"
    dependsOn("frontendInstall")
    workingDir(frontendDir)
    commandLine(npmCommand, "run", "typecheck")
}

tasks.register<Exec>("frontendTest") {
    group = "verification"
    dependsOn("frontendInstall")
    workingDir(frontendDir)
    commandLine(npmCommand, "test", "--", "--run")
}

tasks.register<Exec>("frontendE2e") {
    group = "verification"
    description = "Runs browser E2E against a temporary loopback bootJar instance and isolated database."
    dependsOn(tasks.named("bootJar"))
    workingDir(frontendDir)
    commandLine(npmCommand, "run", "e2e", "--", layout.buildDirectory.file("libs/xingchen-core-0.1.0-SNAPSHOT.jar").get().asFile.absolutePath)
    environment("XINGCHEN_E2E_ADMIN_USER", "e2e-admin")
    environment("XINGCHEN_E2E_ADMIN_PASSWORD", "local-e2e-console-password")
    environment("XINGCHEN_E2E_PORT", "3200")
}

val frontendBuild = tasks.register<Exec>("frontendBuild") {
    group = "build"
    dependsOn("frontendInstall")
    workingDir(frontendDir)
    commandLine(npmCommand, "run", "build")
    inputs.dir(frontendDir.dir("src"))
    outputs.dir(frontendOutput)
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    dependsOn(frontendBuild)
    from(frontendOutput) { into("BOOT-INF/classes/static") }
    manifest { attributes(mapOf("Implementation-Version" to project.version.toString(), "Implementation-Commit" to sourceRevision)) }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.named<Test>("test") { exclude("**/DeepSeekLiveTest.class") }

tasks.register<Test>("liveTest") {
    description = "Runs at most five explicitly opted-in DeepSeek API contract calls."
    group = "verification"
    useJUnitPlatform()
    include("**/DeepSeekLiveTest.class")
    maxParallelForks = 1
    systemProperty("spring.profiles.active", "local-live")
    doFirst {
        if (System.getenv("XINGCHEN_LIVE_TEST") != "1") {
            throw GradleException("Set XINGCHEN_LIVE_TEST=1 to explicitly opt in to paid live API calls.")
        }
        if (System.getenv("XINGCHEN_DEEPSEEK_API_KEY").isNullOrBlank()) {
            throw GradleException("XINGCHEN_DEEPSEEK_API_KEY is not configured; value is never printed.")
        }
    }
}
