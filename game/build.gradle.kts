plugins {
    id("java")
    application
}

group = "ru.itmo.masters"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.googlecode.lanterna:lanterna:3.0.1")
    implementation("org.jetbrains:annotations:20.1.0")

}

application {
    mainClass.set("ru.itmo.masters.Main")
}


sourceSets {
    main {
        java.setSrcDirs(listOf("src"))
        resources.setSrcDirs(listOf("resources"))
    }
}
tasks.getByName<Test>("test") {
    useJUnitPlatform()
}