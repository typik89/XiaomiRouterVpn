package ru.typik.mi.router.android.client

import okhttp3.Headers
import okhttp3.JavaNetCookieJar
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url
import ru.typik.mi.router.android.client.model.Vpn
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.ProtocolException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlin.random.Random

class AdminClient(
    val baseUrl: String,
    val user: String,
    val password: String
) {

    companion object {
        private const val WEB_LOGIN_PATH = "/cgi-bin/luci/web"
        private const val LOGIN_API_PATH = "/cgi-bin/luci/api/xqsystem/login"
        private val FORM_MEDIA_TYPE =
            "application/x-www-form-urlencoded; charset=UTF-8".toMediaType()
    }

    private lateinit var stok: String
    private lateinit var cookieManager: CookieManager
    private lateinit var api: RouterApi

    fun init() {
        cookieManager = CookieManager().apply { setCookiePolicy(CookiePolicy.ACCEPT_ALL) }

        val client = OkHttpClient.Builder()
            .cookieJar(JavaNetCookieJar(cookieManager))
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(normalizeBaseUrl(baseUrl))
            .client(client)
            .build()

        api = retrofit.create(RouterApi::class.java)
        stok = loginAndGetStok()
    }

    fun changeStatusVpn(vpn: Vpn, isSwitchOn: Boolean): String {
        val path =
            "/cgi-bin/luci/;stok=$stok/api/xqsystem/vpn_switch?conn=${if (isSwitchOn) 1 else 0}&id=${vpn.id}"
        val response = executeGet(path)
        if (response.statusCode !in 200..299) {
            error("VPN switch request failed: ${response.statusCode} ${response.body}")
        }
        return response.body
    }

    fun getVpnList(): List<Vpn> {
        val response = executeGet("/cgi-bin/luci/;stok=$stok/api/xqsystem/vpn")
        if (response.statusCode !in 200..299) {
            error("VPN list request failed: ${response.statusCode} ${response.body}")
        }

        val body = response.body
        val listBody = Regex("\"list\"\\s*:\\s*\\[(.*?)]", RegexOption.DOT_MATCHES_ALL)
            .find(body)
            ?.groupValues
            ?.get(1)
            ?: error("VPN list field not found in response: $body")

        val idRegex = Regex("\"id\"\\s*:\\s*\"([^\"]+)\"")
        val titleRegex = Regex("\"oname\"\\s*:\\s*\"([^\"]+)\"")

        return Regex("\\{([^{}]*)\\}")
            .findAll(listBody)
            .mapNotNull { match ->
                val item = match.value
                val id = idRegex.find(item)?.groupValues?.get(1)
                val title = titleRegex.find(item)?.groupValues?.get(1)
                if (id != null && title != null) Vpn(title = title, id = id) else null
            }
            .toList()
    }

    fun getVpnStatus(): Boolean {
        val response = executeGet("/cgi-bin/luci/;stok=$stok/api/xqsystem/vpn_status")
        if (response.statusCode !in 200..299) {
            error("VPN status request failed: ${response.statusCode} ${response.body}")
        }

        val body = response.body
        val match = Regex("\"status\"\\s*:\\s*(\\d+)").find(body)
            ?: error("VPN status field not found in response: $body")

        return when (match.groupValues[1].toInt()) {
            0 -> true
            3 -> false
            else -> error("Unsupported VPN status value: ${match.groupValues[1]}. Body: $body")
        }
    }

    private fun loginAndGetStok(): String {
        val warmup = executeGet(WEB_LOGIN_PATH)
        val webPage = warmup.body

        val key = parseRouterKey(webPage)
        val deviceId = parseDeviceId(webPage)
            ?: cookieManager.cookieStore.cookies
                .firstOrNull { it.name.equals("deviceId", ignoreCase = true) }
                ?.value

        val nonce =
            "0_${deviceId}_${System.currentTimeMillis() / 1000}_${Random.nextInt(1000, 9999)}"
        val loginPassword = sha1(nonce + sha1(password + key))

        val body = form(
            "username" to user,
            "password" to loginPassword,
            "logtype" to "2",
            "nonce" to nonce
        )

        val response = executePost(LOGIN_API_PATH, body)
        val responseBody = response.body
        val directStok = parseStok(responseBody)
        if (directStok != null) {
            return directStok
        }

        val fallbackStok = resolveStokAfterLogin()
        if (fallbackStok != null) {
            return fallbackStok
        }

        error("Cannot login and get stok. status=${response.statusCode} body=$responseBody")
    }

    private fun parseRouterKey(webPage: String): String? {
        val patterns = listOf(
            Regex("""this\.key\s*=\s*['"]([^'"]+)['"]"""),
            Regex("""\bkey\b\s*:\s*['"]([^'"]+)['"]"""),
            Regex("""["']key["']\s*[:=]\s*["']([^"']+)["']"""),
            Regex("""key\s*=\s*['"]([^'"]+)['"]""")
        )
        return patterns.firstNotNullOfOrNull { it.find(webPage)?.groupValues?.get(1) }
    }

    private fun parseDeviceId(webPage: String): String? {
        val fromVar = Regex("""deviceId\s*=\s*['"]([^'"]+)['"]""").find(webPage)
        if (fromVar != null) {
            return fromVar.groupValues[1]
        }

        val fromMac = Regex("""["']mac["']\s*[:=]\s*["']([^"']+)["']""").find(webPage)
        if (fromMac != null) {
            return fromMac.groupValues[1]
        }

        return null
    }

    private fun form(vararg pairs: Pair<String, String>): String {
        return pairs.joinToString("&") { (k, v) ->
            "${urlEncode(k)}=${urlEncode(v)}"
        }
    }

    private fun urlEncode(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
    }

    private fun sha1(value: String): String {
        val digest =
            MessageDigest.getInstance("SHA-1").digest(value.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun parseStok(responseBody: String): String? {
        val tokenMatch = Regex("\"token\"\\s*:\\s*\"([^\"]+)\"").find(responseBody)
        if (tokenMatch != null) {
            return tokenMatch.groupValues[1]
        }

        val urlMatch = Regex(";stok=([a-zA-Z0-9]+)").find(responseBody)
        if (urlMatch != null) {
            return urlMatch.groupValues[1]
        }

        return null
    }

    private fun resolveStokAfterLogin(): String? {
        val paths = listOf(
            "/cgi-bin/luci/web/home",
            "/cgi-bin/luci/web",
            "/cgi-bin/luci/"
        )
        for (path in paths) {
            val response = executeGet(path)

            val location = response.headers["Location"].orEmpty()
            val fromLocation = parseStok(location)
            if (fromLocation != null) {
                return fromLocation
            }

            val fromBody = parseStok(response.body)
            if (fromBody != null) {
                return fromBody
            }
        }
        return null
    }

    private fun executeGet(path: String): HttpResult {
        val response = executeCall(api.get(path))
        return HttpResult(
            statusCode = response.code(),
            body = response.body()?.string().orEmpty(),
            headers = response.headers()
        )
    }

    private fun executePost(path: String, form: String): HttpResult {
        val requestBody = form.toRequestBody(FORM_MEDIA_TYPE)
        val response = executeCall(api.post(path, requestBody))
        return HttpResult(
            statusCode = response.code(),
            body = response.body()?.string().orEmpty(),
            headers = response.headers()
        )
    }

    private fun normalizeBaseUrl(raw: String): String {
        var result = raw
        if (!result.startsWith("http://") && !result.startsWith("https://")) {
            result = "http://$result"
        }
        if (!result.endsWith('/')) {
            result += '/'
        }
        return result
    }

    private fun <T> executeCall(call: Call<T>): Response<T> {
        return try {
            call.execute()
        } catch (_: ProtocolException) {
            // Some routers return malformed chunked responses intermittently.
            // Retry once with a fresh call instance.
            call.clone().execute()
        }
    }

    private interface RouterApi {
        @GET
        fun get(
            @Url path: String,
            @Header("Connection") connection: String = "close",
            @Header("Accept-Encoding") acceptEncoding: String = "identity"
        ): Call<ResponseBody>

        @POST
        fun post(
            @Url path: String,
            @Body body: RequestBody,
            @Header("Connection") connection: String = "close",
            @Header("Accept-Encoding") acceptEncoding: String = "identity",
            @Header("X-Requested-With") requestedWith: String = "XMLHttpRequest"
        ): Call<ResponseBody>
    }

    private data class HttpResult(
        val statusCode: Int,
        val body: String,
        val headers: Headers
    )
}
