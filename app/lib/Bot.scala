package lib

import com.madgag.scalagithub.{GitHubCredentials, GitHub}
import com.madgag.slack.Slack
import com.squareup.okhttp.OkHttpClient
import play.api.Logger

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scalax.file.ImplicitConversions._
import scalax.file.Path

object Bot {

  val logger = Logger(getClass)

  val workingDir = Path.fromString("/tmp") / "bot" / "working-dir"

  import play.api.Play.current
  val config = play.api.Play.configuration

  val orgName = config.getString("github.org").get

  val accessToken = config.getString("github.botAccessToken").get

  val ghCreds = GitHubCredentials.forAccessKey(accessToken, workingDir.toPath).get

  val github = new GitHub(ghCreds)

  val org = Await.result(github.getOrg(orgName), 4.seconds)


  lazy val teamsAllowedToCreatePrivateRepos: Set[Long] = {

    val teamString: String = config.getString("github.teams.can.create.repos.private").get
    logger.info(s"teamString = $teamString")

    val teamIdStrings: Set[String] = teamString.split(',').toSet
    
    val teamIds: Set[Long] = teamIdStrings.map(_.toLong)

    logger.info(s"teamIds = $teamIds")

    teamIds
  }

  val slackOpt = config.getString("slack.webhook.url").map(hookUrl => new Slack(hookUrl, new OkHttpClient()))
}
