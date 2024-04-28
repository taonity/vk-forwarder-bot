package automation.steps

import io.cucumber.java.en.Then
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.http.HttpHeaders
import org.assertj.core.api.Assertions.assertThat
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus

class HttpStepDefinitions (
    @Value("\${forwarder.testing.manual-trigger-password}") private val manualTriggerPassword: String
) {
    private val okHttpClient = OkHttpClient()

    @Then("User sends HTTP request to forward endpoint")
    fun sendHttpRequestToForwardEndpoint() {
        val request = Request.Builder()
            .url("http://localhost:9015/forward")
            .addHeader(HttpHeaders.AUTHORIZATION, manualTriggerPassword)
            .build()

        okHttpClient.newCall(request).execute().use {
            assertThat(it.code).isEqualTo(HttpStatus.OK.value())
        }
    }
}
