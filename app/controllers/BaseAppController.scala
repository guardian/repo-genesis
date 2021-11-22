package controllers

import com.madgag.playgithub.auth.GHRequest
import lib.actions.Actions
import play.api.Logging
import play.api.http.FileMimeTypes
import play.api.i18n.{Langs, MessagesApi}
import play.api.mvc._

case class ControllerAppComponents(
  actions: Actions,
  actionBuilder: DefaultActionBuilder,
  parsers: PlayBodyParsers,
  messagesApi: MessagesApi,
  langs: Langs,
  fileMimeTypes: FileMimeTypes,
  executionContext: scala.concurrent.ExecutionContext
) extends ControllerComponents

trait BaseAppController extends BaseController with Logging {

  val controllerAppComponents: ControllerAppComponents

  override val controllerComponents = controllerAppComponents

  implicit val ec = controllerAppComponents.executionContext // Controversial? https://www.playframework.com/documentation/2.6.x/ThreadPools

  def OrgAuthenticated: ActionBuilder[GHRequest, AnyContent] = controllerAppComponents.actions.OrgAuthenticated

}

abstract class AbstractAppController(
  val controllerAppComponents: ControllerAppComponents
) extends BaseAppController