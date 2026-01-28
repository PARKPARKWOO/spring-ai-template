plugins {
//    kotlin("jvm") version "1.9.25"
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.spring") version "1.9.25"
//    kotlin("kapt") version "2.2.21"
    kotlin("kapt") version "1.9.25"
    id("org.springframework.boot") version "3.5.8"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("plugin.jpa") version "1.9.25"
}

group = "com.example"
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

extra["springAiVersion"] = "1.1.2"
extra["springCloudVersion"] = "2025.0.0"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-mysql")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.security:spring-security-crypto")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.springframework.ai:spring-ai-starter-mcp-client")
    implementation("org.springframework.ai:spring-ai-starter-mcp-server-webmvc")
    implementation("org.springframework.ai:spring-ai-starter-model-anthropic")
    implementation("org.springframework.ai:spring-ai-starter-model-google-genai")
    implementation("org.springframework.ai:spring-ai-starter-model-google-genai-embedding")
    implementation("org.springframework.ai:spring-ai-starter-model-openai")
    implementation("org.springframework.ai:spring-ai-starter-model-vertex-ai-gemini")
    implementation("org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j")
    implementation("com.github.ben-manes.caffeine:caffeine")
    runtimeOnly("com.mysql:mysql-connector-j")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("com.h2database:h2")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    
    // SpringDoc OpenAPI (Swagger)
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.15")
    
    // QueryDSL
    implementation("com.querydsl:querydsl-jpa:5.1.0:jakarta")
    kapt("com.querydsl:querydsl-apt:5.1.0:jakarta")
    kapt("jakarta.annotation:jakarta.annotation-api")
    kapt("jakarta.persistence:jakarta.persistence-api")
    
    // JWT
    implementation("io.jsonwebtoken:jjwt-api:0.12.5")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.5")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.5")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:${property("springAiVersion")}")
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudVersion")}")
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
    annotation("org.springframework.stereotype.Component")
    annotation("org.springframework.stereotype.Service")
    annotation("org.springframework.stereotype.Repository")
    annotation("org.springframework.web.bind.annotation.RestController")
}

// QueryDSL QClass 생성 경로 설정
sourceSets["main"].java.srcDirs("$buildDir/generated/source/kapt/main")

tasks.withType<Test> {
    useJUnitPlatform()
}
