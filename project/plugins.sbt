resolvers += Resolver.bintrayRepo("kamon-io", "sbt-plugins")

addSbtPlugin("io.kamon"                % "sbt-aspectj-runner" % "1.1.0")
addSbtPlugin("io.spray"                % "sbt-revolver"       % "0.9.1")

// Multi-JVM testing
addSbtPlugin("com.typesafe.sbt" % "sbt-multi-jvm" % "0.4.0")

// Kind-projector
addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.9")

// Scalafmt
addSbtPlugin("com.geirsson" % "sbt-scalafmt" % "1.5.1")

// Coursier
addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.1.0-M11")

// Scalafix
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.9.4")

//WartRemover
addSbtPlugin("org.wartremover" % "sbt-wartremover" % "2.4.1")