package configuration

import com.softwaremill.macwire._
import controllers._
import lib.actions.Actions
import lib.{Bot, Permissions}
import play.api.routing.Router
import play.api.{ApplicationLoader, BuiltInComponentsFromContext}
import router.Routes

class ApplicationComponents(context: ApplicationLoader.Context)
  extends BuiltInComponentsFromContext(context) with ReasonableHttpFilters
    with AssetsComponents {

  val bot: Bot = Bot.from(configuration)
  val permissions: Permissions = wire[Permissions]
  implicit val authClient: com.madgag.playgithub.auth.Client = com.madgag.playgithub.auth.Client(
    id = configuration.get[String]("github.clientId"),
    secret = configuration.get[String]("github.clientSecret")
  )

  val actions: Actions = wire[Actions]

  val controllerAppComponents = wire[ControllerAppComponents]

  val authController: Auth = wire[_root_.controllers.Auth]
  val applicationController: Application = wire[_root_.controllers.Application]

  val router: Router = {
    // add the prefix string in local scope for the Routes constructor
    val prefix: String = "/"
    wire[Routes]
  }

}
