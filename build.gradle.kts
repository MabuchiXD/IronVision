plugins {
    java
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val javafxVersion = "17.0.6"
val oshiVersion = "6.4.10"
val slf4jVersion = "2.0.9"
val jSensorsVersion = "2.2.1"

dependencies {
    implementation("org.openjfx:javafx-controls:$javafxVersion:win")
    implementation("org.openjfx:javafx-fxml:$javafxVersion:win")
    implementation("org.openjfx:javafx-graphics:$javafxVersion:win")
    implementation("org.openjfx:javafx-base:$javafxVersion:win")

    implementation("com.github.oshi:oshi-core:$oshiVersion")
    implementation("org.slf4j:slf4j-simple:$slf4jVersion")
    implementation("com.profesorfalken:jSensors:$jSensorsVersion")
}

application {
    mainClass.set("org.example.Launcher")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

// ВОТ ЗДЕСЬ ИЗМЕНЕНИЕ:
// Мы используем полный путь к классу ShadowJar, чтобы не делать import наверху
tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    archiveBaseName.set("IronVision")
    archiveClassifier.set("")
    archiveVersion.set("1.1")

    manifest {
        attributes["Main-Class"] = "org.example.Launcher"
    }

    mergeServiceFiles()
}