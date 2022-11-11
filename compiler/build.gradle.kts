plugins {
    id("java-gradle-plugin")
    id("antlr")
    id("maven-publish")
}

group = "com.hedera.hashgraph.pbj.compiler"
version = "1.0-SNAPSHOT"
tasks.jar {
    archiveBaseName.set("pbj-compiler")
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation("org.jetbrains:annotations:20.1.0")
    antlr("org.antlr:antlr4:4.11.1")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

tasks.generateGrammarSource {
    arguments = arguments + listOf("-package", "com.hedera.hashgraph.pbj.compiler.impl.grammar")
}

gradlePlugin {
    plugins {
        create("hedera.pbj-compiler") {
            id = "hedera.pbj-compiler"
            implementationClass = "com.hedera.hashgraph.pbj.compiler.PbjCompilerPlugin"
            description = "The PBJ Protobuf plugin provides protobuf compilation to java records."
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.hedera.hashgraph.pbj"
            artifactId = "pbj-compiler"
            version = "1.0-SNAPSHOT"
            from(components["java"])
        }
    }
    repositories {
        maven {
            url = rootProject.layout.buildDirectory.dir("testRepo").get().asFile.toURI()
            name = "test"
        }
    }
}