// Settings to apply across the entire build
inThisBuild(
  Seq(
    organization := "org.broadinstitute.monster",
    scalaVersion := "2.12.8",
    // Auto-format
    scalafmtConfig := (ThisBuild / baseDirectory)(_ / ".scalafmt.conf").value,
    scalafmtOnCompile := true,
    // Recommended guardrails
    scalacOptions ++= Seq(
      "-deprecation",
      "-encoding",
      "UTF-8",
      "-explaintypes",
      "-feature",
      "-target:jvm-1.8",
      "-unchecked",
      "-Xcheckinit",
      "-Xfatal-warnings",
      "-Xfuture",
      "-Xlint",
      "-Xmax-classfile-name",
      "200",
      "-Yno-adapted-args",
      "-Ypartial-unification",
      "-Ywarn-dead-code",
      "-Ywarn-extra-implicit",
      "-Ywarn-inaccessible",
      "-Ywarn-infer-any",
      "-Ywarn-nullary-override",
      "-Ywarn-nullary-unit",
      "-Ywarn-numeric-widen",
      "-Ywarn-unused",
      "-Ywarn-value-discard"
    )
  )
)

// Compiler plugins.
val betterMonadicForVersion = "0.3.1"
val gcsLibVersion = "0.1.0"

// Data types & control flow.
val catsVersion = "1.6.0"
val catsEffectVersion = "1.2.0"
val enumeratumVersion = "1.5.13"
val fs2Version = "1.0.5"

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

// Settings to apply to all sub-projects.
// Can't be applied at the build level because of scoping rules.
val commonSettings = Seq(
  addCompilerPlugin("com.olegpy" %% "better-monadic-for" % betterMonadicForVersion),
  Compile / console / scalacOptions := (Compile / scalacOptions).value.filterNot(
    Set(
      "-Xfatal-warnings",
      "-Xlint",
      "-Ywarn-unused",
      "-Ywarn-unused-import"
    )
  ),
  resolvers ++= Seq(
    "Broad Artifactory Releases" at "https://broadinstitute.jfrog.io/broadinstitute/libs-release/",
    "Broad Artifactory Snapshots" at "https://broadinstitute.jfrog.io/broadinstitute/libs-snapshot/"
  ),
  Compile / doc / scalacOptions += "-no-link-warnings",
  Test / fork := true,
  IntegrationTest / fork := true
)

lazy val `monster-ingester` = project
  .in(file("."))
  .aggregate(jadeclient)

lazy val `jadeclient` = project
  .in(file("jade_client"))
  .configs(IntegrationTest)
  .enablePlugins(BuildInfoPlugin)
  .settings(commonSettings)
  .settings(libraryDependencies ++= Seq(
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
  ))