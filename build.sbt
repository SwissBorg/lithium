import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm
import com.typesafe.sbt.SbtMultiJvm.multiJvmSettings

organization := "com.swissborg"
name := "lithium"
version := "0.9.5"

bintrayOrganization := Some("swissborg")
licenses += ("Apache-2.0", url("https://opensource.org/licenses/Apache-2.0"))
bintrayReleaseOnPublish in ThisBuild := false

scalaVersion := "2.12.10"

scalacOptions ++=
  Seq(
    "-encoding",
    "UTF-8",
    "-feature",
    "-language:existentials",
    "-language:higherKinds",
    "-language:implicitConversions",
    "-language:postfixOps",
    "-language:experimental.macros",
    "-unchecked",
    "-Ypartial-unification",
    "-Ywarn-dead-code",
    "-Ywarn-unused",
    "-Ywarn-numeric-widen",
    "-Ywarn-value-discard",
    "-Xfuture",
    "-Yno-adapted-args",
//    "-Xfatal-warnings",
    "-deprecation"
  )

val akkaVersion                = "2.5.25"
val catsVersion                = "2.0.0"
val catsEffectVersion          = "2.0.0"
val scalatestVersion           = "3.0.8"
val monocleVersion             = "2.0.0"
val shapelessVersion           = "2.3.3"
val refinedVersion             = "0.9.10"
val pureConfigVersion          = "0.12.1"
val scalacheckShapelessVersion = "1.1.8"
val refinedScalacheckVersion   = "0.9.10"
val protobufJavaVersion        = "3.8.0"
val typesafeConfigVersion      = "1.3.4"
val logbackVersion             = "1.2.3"
val circeVersion               = "0.12.1"
val scalaLoggingVersion        = "3.9.2"
val kindProjectorVersion       = "0.10.3"
val betterMonadicForVersion    = "0.3.1"

resolvers += Resolver.sonatypeRepo("releases")

// Akka
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor"              % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster"            % akkaVersion,
  "com.typesafe.akka" %% "akka-remote"             % akkaVersion,
  "com.typesafe"      % "config"                   % typesafeConfigVersion,
  "com.typesafe.akka" %% "akka-testkit"            % akkaVersion % Test,
  "com.typesafe.akka" %% "akka-multi-node-testkit" % akkaVersion % Test
)

// Logging
libraryDependencies ++= Seq(
  "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingVersion
)

// PureConfig
libraryDependencies ++= Seq(
  "com.github.pureconfig" %% "pureconfig-core"    % pureConfigVersion,
  "com.github.pureconfig" %% "pureconfig-generic" % pureConfigVersion
)

// Cats
libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-core"    % catsVersion,
  "org.typelevel" %% "cats-kernel"  % catsVersion,
  "org.typelevel" %% "cats-effect"  % catsEffectVersion,
  "org.typelevel" %% "cats-testkit" % catsVersion % Test
)

// Refined
libraryDependencies ++= Seq(
  "eu.timepit" %% "refined"            % refinedVersion,
  "eu.timepit" %% "refined-pureconfig" % refinedVersion
)

// Circe
libraryDependencies ++= Seq(
  "io.circe" %% "circe-core"    % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion
)

// Shapeless
libraryDependencies ++= Seq(
  "com.chuusai" %% "shapeless" % shapelessVersion
)

// Monocle
libraryDependencies ++= Seq(
  "com.github.julien-truffaut" %% "monocle-core" % monocleVersion,
  "com.github.julien-truffaut" %% "monocle-law"  % monocleVersion % Test
)

// ScalaTest
libraryDependencies ++= Seq(
  "com.github.alexarchambault" %% "scalacheck-shapeless_1.13" % scalacheckShapelessVersion % Test,
  "eu.timepit"                 %% "refined-scalacheck"        % refinedScalacheckVersion   % Test,
  "org.scalatest"              %% "scalatest"                 % scalatestVersion           % Test
)

// ScalaCheck
libraryDependencies ++= Seq(
  "com.github.alexarchambault" %% "scalacheck-shapeless_1.13" % scalacheckShapelessVersion % Test,
  "eu.timepit"                 %% "refined-scalacheck"        % refinedScalacheckVersion   % Test
)

addCompilerPlugin("org.typelevel" %% "kind-projector"     % kindProjectorVersion)
addCompilerPlugin("com.olegpy"    %% "better-monadic-for" % betterMonadicForVersion)

lazy val root = (project in file("."))
  .enablePlugins(MultiJvmPlugin)
  .configs(MultiJvm)
  .settings(multiJvmSettings: _*)
  .settings(parallelExecution in Test := false)

scalafmtOnCompile := true

testOptions in Test += Tests.Argument("-oF")

parallelExecution in MultiJvm := false

wartremoverErrors ++= Warts.unsafe
wartremoverExcluded ++= Seq(
  sourceManaged.value,
  baseDirectory.value / "src" / "test",
  baseDirectory.value / "src" / "multi-jvm"
)
