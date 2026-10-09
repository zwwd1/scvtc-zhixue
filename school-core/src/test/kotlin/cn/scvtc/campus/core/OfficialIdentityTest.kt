package cn.scvtc.campus.core

import org.junit.Test
import org.junit.Assert.*

class OfficialIdentityTest {
    @Test fun sameAliasesAgree(){assertEquals("2026123456",OfficialIdentity.account("""{"data":{"studentNumber":"2026123456","xh":"2026123456"}}"""))}
    @Test fun nestedOfficialStudentCodeIsAccepted(){assertEquals("2026123456",OfficialIdentity.account("""{"data":{"student":{"studentCode":"2026123456"}}}"""))}
    @Test fun conflictingAccountsAreNotVerified(){assertEquals("",OfficialIdentity.account("""{"data":{"studentNo":"2026123456","studentId":"2026999999"}}"""))}
    @Test fun loginHtmlAndMalformedDataCannotVerifyIdentity(){assertEquals("",OfficialIdentity.account("<html>login</html>"));assertEquals("",OfficialIdentity.account("{bad"))}
    @Test fun genericIdsAndShortNumbersAreNotStudentIdentity(){assertEquals("",OfficialIdentity.account("""{"id":"2026123456","xh":"12"}"""))}
    @Test fun deniedOrUnsuccessfulResponseCannotVerifyAnOldIdentity(){assertEquals("",OfficialIdentity.account("""{"code":401,"data":{"xh":"2026123456"}}"""));assertEquals("",OfficialIdentity.account("""{"success":false,"data":{"xh":"2026123456"}}"""))}
}
