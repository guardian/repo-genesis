package lib.actions

import com.madgag.playgithub.auth.AuthenticatedSessions.AccessToken
import com.madgag.playgithub.auth.Client
import controllers.Auth
import lib._

import scalax.file.ImplicitConversions._


object Actions {
  private val authScopes = Seq("user:email")

  implicit val authClient: Client = Auth.authClient

  implicit val provider = AccessToken.FromSession

  val GitHubAuthenticatedAction = com.madgag.playgithub.auth.Actions.gitHubAction(authScopes, Bot.workingDir.toPath)


}