package com.kudcrafts.widgets.board

import com.kudcrafts.widgets.core.FetchResult
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** board's API as this device's CLI token (`Authorization: Bearer bct_…`). */
class BoardApi(private val http: OkHttpClient, private val base: String = BASE) {
    companion object {
        const val BASE = "https://board.kudcrafts.com"
        private val JSON = "application/json".toMediaType()
    }

    fun overview(token: String, preset: String = "today"): FetchResult<BoardOverview> = try {
        val req = Request.Builder()
            .url("$base/api/board/overview?lite=1&preset=$preset")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .build()
        http.newCall(req).execute().use { res ->
            FetchResult.forStatus(res.code) ?: FetchResult.Ok(BoardOverview.parse(res.body!!.string()))
        }
    } catch (e: IOException) {
        FetchResult.Error(e.message ?: "network error")
    } catch (e: kotlinx.serialization.SerializationException) {
        FetchResult.Error("unexpected response")
    }

    sealed interface Start {
        data class Ok(val code: DeviceFlow.Code) : Start
        data class Error(val message: String) : Start
    }

    fun startDevice(deviceName: String): Start = try {
        val req = Request.Builder().url("$base/api/board/device/code")
            .post(DeviceFlow.requestBody(deviceName).toRequestBody(JSON)).build()
        http.newCall(req).execute().use { res ->
            val body = res.body?.string().orEmpty()
            if (res.isSuccessful) Start.Ok(DeviceFlow.json.decodeFromString(DeviceFlow.Code.serializer(), body))
            else Start.Error((DeviceFlow.parsePoll(res.code, body) as? DeviceFlow.Poll.Failed)?.message ?: "Couldn't start the sign-in.")
        }
    } catch (e: IOException) {
        Start.Error("Can't reach board.")
    } catch (e: kotlinx.serialization.SerializationException) {
        Start.Error("Unexpected response from board.")
    }

    fun poll(deviceCode: String): DeviceFlow.Poll = try {
        val req = Request.Builder().url("$base/api/board/device/token")
            .post(DeviceFlow.pollBody(deviceCode).toRequestBody(JSON)).build()
        http.newCall(req).execute().use { res -> DeviceFlow.parsePoll(res.code, res.body?.string().orEmpty()) }
    } catch (e: IOException) {
        // A dropped connection mid-poll isn't a verdict: keep polling.
        DeviceFlow.Poll.Pending
    }

    /** `board logout`: revoke the calling token. True when board accepted it. */
    fun logout(token: String): Boolean = try {
        val req = Request.Builder().url("$base/api/board/cli/logout")
            .header("Authorization", "Bearer $token")
            .post(ByteArray(0).toRequestBody(null)).build()
        http.newCall(req).execute().use { it.isSuccessful }
    } catch (e: IOException) {
        false
    }
}
