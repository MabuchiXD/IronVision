plugins {
    id("java")
    id("application")
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    implementation("com.github.oshi:oshi-core:6.4.0")
    implementation("org.slf4j:slf4j-simple:2.0.9")
    implementation("com.profesorfalken:jSensors:2.2.1")
}
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
    tasks.test {
        useJUnitPlatform()
    }
}