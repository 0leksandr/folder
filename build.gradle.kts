import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.4.21"
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "com.nezhraba"
// CI passes the release tag's version (-PpluginVersion=1.0.1); the default is for local builds.
version = providers.gradleProperty("pluginVersion").getOrElse("1.0.0")

val minIdeBuild = "243"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdea("2026.2.4")

        // The Java plugin is only used by tests, to get a language with real folding builders.
        testBundledPlugin("com.intellij.java")
        testFramework(TestFrameworkType.Platform)
    }

    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = minIdeBuild
            untilBuild = provider { null }
        }
    }
}

// Bytecode must stay loadable on the oldest supported IDE runtime (JBR 21), whatever JDK runs Gradle.
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}
tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
}

tasks.test {
    // The unified IntelliJ IDEA distribution ships ultimate-only startup activities that cannot be
    // instantiated in the light-test environment; the Java plugin used by the tests does not need them.
    systemProperty("idea.suppressed.plugins.id", "com.intellij.modules.ultimate")
}

val generateUpdatePluginsXml by tasks.registering {
    group = "intellij platform"
    description = "Generates updatePlugins.xml for a custom plugin repository next to the plugin zip."

    val pluginXml = layout.projectDirectory.file("src/main/resources/META-INF/plugin.xml")
    val zipName = tasks.named<AbstractArchiveTask>("buildPlugin").flatMap { it.archiveFileName }
    val downloadBaseUrl = providers.gradleProperty("downloadBaseUrl")
    val ideBuild = minIdeBuild
    val pluginVersion = project.version.toString()
    val output = layout.buildDirectory.file("distributions/updatePlugins.xml")

    inputs.file(pluginXml)
    inputs.property("zipName", zipName)
    inputs.property("downloadBaseUrl", downloadBaseUrl)
    inputs.property("pluginVersion", pluginVersion)
    outputs.file(output)
    dependsOn("buildPlugin")

    doLast {
        val descriptor = groovy.xml.XmlParser().parse(pluginXml.asFile)
        val id = (descriptor.get("id") as groovy.util.NodeList).text()
        val name = (descriptor.get("name") as groovy.util.NodeList).text()
        output.get().asFile.writeText(
            """
            |<plugins>
            |    <plugin id="$id" url="${downloadBaseUrl.get()}/${zipName.get()}" version="$pluginVersion">
            |        <idea-version since-build="$ideBuild"/>
            |        <name>$name</name>
            |    </plugin>
            |</plugins>
            |""".trimMargin()
        )
    }
}
