package lib

import com.madgag.github.GitHubCredentials
import lib.scalagithub.GitHub
import org.kohsuke.github.GHMyself
import com.madgag.github.Implicits._

import scala.concurrent.Future
import scalax.file.ImplicitConversions._
import scalax.file.Path
import scala.concurrent.ExecutionContext.Implicits.global

object Bot {

  val workingDir = Path.fromString("/tmp") / "bot" / "working-dir"

  import play.api.Play.current
  val config = play.api.Play.configuration

  val org = config.getString("github.org").get

  val accessToken = config.getString("github.botAccessToken").get

  val ghCreds = GitHubCredentials.forAccessKey(accessToken, workingDir.toPath).get

  def conn() = ghCreds.conn()

  def neoGitHub = new GitHub(ghCreds)

  val orgUser = conn().getOrganization(org)

  val teamsAllowedToCreatePrivateRepos =
    config.getString("github.teams.can.create.repos.private").get.split(',').map(t => orgUser.getTeamByName(t.trim)).toSet

  def allowedToCreatePrivateRepos(user: GHMyself): Future[Boolean] = {
    for (membershipResponses <- Future.traverse(teamsAllowedToCreatePrivateRepos)(t => neoGitHub.getTeamMembership(t.getId, user.getLogin).trying)) yield {
      membershipResponses.exists(_.map(_.result.state == "active").getOrElse(false))
    }
  }
}
