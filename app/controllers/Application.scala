package controllers

import akka.actor.Scheduler
import akka.pattern.after
import com.madgag.github.Implicits._
import com.madgag.playgithub.auth.{GHRequest, MinimalGHPerson}
import com.madgag.scalagithub.GitHubResponse
import com.madgag.scalagithub.commands.CreateRepo
import com.madgag.scalagithub.model.{RepoId, Team, User}
import com.madgag.slack.Slack.Message
import controllers.RetryDelays.fibonacci
import lib.Permissions.allowedToCreatePrivateRepos
import lib.actions.Actions._
import lib.{Bot, Permissions}
import play.api.Logger
import play.api.Play.current
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages.Implicits._
import play.api.libs.json.Json
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{FiniteDuration, _}
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.{Random, Success}

case class RepoNameCheckResults(isGood: Boolean, existingRepo: Option[String])

object RepoNameCheckResults {
  val Good = RepoNameCheckResults(true, None)

  implicit val writesRepoNameCheckResults = Json.writes[RepoNameCheckResults]
}

// https://gist.github.com/viktorklang/9414163
object RetryDelays {
  def withJitter(delays: Seq[FiniteDuration], maxJitter: Double, minJitter: Double) =
    delays.map(_ * (minJitter + (maxJitter - minJitter) * Random.nextDouble))

  val fibonacci: Stream[FiniteDuration] = 0.seconds #:: 1.seconds #:: (fibonacci zip fibonacci.tail).map{ t => t._1 + t._2 }
}

object Application extends Controller {

  implicit val s = play.api.libs.concurrent.Akka.system.scheduler

  def retry[T](desc: String, f: => Future[T], delays: Seq[FiniteDuration])(acceptable: T => Boolean)(implicit ec: ExecutionContext, s: Scheduler ): Future[T] = {
    f.filter(acceptable) recoverWith {
      case _ if delays.nonEmpty =>
        val retryDelay = delays.head
        Logger.info(s"Will retry '$desc' after $retryDelay")
        after(retryDelay, s)(retry(desc, f, delays.tail)(acceptable))
    }
  }

  def about = Action { implicit req =>
    Ok(views.html.about())
  }

  case class RepoCreation(name: String, teamId: Long, isPrivate: Boolean)

  val repoCreationForm = Form(mapping(
    "name" -> text(minLength = 1, maxLength = 100),
    "teamId" -> longNumber,
    "private" -> boolean
  )(RepoCreation.apply)(RepoCreation.unapply))

  def newRepo = OrgAuthenticated.async { implicit req =>
    for {
      user <- req.userF
      userTeams <- req.userTeamsF
      allowPrivate <- allowedToCreatePrivateRepos(req)
      privateRepoTeams <- Permissions.privateRepoTeams()
    } yield {
      val userTeamsInOrg = userTeams.filter(_.organization.id == Bot.org.id).sortBy(_.members_count)
      println(s"${user.atLogin} userTeams (${userTeams.size}) : ${userTeams.take(1).mkString(",")} ...")
      println(s"privateRepoTeams = $privateRepoTeams")
      Ok(views.html.createNewRepo(repoCreationForm, userTeamsInOrg, allowPrivate, privateRepoTeams))
    }
  }

  def checkProposedRepoName(repoName: String) = OrgAuthenticated.async { implicit req =>
    val prospectiveId = RepoId(Bot.orgName, repoName)
    for {
      repoTry <- Bot.github.getRepo(prospectiveId).trying
      userRepoTry <- req.gitHub.getRepo(prospectiveId).trying
    } yield {
      val results = repoTry match {
        case Success(repo) => RepoNameCheckResults(false, Some(repo.result.html_url))
        case _ => RepoNameCheckResults.Good
      }
      Ok(Json.toJson(results))
    }
  }


  /*
    Create repo results:
      * Repo already exists, you can see/admin it
      * Repo already exists, it's private and you don't have access
      * Repo has been created (user should be taken to admin screen)
      * Repo creation failed...
   */
  def createRepo = OrgAuthenticated.async(parse.form(repoCreationForm)) { implicit req =>
    val repoCreation = req.body
    val repoId = RepoId(Bot.org.login, repoCreation.name)
    Logger.info(s"@${MinimalGHPerson.fromRequest(req).map(_.login)} wants to create ${repoId.fullName}")
    for {
      _ <- assertUserAllowedToCreateRepoF
      team <- Bot.github.getTeam(repoCreation.teamId)
      user <- req.userF
      result <- executeCreationAndRedirectToRepo(user, repoCreation, team)
    } yield result
  }

  def assertUserAllowedToCreateRepoF(implicit req: GHRequest[RepoCreation]) =
    if (req.body.isPrivate) allowedToCreatePrivateRepos(req) else Future.successful(true)


  def executeCreationAndRedirectToRepo(user: User, repoCreation: RepoCreation, repoTeam: Team)(implicit req: RequestHeader): Future[Result] = {

    val command = CreateRepo(
      name = repoCreation.name,
      description = Some(s"Placeholder description: ${user.atLogin} created this with repo-genesis"),
      `private` = repoCreation.isPrivate)

    for {
      createdRepo <- Bot.github.createOrgRepo(Bot.orgName, command)
      creationString = s"${user.atLogin} created ${command.publicOrPrivateString} repo ${createdRepo.html_url}"
      _ = Logger.info(creationString)
      teamAddResult: GitHubResponse[Boolean] <- retry[GitHubResponse[Boolean]](s"Make team ${repoCreation.teamId} admin for '${repoCreation.name}'", Bot.github.addTeamRepo(repoCreation.teamId, Bot.orgName, repoCreation.name), fibonacci.take(5))( _.result == true)
      _ = Logger.info(s"${user.atLogin} added repo ${createdRepo.html_url} for team ${repoCreation.teamId}: ${teamAddResult.result}")
    } yield {
      val teamAddSucceeded = teamAddResult.result
      if (!teamAddSucceeded) {
        Logger.error(s"Failed to set team permission on ${createdRepo.html_url} for team ${repoCreation.teamId}") // trigger sentry
      }

      for (slack <- Bot.slackOpt) {
        slack.send(Message(
            s"$creationString for GitHub team ${repoTeam.atSlug} with Repo Genesis: ${routes.Application.about().absoluteURL()}",
          Some("repo-genesis"),
          Some(user.avatar_url),
          Seq.empty
        ))
      }
      Redirect(createdRepo.collaborationSettingsUrl)
    }
  }
}
