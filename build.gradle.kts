plugins {
	java
	id("org.springframework.boot") version "4.1.0"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "io.github.dreadvoice"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
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
	from(bundleFrontend) {
		into("BOOT-INF/classes/static")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}
