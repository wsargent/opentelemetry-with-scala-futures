import java.time.Instant

// https://www.playframework.com/documentation/2.9.x/Anatomy#Default-sbt-layout
lazy val root = (project in file("."))
  .enablePlugins(PlayScala, JavaAgent)
  .disablePlugins(PlayLayoutPlugin)
  .settings(
    name := """opentelemetry-with-scala-futures""",
    organization := "com.example",
    version := "1.0-SNAPSHOT",
    scalaVersion := "3.7.0",
    // automatically fork and add agent on "test", and "runProd"
    // Note that intellij will not fork correctly so you have to run from CLI
    // https://github.com/sbt/sbt-javaagent?tab=readme-ov-file#scopes
    javaAgents += "io.opentelemetry.javaagent" % "opentelemetry-javaagent" % "2.15.0" % "dist;test",
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
      s"""
      |# run jaeger-all-in-one to see traces, or set this to none
      |addJava "-Dotel.traces.exporter=otlp"
      |addJava "-Dotel.metrics.exporter=none"
      |addJava "-Dotel.logs.exporter=none"
      |addJava "-Dotel.javaagent.debug=false"
      |addJava "-Dotel.javaagent.logging=application"
      |addJava "-Dotel.resource.attributes=service.name=${name.value},service.version=${version.value},artifact.build_time=${Instant.now.toString}"
      |# process.command_line is very large and potentially a security risk, do not include it
      |addJava "-Dotel.java.disabled.resource.providers=io.opentelemetry.instrumentation.resources.ProcessResourceProvider",
      |addJava "-Dio.opentelemetry.javaagent.shaded.io.opentelemetry.context.enableStrictContext=false"
      |""".stripMargin,
    libraryDependencies ++= Seq(
      guice,
      ws,
      "io.opentelemetry" % "opentelemetry-api" % "1.50.0",
      "io.opentelemetry" % "opentelemetry-sdk" % "1.50.0",
      "io.opentelemetry" % "opentelemetry-exporter-logging" % "1.50.0",
      "io.opentelemetry.semconv" % "opentelemetry-semconv" % "1.32.0",
      "io.opentelemetry" % "opentelemetry-sdk-extension-autoconfigure" % "1.50.0",
      // Testing
      "org.scalatestplus.play" %% "scalatestplus-play" % "7.0.1" % Test
    ),
    scalacOptions ++= Seq(
      "-feature"
    )
  )
