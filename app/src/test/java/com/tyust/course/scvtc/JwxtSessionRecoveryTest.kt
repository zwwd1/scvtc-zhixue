package com.tyust.course.scvtc

import cn.scvtc.campus.*
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

/** Exercises the real OkHttp request/JSON boundary, not browser URL matching. */
class JwxtSessionRecoveryTest {
    private val student="""{"code":200,"data":{"rows":[{"xh":"2026000001","xm":"Fixture","password":"excluded"}]}}"""
    private fun api(reply:(Request)->Pair<Int,String>):JwxtApi=JwxtApi(OkHttpClient.Builder().addInterceptor { chain ->
        val (code,body)=reply(chain.request())
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("fixture")
            .body(body.toResponseBody("application/json".toMediaType())).build()
    }.build())

    @Test fun unauthorizedSessionRecoversOnceAndRevalidatesStudentApi()=runBlocking {
        var requests=0;var recoveries=0
        val api=api { request ->
            assertEquals(JwxtApi.IDENTITY,request.url.toString().substringBefore('?'));assertEquals("POST",request.method)
            requests++;if(requests==1)401 to "{}" else 200 to student
        }
        val session=JwxtSessionManager(api){recoveries++}
        val value=session.verifiedStudent("2026000001")
        assertEquals("2026000001",value.account);assertEquals(1,recoveries);assertEquals(2,requests)
        assertFalse(value.fields.containsKey("password"))
    }

    @Test fun failedRecoveryDoesNotLoopOrReturnAFalseSuccess()=runBlocking {
        var requests=0;var recoveries=0
        val session=JwxtSessionManager(api { requests++;302 to "" }){recoveries++}
        val error=runCatching{session.verifiedStudent("2026000001")}.exceptionOrNull()
        assertTrue(error is JwxtAuthenticationRequired);assertEquals(1,recoveries);assertEquals(2,requests)
    }

    @Test fun expiredReadContinuesOnlyAfterASecondVerifiedIdentity()=runBlocking {
        var identities=0;var reads=0;var recoveries=0
        val session=JwxtSessionManager(api { identities++;200 to student }){recoveries++}
        val result=session.withSession("2026000001") { s ->
            reads++;if(reads==1)throw JwxtAuthenticationRequired();s.account
        }
        assertEquals("2026000001",result);assertEquals(2,identities);assertEquals(2,reads);assertEquals(1,recoveries)
    }

    @Test fun successfulHttpWithLoginHtmlOrBusinessAuthenticationErrorIsNotLogin()=runBlocking {
        for(body in listOf("<html>Login</html>","""{"code":51000010,"data":null}""","""{"code":401,"data":null}""")){
            assertTrue(runCatching {api {200 to body}.student("2026000001")}.exceptionOrNull() is JwxtAuthenticationRequired)
        }
    }

    @Test fun anotherStudentDoesNotTriggerCredentialSubmission()=runBlocking {
        var recoveries=0
        val session=JwxtSessionManager(api {200 to student}){recoveries++}
        val error=runCatching{session.verifiedStudent("2026000002")}.exceptionOrNull()
        assertTrue(error is IllegalStateException);assertFalse(error is JwxtAuthenticationRequired);assertEquals(0,recoveries)
    }

    @Test fun nativeRequestRegeneratesOfficialNonceAndUsesScopedObservedHeaders()=runBlocking {
        val requests=mutableListOf<Request>()
        val client=OkHttpClient.Builder().addInterceptor{chain->
            requests+=chain.request()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                .body(student.toResponseBody("application/json".toMediaType())).build()
        }.build()
        JwxtApi(client){path->assertEquals(JwxtApi.IDENTITY,path);mapOf("permission" to "observed-fixture","Cookie" to "must-not-copy")}.student("2026000001")
        val request=requests.single();val timestamp=request.url.queryParameter("_t")!!
        val expected=java.security.MessageDigest.getInstance("MD5").digest((timestamp+"lyedu").toByteArray()).joinToString(""){"%02x".format(it.toInt() and 255)}
        assertEquals(expected,request.header("csrfToken"));assertEquals("observed-fixture",request.header("permission"))
        assertNull(request.header("Cookie"));assertEquals("https://jwxt.scvtc.edu.cn",request.header("Origin"))
        assertTrue(kotlin.math.abs(System.currentTimeMillis()/1000-timestamp.toLong())<=2)
    }

    @Test fun actualSchoolExpiredSessionCodeRecoversAfterHttp510()=runBlocking {
        var requests=0;var recoveries=0
        val session=JwxtSessionManager(api{requests++;if(requests==1)510 to """{"code":51000010}""" else 200 to student}){recoveries++}
        assertEquals("2026000001",session.verifiedStudent("2026000001").account)
        assertEquals(1,recoveries);assertEquals(2,requests)
    }

    @Test fun serverOrProtocolFailureDoesNotTriggerPasswordRetry()=runBlocking {
        for(reply in listOf(500 to "{}",200 to "broken JSON",200 to """{"code":500,"data":null}""")){
            var recoveries=0
            val session=JwxtSessionManager(api {reply}){recoveries++}
            assertTrue(runCatching{session.verifiedStudent("2026000001")}.isFailure)
            assertEquals(0,recoveries)
        }
    }
}
