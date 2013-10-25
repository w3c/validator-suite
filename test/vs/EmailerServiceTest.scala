package vs

import org.w3.vs.util.VSTest
import org.w3.vs.{Emails, EmailService, ValidatorSuite}
import play.api.Mode
import org.w3.vs.model.User

/**
 * /!\ Not an automated test!
 *
 * To test sending an email through VS:
 * 1. remove the 'abstract' keyword in front of this class
 * 2. set-up an smtp server configuration in application-test.conf
 * 3. edit the 'email' val below to your own email
 * 4. run 'test-only vs.EmailServiceTest' in sbt
 */
abstract class EmailerServiceTest extends VSTest {

  val email = "my@email"

  implicit def vs: ValidatorSuite with EmailService = new ValidatorSuite with EmailService {
    val mode = Mode.Test
  }

  "EmailService" should {
    "send an email" in {

      vs.sendEmail(Emails.registered(User.create("John Doe", email, "123456", 20)))

      Thread.sleep(12000)

    }
  }
}
