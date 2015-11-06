package lib

import com.madgag.github.GitHubCredentials
import com.madgag.slack.Slack
import com.squareup.okhttp.OkHttpClient
import lib.scalagithub.GitHub

import scalax.file.ImplicitConversions._
import scalax.file.Path

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

    val teamIdStrings: Set[String] = teamString.split(',').toSet
    
    val teamIds: Set[Long] = teamIdStrings.map(_.toLong)

    println(s"teamIds = $teamIds")

    teamIds
  }

  val slackOpt = config.getString("slack.webhook.url").map(hookUrl => new Slack(hookUrl, new OkHttpClient()))
}
