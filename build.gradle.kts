plugins {
    java
    id("org.springframework.boot") version "4.0.6"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "nbc"
version = "0.0.1-SNAPSHOT"
description = "demo"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-jackson")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation(platform("software.amazon.awssdk:bom:2.25.0"))
    implementation("software.amazon.awssdk:s3")
    compileOnly("org.projectlombok:lombok")

    // prod runtime — RDS MySQL
    runtimeOnly("com.mysql:mysql-connector-j")

    // H2 — prod jar 에 미포함. bootRun (local profile dev) + 모든 테스트에서만 사용.
    // 결정 근거: docs/adr/0014-prod-rds-mysql-migration.md
    developmentOnly("org.springframework.boot:spring-boot-h2console")
    developmentOnly("com.h2database:h2")
    testRuntimeOnly("com.h2database:h2")

    annotationProcessor("org.projectlombok:lombok")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testCompileOnly("org.projectlombok:lombok")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testAnnotationProcessor("org.projectlombok:lombok")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
