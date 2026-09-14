plugins {
	java
	id("org.springframework.boot") version "4.1.0"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "io.github.dreadvoice"
version = "1.0.0"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

springBoot {
	buildInfo()
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	compileOnly("org.projectlombok:lombok")
	developmentOnly("org.springframework.boot:spring-boot-devtools")
	annotationProcessor("org.projectlombok:lombok")
	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testCompileOnly("org.projectlombok:lombok")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	testAnnotationProcessor("org.projectlombok:lombok")
	implementation(platform("dev.langchain4j:langchain4j-bom:1.18.0"))
	implementation("dev.langchain4j:langchain4j")
	implementation("dev.langchain4j:langchain4j-anthropic")
	implementation("dev.langchain4j:langchain4j-open-ai")
	implementation("dev.langchain4j:langchain4j-ollama")
	implementation("dev.langchain4j:langchain4j-google-ai-gemini")
	implementation("dev.langchain4j:langchain4j-bedrock")
	runtimeOnly("org.xerial:sqlite-jdbc:3.42.0.0")
	implementation("org.hibernate.orm:hibernate-community-dialects")
	// flyway-core 12.x bundles SQLite support; there is no separate flyway-database-sqlite module.
	implementation("org.flywaydb:flyway-core")
	implementation("org.springframework.boot:spring-boot-flyway")
	implementation("org.yaml:snakeyaml")
}

val frontendDir = layout.projectDirectory.dir("frontend")
val npmExecutable = if (System.getProperty("os.name").lowercase().contains("windows")) "npm.cmd" else "npm"

val installFrontendDependencies = tasks.register<Exec>("installFrontendDependencies") {
	group = "frontend"
	description = "Installs the frontend npm dependencies from the lockfile."
	workingDir = frontendDir.asFile
	commandLine(npmExecutable, "ci")
	inputs.file(frontendDir.file("package.json"))
	inputs.file(frontendDir.file("package-lock.json"))
	outputs.dir(frontendDir.dir("node_modules"))
}

val buildFrontend = tasks.register<Exec>("buildFrontend") {
	group = "frontend"
	description = "Type-checks and builds the frontend into frontend/dist."
	dependsOn(installFrontendDependencies)
	workingDir = frontendDir.asFile
	commandLine(npmExecutable, "run", "build")
	inputs.dir(frontendDir.dir("src"))
	inputs.dir(frontendDir.dir("public"))
	inputs.files(
		frontendDir.file("index.html"),
		frontendDir.file("package.json"),
		frontendDir.file("vite.config.ts"),
		frontendDir.file("tsconfig.json"),
		frontendDir.file("tsconfig.app.json"),
		frontendDir.file("tsconfig.node.json"),
	)
	outputs.dir(frontendDir.dir("dist"))
}

val bundleFrontend = tasks.register<Sync>("bundleFrontend") {
	group = "frontend"
	description = "Collects the built frontend as the static resources served by the application."
	from(buildFrontend)
	into(layout.buildDirectory.dir("frontend-static"))
}

tasks.bootJar {
	archiveFileName.set("incant.jar")
	manifest {
		attributes(
			"Implementation-Title" to "Incant",
			"Implementation-Version" to project.version.toString(),
		)
	}
	from(bundleFrontend) {
		into("BOOT-INF/classes/static")
	}
}

tasks.jar {
	enabled = false
}

val runtimeModules = listOf(
	"java.base",
	"java.compiler",
	"java.desktop",
	"java.instrument",
	"java.logging",
	"java.management",
	"java.naming",
	"java.net.http",
	"java.prefs",
	"java.security.jgss",
	"java.security.sasl",
	"java.sql",
	"java.transaction.xa",
	"java.xml",
	"jdk.crypto.cryptoki",
	"jdk.crypto.ec",
	"jdk.unsupported",
	"jdk.zipfs",
)

val jlinkRuntime = tasks.register<Exec>("jlinkRuntime") {
	group = "distribution"
	description = "Builds a Java runtime holding only the modules Incant needs."

	val launcher = javaToolchains.launcherFor(java.toolchain)
	val destination = layout.buildDirectory.dir("jlink")

	outputs.dir(destination)

	doFirst {
		val output = destination.get().asFile
		output.deleteRecursively()

		executable = launcher.get().metadata.installationPath.file("bin/jlink").asFile.absolutePath
		args(
			"--add-modules", runtimeModules.joinToString(","),
			"--strip-debug",
			"--no-header-files",
			"--no-man-pages",
			"--compress", "zip-6",
			"--output", output.absolutePath,
		)
	}
}

val jpackageImage = tasks.register<Exec>("jpackageImage") {
	group = "distribution"
	description = "Builds a self-contained application image with a native launcher."
	dependsOn(tasks.bootJar, jlinkRuntime)

	val launcher = javaToolchains.launcherFor(java.toolchain)
	val destination = layout.buildDirectory.dir("jpackage")
	val input = layout.buildDirectory.dir("libs")
	val runtime = layout.buildDirectory.dir("jlink")

	inputs.file(tasks.bootJar.flatMap { it.archiveFile })
	outputs.dir(destination)

	doFirst {
		val output = destination.get().asFile
		output.deleteRecursively()
		output.mkdirs()

		executable = launcher.get().metadata.installationPath.file("bin/jpackage").asFile.absolutePath
		args(
			"--type", "app-image",
			"--name", "Incant",
			"--app-version", project.version.toString(),
			"--input", input.get().asFile.absolutePath,
			"--main-jar", tasks.bootJar.get().archiveFileName.get(),
			"--runtime-image", runtime.get().asFile.absolutePath,
			"--dest", output.absolutePath,
		)
	}
}

val installerType = when {
	org.gradle.internal.os.OperatingSystem.current().isMacOsX -> "dmg"
	org.gradle.internal.os.OperatingSystem.current().isWindows -> "msi"
	else -> "deb"
}

val installerOptions = if (installerType == "deb") {
	listOf(
		"--linux-deb-maintainer", "akashchoudhary2005@gmail.com",
		"--linux-menu-group", "Development",
		"--linux-shortcut",
	)
} else {
	emptyList()
}

tasks.register<Exec>("jpackageInstaller") {
	group = "distribution"
	description = "Builds a $installerType installer for the machine running the build."
	dependsOn(tasks.bootJar, jlinkRuntime)

	val launcher = javaToolchains.launcherFor(java.toolchain)
	val destination = layout.buildDirectory.dir("installer")
	val input = layout.buildDirectory.dir("libs")
	val runtime = layout.buildDirectory.dir("jlink")

	inputs.file(tasks.bootJar.flatMap { it.archiveFile })
	outputs.dir(destination)

	doFirst {
		val output = destination.get().asFile
		output.deleteRecursively()
		output.mkdirs()

		executable = launcher.get().metadata.installationPath.file("bin/jpackage").asFile.absolutePath
		args(
			"--type", installerType,
			"--name", "Incant",
			"--app-version", project.version.toString(),
			"--vendor", "Incant",
			"--description", "A local-first, provider-agnostic runtime for Claude Skills",
			"--input", input.get().asFile.absolutePath,
			"--main-jar", tasks.bootJar.get().archiveFileName.get(),
			"--runtime-image", runtime.get().asFile.absolutePath,
			"--dest", output.absolutePath,
		)
		args(installerOptions)
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}
