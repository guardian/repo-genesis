package controllers

import com.madgag.playgithub.auth.{AuthController, Client}

case class Auth(
  authClient: Client,
  controllerComponents: ControllerAppComponents
) extends AuthController {

}
