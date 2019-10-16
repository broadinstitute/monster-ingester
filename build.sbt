// Data types & control flow.
val enumeratumVersion = "1.5.13"

// DB.
val doobieVersion = "0.8.4"
val postgresqlDriverVersion = "42.2.5"

// JSON.
val circeVersion = "0.12.2"
val circeDerivationVersion = "0.12.0-M7"
val enumeratumCirceVersion = "1.5.22"

// Logging.
val logbackVersion = "1.2.3"
val log4CatsVersion = "1.0.0"

// Web.
val http4sVersion = "0.21.0-M5"
val sshJVersion = "0.27.0"

// Storage libraries.
val gcsLibVersion = "0.5.0"
val commonsNetVersion = "3.6"
val googleAuthVersion = "0.18.0"

// Testing.
val googleCloudJavaVersion = "1.90.0"
val scalaMockVersion = "4.4.0"
val scalaTestVersion = "3.0.8"
val vaultDriverVersion = "5.0.0"
val liquibaseVersion = "3.8.0"
val testcontainersVersion = "1.12.2"
val testcontainersScalaVersion = "0.33.0"

lazy val `monster-ingester` = project
  .in(file("."))
  .aggregate(`jade-client`, `core`)

lazy val `jade-client` = project
  .in(file("jade-client"))
  .enablePlugins(BasePlugin)
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
      "org.tpolecat" %% "doobie-postgres" % doobieVersion,
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
  .dependsOn(`jade-client`, `core-migrations` % Test)
  .settings(
    libraryDependencies ++= Seq(
      "io.chrisdavenport" %% "log4cats-slf4j" % log4CatsVersion,
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
