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

  lazy val teamsAllowedToCreatePrivateRepos = {

    val teamString: String = config.getString("github.teams.can.create.repos.private").get
    println(s"teamString = $teamString")

    val teamNames: Set[String] = teamString.split(',').toSet

    println(s"teamNames = $teamNames")

    val allteams = orgUser.getTeams

    println(s"allteams.size = ${allteams.size}")
    println(s"allteams.keys = ${allteams.keys}")

    val teamsThatCanDoPrivate: Set[GHTeam] = orgUser.getTeams.filterKeys(teamNames).values.toSet

    println(s"teamsThatCanDoPrivate = $teamsThatCanDoPrivate")

    teamsThatCanDoPrivate
  }

}
