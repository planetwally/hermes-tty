package com.planetwally.hermestty

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit

class ApiError(val status: Int, message: String) : IOException(message)

data class SessionInfo(
    val id: String,
    val title: String?,
    val source: String?,
    val model: String?,
    val messageCount: Int,
    val lastActive: Double,
    val preview: String?,
) {
    val label: String get() = title ?: preview?.lineSequence()?.firstOrNull() ?: id

    companion object {
        fun from(o: JSONObject) = SessionInfo(
            id = o.getString("id"),
            title = o.str("title"),
            source = o.str("source"),
            model = o.str("model"),
            messageCount = o.optInt("message_count", 0),
            lastActive = o.num("last_active") ?: o.num("started_at") ?: 0.0,
            preview = o.str("preview"),
        )
    }
}

fun JSONObject.str(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

fun JSONObject.num(key: String): Double? =
    if (isNull(key)) null else optDouble(key).takeUnless { it.isNaN() }

fun normalizeBaseUrl(raw: String): String {
    var u = raw.trim().trimEnd('/')
    if (u.isNotEmpty() && !u.contains("://")) u = "http://$u"
    return u.removeSuffix("/v1").trimEnd('/')
}

/** Accumulates `data:` lines of a server-sent event stream into JSON events. */
class SseParser {
    private val data = StringBuilder()

    /** Feeds one line; returns an event when a blank line completes one holding valid JSON. */
    fun feed(line: String): JSONObject? {
        when {
            line.isEmpty() -> if (data.isNotEmpty()) {
                val event = runCatching { JSONObject(data.toString()) }.getOrNull()
                data.clear()
                return event
            }
            line.startsWith(":") -> Unit // keepalive / comment
            line.startsWith("data:") -> {
                if (data.isNotEmpty()) data.append('\n')
                data.append(line.removePrefix("data:").trimStart())
            }
        }
        return null
    }
}

/**
 * Thin client for the hermes-agent gateway API server (`API_SERVER_ENABLED=true`).
 *
 * Turns go through the Runs API rather than `/api/sessions/{id}/chat/stream`: a dropped
 * session stream interrupts the agent, while a run keeps executing server-side and stays
 * pollable at `GET /v1/runs/{id}` — which is what a phone that sleeps and roams needs.
 */
class HermesApi(baseUrl: String, private val key: String) {
    val base = normalizeBaseUrl(baseUrl)

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // The server sends a keepalive comment every 10s, so a silent minute means the link is dead.
    private val streamClient = client.newBuilder().readTimeout(45, TimeUnit.SECONDS).build()

    private val jsonType = "application/json".toMediaType()

    private fun req(path: String) = Request.Builder().url(base + path).header("Authorization", "Bearer $key")

    private fun JSONObject.body() = toString().toRequestBody(jsonType)

    private suspend fun call(r: Request): JSONObject = withContext(Dispatchers.IO) {
        client.newCall(r).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw ApiError(resp.code, errorMessage(resp.code, text))
            if (text.isBlank()) JSONObject() else JSONObject(text)
        }
    }

    private suspend fun get(path: String) = call(req(path).get().build())

    private suspend fun post(path: String, body: JSONObject = JSONObject()) = call(req(path).post(body.body()).build())

    suspend fun health(): JSONObject = call(Request.Builder().url("$base/health").get().build())

    suspend fun capabilities(): JSONObject = get("/v1/capabilities")

    suspend fun sessions(limit: Int = 80): List<SessionInfo> {
        val arr = get("/api/sessions?limit=$limit").optJSONArray("data") ?: JSONArray()
        return (0 until arr.length()).map { SessionInfo.from(arr.getJSONObject(it)) }
    }

    suspend fun session(id: String): SessionInfo {
        val o = get("/api/sessions/${enc(id)}")
        return SessionInfo.from(o.optJSONObject("session") ?: o)
    }

    suspend fun createSession(): SessionInfo {
        val o = post("/api/sessions")
        return SessionInfo.from(o.optJSONObject("session") ?: o)
    }

    suspend fun renameSession(id: String, title: String) {
        call(req("/api/sessions/${enc(id)}").patch(JSONObject().put("title", title).body()).build())
    }

    suspend fun messages(id: String): List<JSONObject> {
        val arr = get("/api/sessions/${enc(id)}/messages").optJSONArray("data") ?: JSONArray()
        return (0 until arr.length()).map { arr.getJSONObject(it) }
    }

    /** Starts one agent turn in [sessionId]; the idempotency key makes a retried POST safe. */
    suspend fun startRun(sessionId: String, input: String): String {
        val idem = UUID.randomUUID().toString()
        val body = JSONObject().put("input", input).put("session_id", sessionId)
        var attempt = 0
        while (true) {
            try {
                return call(req("/v1/runs").header("Idempotency-Key", idem).post(body.body()).build())
                    .getString("run_id")
            } catch (e: ApiError) {
                throw e
            } catch (e: IOException) {
                if (++attempt >= 3) throw e
            }
        }
    }

    suspend fun run(runId: String): JSONObject = get("/v1/runs/$runId")

    suspend fun stop(runId: String) = post("/v1/runs/$runId/stop")

    suspend fun steer(runId: String, text: String) = post("/v1/runs/$runId/steer", JSONObject().put("input", text))

    suspend fun approve(runId: String, choice: String) =
        post("/v1/runs/$runId/approval", JSONObject().put("choice", choice))

    /** Server-sent events for a run. Completes when the server closes the stream. */
    fun events(runId: String): Flow<JSONObject> = flow {
        val call = streamClient.newCall(
            req("/v1/runs/$runId/events").header("Accept", "text/event-stream").get().build()
        )
        val handle = currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) throw ApiError(resp.code, errorMessage(resp.code, resp.body?.string().orEmpty()))
                val source = resp.body!!.source()
                val parser = SseParser()
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    parser.feed(line)?.let { emit(it) }
                }
            }
        } finally {
            handle?.dispose()
        }
    }.flowOn(Dispatchers.IO)

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun errorMessage(code: Int, body: String): String {
        if (code == 401) return "401 unauthorized — check the API key"
        val detail = runCatching {
            val o = JSONObject(body)
            when (val e = o.opt("error")) {
                is JSONObject -> e.optString("message")
                is String -> e
                else -> o.optString("message")
            }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: body.take(200)
        return "HTTP $code: $detail"
    }
}
