package lib

import java.util

import com.madgag.github.GitHubCredentials
import lib.scalagithub.GitHub
import org.kohsuke.github.{GHTeam, GHMyself}
import com.madgag.github.Implicits._

import scala.concurrent.Future
import scalax.file.ImplicitConversions._
import scalax.file.Path
import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.convert.wrapAsScala._

object Bot {

  val workingDir = Path.fromString("/tmp") / "bot" / "working-dir"

  import play.api.Play.current
  val config = play.api.Play.configuration

  val org = config.getString("github.org").get

  val accessToken = config.getString("github.botAccessToken").get

  val ghCreds = GitHubCredentials.forAccessKey(accessToken, workingDir.toPath).get

  def conn() = ghCreds.conn()

  val neoGitHub = new GitHub(ghCreds)

  val orgUser = conn().getOrganization(org)

  lazy val teamsAllowedToCreatePrivateRepos: Set[Long] = {

    val teamString: String = config.getString("github.teams.can.create.repos.private").get
    println(s"teamString = $teamString")

    val teamIds: Set[Long] = teamString.split(',').toSet.map(_.toLong)

    println(s"teamIds = $teamIds")

    teamIds
  }

}
