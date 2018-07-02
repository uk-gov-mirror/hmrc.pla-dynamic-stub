import sbt._
import uk.gov.hmrc.SbtAutoBuildPlugin
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin
import uk.gov.hmrc.versioning.SbtGitVersioning

object StubServiceBuild extends Build with MicroService {
  import scala.util.Properties.envOrElse

  val appName = "pla-dynamic-stub"


  override lazy val appDependencies: Seq[ModuleID] = AppDependencies()
}

private object AppDependencies {
  import play.sbt.PlayImport._
  import play.core.PlayVersion


  private val microserviceBootstrapVersion = "6.18.0"
  private val hmrcTestVersion = "3.0.0"
  private val playReactiveMongoVersion = "6.2.0"
  private val pegdownVersion = "1.6.0"
  private val scalaTestVersion = "3.0.0"

  val compile = Seq(
    ws,
    "uk.gov.hmrc" %% "microservice-bootstrap" % microserviceBootstrapVersion,
    "uk.gov.hmrc" %% "play-reactivemongo" % playReactiveMongoVersion,
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
        "org.mockito" % "mockito-core" % "2.13.0" % scope,
        "uk.gov.hmrc" %% "domain" % "5.1.0",
        "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.0" % scope
      )
    }.test

  }

  def apply() = compile ++ Test()
}

