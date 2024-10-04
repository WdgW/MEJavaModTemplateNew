group = "matrix_energy"

version = "0.0.1"


sourceSets {
    main {
        java.srcDir("src")
    }
}
val mindustryVersion: String by extra { "v146" }

val jabelVersion: String by extra { "93fde537c7" }

val mindustryDataDir: String by extra { "C:\\Users\\Administrator\\AppData\\Roaming\\Mindustry" }

val isWindows: Boolean by extra { System.getProperty("os.name").lowercase().contains("windows") }

val d8 by extra { if (isWindows) "d8.bat" else "d8" }
//val d8 by extra { "W:\\android-sdk-windows\\build-tools\\34.0.0\\d8.bat" }

val sdkRoot: String? by extra { System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT") }

repositories {
    mavenCentral()
    google()
    maven { url = uri("https://maven.aliyun.com/repository/public") }
    maven { url = uri("https://maven.aliyun.com/nexus/content/groups/public/") }
    maven { url = uri("https://maven.aliyun.com/repository/public/") }
    maven { url = uri("https://maven.aliyun.com/repository/google/") }
    maven { url = uri("https://maven.aliyun.com/repository/jcenter/") }
    maven { url = uri("https://maven.aliyun.com/repository/central/") }

    maven { url = uri("https://raw.githubusercontent.com/Zelaux/MindustryRepo/master/repository") }
    maven { url = uri("https://www.jitpack.io") }

}



plugins {
    id("java")
}


dependencies {
    compileOnly("com.github.Anuken.Arc:arc-core:$mindustryVersion")
    compileOnly("com.github.Anuken.Mindustry:core:$mindustryVersion")
//    compileOnly("com.github.WdgW.MELib:test")

    annotationProcessor("com.github.Anuken:jabel:$jabelVersion")
}

java {
    targetCompatibility = JavaVersion.VERSION_1_8
    sourceCompatibility = JavaVersion.VERSION_21
}
//java 8 backwards compatibility flag

allprojects {
    tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs.addAll(listOf("--release", "8"))
        options.encoding = "UTF-8"
    }
}

configurations.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "com.github.Anuken.Arc") {
            useVersion("$mindustryVersion")
        }
    }
}



tasks.register("jarAndroid") {
    dependsOn(tasks.named("jar"))

    doLast {
        if (sdkRoot == null || !File(sdkRoot).exists()) {
            throw GradleException("No valid Android SDK found. Ensure that ANDROID_HOME is set to your Android SDK directory.")
        }

        val platformRoot = File("$sdkRoot/platforms/").listFiles()?.sortedArrayDescending()
            ?.firstOrNull { File(it, "android.jar").exists() }
        if (platformRoot == null) {
            throw GradleException("No android.jar found. Ensure that you have an Android platform installed.")
        }

        // Collect dependencies needed for desugaring
        val dependencies =
            configurations.compileClasspath.get().files + configurations.runtimeClasspath.get().files + listOf(
                File(
                    platformRoot,
                    "android.jar"
                )
            )

        var classpath = mutableListOf<String>()
        dependencies.forEach {
            classpath.add("--classpath")
            classpath.add(it.path.toString())
        }


        // Dex and desugar files - this requires d8 in your PATH

        exec {
            workingDir("${project.layout.buildDirectory.get()}/libs")
            commandLine("$d8", *classpath.toTypedArray(), "--min-api", "34", "--output", "${project.name}Android.jar", "${project.name}Desktop.jar")
//            commandLine("cmd /c \"$d8 $classpath --min-api 14 --output ${project.name}Android.jar ${project.name}Desktop.jar\"")
        }
    }
}

tasks.named<Jar>("jar") {
    archiveFileName.set("${project.name}Desktop.jar")

    from({
        configurations["runtimeClasspath"].files.map { if (it.isDirectory) it else zipTree(it) }
    })

    from(projectDir) {
        include("mod.hjson")
    }

    from("assets/") {
        include("**")
    }
}


tasks.register<Jar>("deploy") {
    dependsOn("jarAndroid")
    dependsOn("jar")
    archiveFileName.set("${project.name}.jar")

    from(zipTree("${project.layout.buildDirectory.get()}/libs/${project.name}Desktop.jar"))
    from(zipTree("${project.layout.buildDirectory.get()}/libs/${project.name}Android.jar"))

    doLast {
        delete {
            delete("${project.layout.buildDirectory.get()}/libs/${project.name}Desktop.jar")
            delete("${project.layout.buildDirectory.get()}/libs/${project.name}Android.jar")
        }
    }
}

// Run Batch Script task
tasks.register<Exec>("runBatchScript") {
    workingDir = File("W:\\game")
    commandLine("W:\\game\\mindustry.bat")
}

// Clean Game Mode task
tasks.register<Delete>("cleanGameMode") {
    delete("$mindustryDataDir/mods/${project.name}Desktop.jar")
}

// Copy To Game task
tasks.register<Copy>("copyToGame") {
    from("${project.layout.buildDirectory.get()}/libs/${project.name}Desktop.jar")
    into("$mindustryDataDir/mods")
}

// Play Game task
tasks.register<Copy>("playGame") {
    dependsOn(tasks.named("jar"))

    doFirst {
        delete("$mindustryDataDir/mods/${project.name}Desktop.jar")
    }

    doLast {
        from("${project.layout.buildDirectory.get()}/libs/${project.name}Desktop.jar")
        into("$mindustryDataDir/mods")

        exec {
            workingDir = File("W:\\game")
            commandLine("W:\\game\\mindustry.bat")
        }
    }
}
