lazy val root = (project in file("."))
  .enablePlugins(PlayScala, JavaAgent)
  .settings(
    name := """opentelemetry-with-scala-futures""",
    organization := "com.example",
    version := "1.0-SNAPSHOT",
    crossScalaVersions := Seq("3.4.0"),
    scalaVersion := crossScalaVersions.value.head,
    // automatically fork and add agent on "test", and "runProd"
    // Note that intellij will not fork correctly so you have to run from CLI
    // https://github.com/sbt/sbt-javaagent?tab=readme-ov-file#scopes
    javaAgents += "io.opentelemetry.javaagent" % "opentelemetry-javaagent" % "2.4.0" % "dist;test",
    // https://opentelemetry.io/docs/zero-code/java/agent/configuration/
    javaOptions ++= Seq(
       "-Dotel.service.name=opentelemetry-with-scala-futures",
       "-Dotel.traces.exporter=logging",
       "-Dotel.metrics.exporter=none",
       "-Dotel.logs.exporter=none",
       "-Dotel.javaagent.debug=false",
       "-Dotel.javaagent.logging=application",
      // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/874#issuecomment-747506873
      // https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/2676/files
       "-Dio.opentelemetry.javaagent.shaded.io.opentelemetry.context.enableStrictContext=true"
    ),
    bashScriptExtraDefines +=
      """
      |addJava "-Dotel.service.name=opentelemetry-with-scala-futures"
      |addJava "-Dotel.traces.exporter=logging"
      |addJava "-Dotel.metrics.exporter=none"
      |addJava "-Dotel.logs.exporter=none"
      |addJava "-Dotel.javaagent.debug=true"
      |addJava "-Dotel.javaagent.logging=application"
      |addJava "-Dio.opentelemetry.javaagent.shaded.io.opentelemetry.context.enableStrictContext=true"
      |addJava "-Dplay.http.secret.key=a-sacrifice-to-the-entropy-gods-awefawefawefawefawef"
      |""".stripMargin,
    libraryDependencies ++= Seq(
      guice,
      "com.lihaoyi" %% "sourcecode" % "0.4.2",
      "io.opentelemetry" % "opentelemetry-api" % "1.38.0",
      "io.opentelemetry" % "opentelemetry-sdk" % "1.38.0",
      "io.opentelemetry" % "opentelemetry-exporter-logging" % "1.38.0",
      "io.opentelemetry.semconv" % "opentelemetry-semconv" % "1.25.0-alpha",
      "io.opentelemetry" % "opentelemetry-sdk-extension-autoconfigure" % "1.38.0",
      "org.scalatestplus.play" %% "scalatestplus-play" % "7.0.1" % Test
    ),
    scalacOptions ++= Seq(
      "-feature",
    )
  )