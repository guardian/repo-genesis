package controllers

import akka.actor.{ActorSystem, Scheduler}
import akka.pattern.after
import com.madgag.github.Implicits._
import com.madgag.playgithub.auth.{GHRequest, MinimalGHPerson}
import com.madgag.scalagithub.GitHubResponse
import com.madgag.scalagithub.commands.CreateRepo
import com.madgag.scalagithub.model.{RepoId, Team, User}
import controllers.Application.{RepoCreation, RepoCreationForm, retry}
import controllers.RetryDelays.fibonacci
import lib.{Bot, Permissions}
import play.api.Logging
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.I18nSupport
import play.api.libs.json.Json
import play.api.mvc._

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
    delays.map(_ * (minJitter + (maxJitter - minJitter) * Random.nextDouble()))

  val fibonacci: LazyList[FiniteDuration] = 0.seconds #:: 1.seconds #:: (fibonacci zip fibonacci.tail).map{ t => t._1 + t._2 }
}

object Application extends Logging {

  case class RepoCreation(name: String, teamId: Long, isPrivate: Boolean)

  val RepoCreationForm = Form(mapping(
    "name" -> text(minLength = 1, maxLength = 100),
    "teamId" -> longNumber,
    "private" -> boolean
  )(RepoCreation.apply)(RepoCreation.unapply))

  def retry[T](desc: String, f: => Future[T], delays: Seq[FiniteDuration])(acceptable: T => Boolean)(implicit ec: ExecutionContext, s: Scheduler): Future[T] = {
    f.filter(acceptable) recoverWith {
      case _ if delays.nonEmpty =>
        val retryDelay = delays.head
        logger.info(s"Will retry '$desc' after $retryDelay")
        after(retryDelay, s)(retry(desc, f, delays.tail)(acceptable))
    }
  }

}

class Application(
  bot: Bot,
  permissions: Permissions,
  actorSystem: ActorSystem,
  cc: ControllerAppComponents
) extends AbstractAppController(cc) with Logging with I18nSupport {

  implicit val scheduler = actorSystem.scheduler

  def about = Action { implicit req =>
    Ok(views.html.about(bot.org))
  }

  def newRepo = OrgAuthenticated.async { implicit req: GHRequest[AnyContent] =>
    for {
      user <- req.userF
      userTeams <- req.userTeamsF
      allowPrivate <- permissions.allowedToCreatePrivateRepos(req)
      privateRepoTeams <- permissions.privateRepoTeams()
    } yield {
      val userTeamsInOrg = userTeams.filter(_.organization.id == bot.org.id).sortBy(_.members_count)
      println(s"${user.atLogin} userTeams (${userTeams.size}) : ${userTeams.take(1).mkString(",")} ...")
      println(s"privateRepoTeams = $privateRepoTeams")
      Ok(views.html.createNewRepo(bot.org, RepoCreationForm, userTeamsInOrg, allowPrivate, privateRepoTeams))
    }
  }

  def checkProposedRepoName(repoName: String) = OrgAuthenticated.async { implicit req: GHRequest[_] =>
    val prospectiveId = RepoId(bot.orgLogin, repoName)
    for {
      repoTry <- bot.github.getRepo(prospectiveId).trying
      userRepoTry <- req.gitHub.getRepo(prospectiveId).trying
    } yield Ok(Json.toJson(repoTry match {
      case Success(repo) => RepoNameCheckResults(false, Some(repo.result.html_url))
      case _ => RepoNameCheckResults.Good
    }))
  }


  /*
    Create repo results:
      * Repo already exists, you can see/admin it
      * Repo already exists, it's private and you don't have access
      * Repo has been created (user should be taken to admin screen)
      * Repo creation failed...
   */
  def createRepo = OrgAuthenticated.async(parse.form(RepoCreationForm)) { implicit req: GHRequest[RepoCreation] =>
    val repoCreation = req.body
    val repoId = RepoId(bot.org.login, repoCreation.name)
    logger.info(s"@${MinimalGHPerson.fromRequest(req).map(_.login)} wants to create ${repoId.fullName}")
    for {
      _ <- assertUserAllowedToCreateRepoF
      team <- bot.github.getTeam(repoCreation.teamId)
      user <- req.userF
      result <- executeCreationAndRedirectToRepo(user, repoCreation, team)
    } yield result
  }

  def assertUserAllowedToCreateRepoF(implicit req: GHRequest[RepoCreation]): Future[Boolean] =
    if (req.body.isPrivate) permissions.allowedToCreatePrivateRepos(req) else Future.successful(true)


  def executeCreationAndRedirectToRepo(user: User, repoCreation: RepoCreation, repoTeam: Team)(implicit req: RequestHeader): Future[Result] = {

    val command = CreateRepo(
      name = repoCreation.name,
      description = Some(s"Placeholder description: ${user.atLogin} created this with repo-genesis"),
      `private` = repoCreation.isPrivate)

    for {
      createdRepo <- bot.github.createOrgRepo(bot.orgLogin, command)
      creationString = s"${user.atLogin} created ${command.publicOrPrivateString} repo ${createdRepo.html_url}"
      _ = logger.info(creationString)
      teamAddResult: GitHubResponse[Boolean] <- retry[GitHubResponse[Boolean]](
        s"Make team ${repoCreation.teamId} admin for '${repoCreation.name}'",
        bot.github.addTeamRepo(repoCreation.teamId, bot.orgLogin, repoCreation.name),
        fibonacci.take(5)
      )( _.result == true)
      _ = logger.info(s"${user.atLogin} added repo ${createdRepo.html_url} for team ${repoCreation.teamId}: ${teamAddResult.result}")
    } yield {
      val teamAddSucceeded = teamAddResult.result
      if (!teamAddSucceeded) {
        logger.error(s"Failed to set team permission on ${createdRepo.html_url} for team ${repoCreation.teamId}") // trigger sentry
      }

      Redirect(createdRepo.collaborationSettingsUrl)
    }
  }
}
