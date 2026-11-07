pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    mavenLocal { content { includeGroup("dev.cpsaul.conectoria") } }
    val gprKey = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
    if (gprKey != null) {
      maven("https://maven.pkg.github.com/cpsaul-dev/conector-ia") {
        name = "conectorIa"
        credentials {
          username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR") ?: "github-actions"
          password = gprKey
        }
        content { includeGroup("dev.cpsaul.conectoria") }
      }
    }
    google()
    mavenCentral()
  }
}

rootProject.name = "LectorNoticias"

include(":app")
