// Compiler plugins.
val gcsLibVersion = "0.1.0"

// Data types & control flow.
val catsVersion = "1.6.0"
val catsEffectVersion = "1.2.0"
val enumeratumVersion = "1.5.13"
val fs2Version = "1.0.5"

// DB.
val doobieVersion = "0.7.0"
val postgresqlDriverVersion = "42.2.5"

// JSON.
val circeVersion = "0.11.1"
val circeDerivationVersion = "0.11.0-M3"
val enumeratumCirceVersion = "1.5.21"

// Logging.
val logbackVersion = "1.2.3"
val log4CatsVersion = "0.3.0"

// Web.
val http4sVersion = "0.20.10"
val sshJVersion = "0.27.0"

// Storage libraries.
val commonsNetVersion = "3.6"
val googleAuthVersion = "0.17.1"

// Testing.
val googleCloudJavaVersion = "1.90.0"
val scalaMockVersion = "4.4.0"
val scalaTestVersion = "3.0.8"
val vaultDriverVersion = "5.0.0"
val liquibaseVersion = "3.7.0"
val testcontainersVersion = "1.12.0"
val testcontainersScalaVersion = "0.29.0"

// Settings to apply to all sub-projects.
// Can't be applied at the build level because of scoping rules.
val commonSettings = Seq(
  resolvers ++= Seq(
    "Broad Artifactory Releases" at "https://broadinstitute.jfrog.io/broadinstitute/libs-release/",
    "Broad Artifactory Snapshots" at "https://broadinstitute.jfrog.io/broadinstitute/libs-snapshot/"
  ),
)

lazy val `monster-ingester` = project
  .in(file("."))
  .aggregate(`jade-client`, `core`)

lazy val `jade-client` = project
  .in(file("jade-client"))
  .enablePlugins(BasePlugin)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.beachape" %% "enumeratum" % enumeratumVersion,
      "com.beachape" %% "enumeratum-circe" % enumeratumCirceVersion,
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-derivation" % circeDerivationVersion,
      "io.circe" %% "circe-parser" % circeVersion,
      "io.circe" %% "circe-literal" % circeVersion,
      "org.http4s" %% "http4s-circe" % http4sVersion,
      "org.http4s" %% "http4s-blaze-client" % http4sVersion,
      "org.http4s" %% "http4s-circe" % http4sVersion,
      "org.http4s" %% "http4s-dsl" % http4sVersion,
      "org.broadinstitute.monster" %% "gcs-lib" % gcsLibVersion
    ),
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-literal" % circeVersion,
      "org.scalamock" %% "scalamock" % scalaMockVersion,
      "org.scalatest" %% "scalatest" % scalaTestVersion,
    ).map(_ % Test)
  )

lazy val `core` = project
  .in(file("core"))
  .enablePlugins(BasePlugin)
  .dependsOn(`jade-client`, `core-migrations`)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-derivation" % circeDerivationVersion,
      "io.circe" %% "circe-parser" % circeVersion,
      "io.circe" %% "circe-literal" % circeVersion,
      "org.http4s" %% "http4s-circe" % http4sVersion,
      "org.http4s" %% "http4s-blaze-client" % http4sVersion,
      "org.http4s" %% "http4s-circe" % http4sVersion,
      "org.http4s" %% "http4s-dsl" % http4sVersion,
      "org.tpolecat" %% "doobie-core" % doobieVersion,
      "org.tpolecat" %% "doobie-hikari" % doobieVersion,
      "org.tpolecat" %% "doobie-postgres" % doobieVersion,
      "org.tpolecat" %% "doobie-postgres-circe" % doobieVersion,
    ),
    libraryDependencies ++= Seq(
      "com.dimafeng" %% "testcontainers-scala" % testcontainersScalaVersion,
      "io.circe" %% "circe-literal" % circeVersion,
      "org.liquibase" % "liquibase-core" % liquibaseVersion,
      "org.scalamock" %% "scalamock" % scalaMockVersion,
      "org.scalatest" %% "scalatest" % scalaTestVersion,
      "org.testcontainers" % "postgresql" % testcontainersVersion
    ).map(_ % Test),
    dependencyOverrides := Seq(
      "org.postgresql" % "postgresql" % postgresqlDriverVersion
    )
  )

lazy val `core-migrations` = project
  .in(file("./core/db-migrations"))
  .settings(commonSettings)