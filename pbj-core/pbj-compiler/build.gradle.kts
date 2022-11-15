plugins {
    id("antlr")
    id("java-gradle-plugin")
    id("maven-publish")
}

group = "com.hedera.pbj"

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation("org.jetbrains:annotations:23.0.0")
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
        create("compiler") {
            id = "com.hedera.pbj.pbj-compiler"
            group = "com.hedera.pbj"
            implementationClass = "com.hedera.hashgraph.pbj.compiler.PbjCompilerPlugin"
            description = "The PBJ Protobuf plugin provides protobuf compilation to java records."
        }
    }
}

tasks.assemble {
    dependsOn(tasks.publishToMavenLocal)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
