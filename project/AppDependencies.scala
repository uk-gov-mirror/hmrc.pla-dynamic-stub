import sbt._

object AppDependencies {
import play.core.PlayVersion
  import play.sbt.PlayImport._

  private val bootstrapPlayVersion = "1.3.0"
  private val hmrcTestVersion = "3.9.0-play-26"
  private val playReactiveMongoVersion = "7.23.0-play-26"
  private val pegdownVersion = "1.6.0"
  private val scalaTestVersion = "3.0.8"

  val compile = Seq(
    ws,
    "uk.gov.hmrc" %% "bootstrap-play-26" % bootstrapPlayVersion,
    "uk.gov.hmrc" %% "simple-reactivemongo" % playReactiveMongoVersion,
    "uk.gov.hmrc" %% "stub-data-generator" % "0.5.3",
    "org.scalacheck" %% "scalacheck" % "1.13.5",
    "io.github.amrhassan" %% "scalacheck-cats" % "0.4.0"
  )

  trait TestDependencies {
    lazy val scope: String = "test"
    lazy val test : Seq[ModuleID] = ???
  }

  object Test {
    def apply() = new TestDependencies {
      override lazy val test = Seq(
        "uk.gov.hmrc" %% "hmrctest" % hmrcTestVersion % scope,
        "org.scalatest" %% "scalatest" % scalaTestVersion % scope,
        "org.pegdown" % "pegdown" % pegdownVersion % scope,
        "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
        "org.mockito" % "mockito-core" % "2.3.4" % scope,
        "uk.gov.hmrc" %% "domain" % "5.6.0-play-26",
        "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.3" % scope
      )
    }.test
  }

  def apply(): Seq[ModuleID] = compile ++ Test()
}

