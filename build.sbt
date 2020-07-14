import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm
import com.typesafe.sbt.SbtMultiJvm.multiJvmSettings

lazy val scala212               = "2.12.10"
lazy val scala213               = "2.13.1"
lazy val supportedScalaVersions = List(scala212, scala213)

organization := "com.swissborg"
name := "lithium"
scalaVersion := scala213

lazy val publishSettings = Seq(
  licenses := Seq("Apache-2.0" -> url("https://opensource.org/licenses/Apache-2.0")),
  homepage := Some(url("https://github.com/SwissBorg/lithium")),
  scmInfo := Some(ScmInfo(url("https://github.com/SwissBorg/lithium"), "scm:git@github.com:SwissBorg/lithium.git")),
  developers := List(
    Developer(
      "DennisVDB",
      "Dennis van der Bij",
      "d.vanderbij@gmail.com",
      url("https://github.com/DennisVDB")
    )
  )
)

lazy val scalacOptionsOnly212 = Seq("-Ypartial-unification", "-Xfuture", "-Yno-adapted-args")
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
    "-Ywarn-dead-code",
    "-Ywarn-unused",
    "-Ywarn-numeric-widen",
    "-Ywarn-value-discard",
    "-deprecation"
  ) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, 12)) => scalacOptionsOnly212
    case _             => Seq()
  })

val akkaVersion                = "2.6.6"
val catsVersion                = "2.1.1"
val catsEffectVersion          = "2.1.4"
val scalacheckVersion          = "1.14.3"
val scalatestVersion           = "3.1.2"
val scalatestplusVersion       = "3.1.2.0"
val scalacheckShapelessVersion = "1.2.5"
val shapelessVersion           = "2.3.3"
val logbackVersion             = "1.2.3"
val slf4jApiVersion            = "1.7.30"
val kindProjectorVersion       = "0.11.0"
val betterMonadicForVersion    = "0.3.1"

resolvers += Resolver.sonatypeRepo("releases")

// Akka
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor"              % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster"            % akkaVersion,
  "com.typesafe.akka" %% "akka-remote"             % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit"            % akkaVersion % Test,
  "com.typesafe.akka" %% "akka-multi-node-testkit" % akkaVersion % Test
)

// Logging
libraryDependencies ++= Seq(
  "org.slf4j"         % "slf4j-api"       % slf4jApiVersion,
  "com.typesafe.akka" %% "akka-slf4j"     % akkaVersion % Test,
  "ch.qos.logback"    % "logback-classic" % logbackVersion % Test
)

// Cats
libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-core"    % catsVersion,
  "org.typelevel" %% "cats-kernel"  % catsVersion,
  "org.typelevel" %% "cats-effect"  % catsEffectVersion,
  "org.typelevel" %% "cats-testkit" % catsVersion % Test
)

// Shapeless
libraryDependencies ++= Seq(
  "com.chuusai" %% "shapeless" % shapelessVersion
)

// ScalaTest
libraryDependencies ++= Seq(
  )

// Tests
libraryDependencies ++= Seq(
  "org.scalatest"              %% "scalatest"                 % scalatestVersion           % Test,
  "org.scalacheck"             %% "scalacheck"                % scalacheckVersion          % Test,
  "org.scalatestplus"          %% "scalacheck-1-14"           % scalatestplusVersion       % Test,
  "com.github.alexarchambault" %% "scalacheck-shapeless_1.14" % scalacheckShapelessVersion % Test
)

addCompilerPlugin(("org.typelevel" % "kind-projector" % kindProjectorVersion).cross(CrossVersion.full))

addCompilerPlugin("com.olegpy" %% "better-monadic-for" % betterMonadicForVersion)

lazy val root = (project in file("."))
  .enablePlugins(MultiJvmPlugin)
  .configs(MultiJvm)
  .settings(multiJvmSettings: _*)
  .settings(parallelExecution in Test := false)
  .settings(crossScalaVersions := supportedScalaVersions)
  .settings(publishSettings)

scalafmtOnCompile := true

testOptions in Test += Tests.Argument("-oF")

parallelExecution in MultiJvm := false
