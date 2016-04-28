package controllers

import com.madgag.playgithub.auth.GHRequest
import com.madgag.scalagithub.commands.CreateRepo
import com.madgag.scalagithub.model.{Team, User}
import com.madgag.slack.Slack.Message
import lib.Permissions.allowedToCreatePrivateRepos
import lib.actions.Actions._
import lib.{Bot, Permissions}
import play.api.Logger
import play.api.Play.current
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages.Implicits._
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Application extends Controller {

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

  def createRepo = OrgAuthenticated.async(parse.form(repoCreationForm)) { implicit req =>
    val repoCreation = req.body
    for {
      _ <- assertUserAllowedToCreateRepoF
      team <- Bot.github.getTeam(repoCreation.teamId)
      user <- req.userF
      result <- executeCreationAndRedirectToRepo(user, req.body, team)
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
      teamAddResult <- Bot.github.addTeamRepo(repoCreation.teamId, Bot.orgName, repoCreation.name)
      _ = Logger.info(s"${user.atLogin} added repo ${createdRepo.html_url} for team ${repoCreation.teamId}: ${teamAddResult.result}")
    } yield {
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
