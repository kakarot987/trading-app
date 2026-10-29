plugins {
    kotlin("jvm") version "1.9.23"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.0")
    testImplementation(kotlin("test"))
}


tasks.withType<Test> {
    useJUnitPlatform()
}
