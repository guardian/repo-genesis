package lib

import com.madgag.scalagithub.model.Org
import com.madgag.scalagithub.{GitHub, GitHubCredentials}
import okhttp3.OkHttpClient
import play.api.{Configuration, Logger}

import java.nio.file.Path
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

case class Bot(
  workingDir: Path,
  github: GitHub,
  org: Org,
  teamsAllowedToCreatePrivateRepos: Set[Long]
) {
  val orgLogin: String = org.login
}

object Bot {
  def from(configuration: Configuration): Bot = {
    val workingDir = Path.of("/tmp", "bot", "working-dir")
    val orgName = configuration.get[String]("github.org")

    val accessToken = configuration.get[String]("github.botAccessToken")

    val ghCreds = GitHubCredentials.forAccessKey(accessToken, workingDir).get

    val github = new GitHub(ghCreds)
    val org = Await.result(github.getOrg(orgName), 4.seconds)

    lazy val teamsAllowedToCreatePrivateRepos: Set[Long] = {
      val teamString: String = configuration.get[String]("github.teams.can.create.repos.private")

      val teamIdStrings: Set[String] = teamString.split(',').toSet.filter(_.nonEmpty)
      teamIdStrings.map(_.toLong)
    }
    Bot(
      workingDir,
      github,
      org.result,
      teamsAllowedToCreatePrivateRepos
    )
  }
}