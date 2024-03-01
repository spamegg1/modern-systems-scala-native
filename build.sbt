import scala.scalanative.build.*

scalaVersion := "3.4.0"
enablePlugins(ScalaNativePlugin)
libraryDependencies += "io.argonaut" %% "argonaut" % "6.3.9"
scalacOptions += "-deprecation"

// Enable verbose reporting during compilation
nativeConfig ~= { c =>
  c.withCompileOptions(
    c.compileOptions ++ Seq(
      // "-fsanitize=address"
      // "-v"
    )
  )
}

// Use an alternate linker
nativeConfig ~= { c =>
  c.withLinkingOptions(
    c.linkingOptions ++ Seq(
      "-fuse-ld=lld"
    )
  )
}
