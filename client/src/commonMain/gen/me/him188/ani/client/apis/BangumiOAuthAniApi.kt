/**
 *
 * Please note:
 * This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * Do not edit this file manually.
 *
 */

@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport",
)

package me.him188.ani.client.apis

import me.him188.ani.client.models.AniAnonymousBangumiUserToken
import me.him188.ani.client.models.AniBangumiLoginRequest
import me.him188.ani.client.models.AniBangumiLoginResponse
import me.him188.ani.client.models.AniBangumiUserToken
import me.him188.ani.client.models.AniRefreshBangumiTokenRequest

import me.him188.ani.client.infrastructure.*
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.request.forms.formData
import io.ktor.client.engine.HttpClientEngine
import kotlinx.serialization.json.Json
import io.ktor.http.ParametersBuilder
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

open class BangumiOAuthAniApi : ApiClient {

    constructor(
        baseUrl: String = ApiClient.BASE_URL,
        httpClientEngine: HttpClientEngine? = null,
        httpClientConfig: ((HttpClientConfig<*>) -> Unit)? = null,
        jsonSerializer: Json = ApiClient.JSON_DEFAULT
    ) : super(
        baseUrl = baseUrl,
        httpClientEngine = httpClientEngine,
        httpClientConfig = httpClientConfig,
        jsonBlock = jsonSerializer,
    )

    constructor(
        baseUrl: String,
        httpClient: HttpClient
    ) : super(baseUrl = baseUrl, httpClient = httpClient)

    /**
     * 使用 Bangumi token 登录
     * 使用 Bangumi token 登录并获取用户会话 token。
     * @param aniBangumiLoginRequest Bangumi token 字符串以及客户端版本与平台架构信息。 clientOS参数可选值：&#x60;windows, macos, android, ios, linux, debian, ubuntu, redhat&#x60;；clientArch参数可选值：&#x60;aarch64, x86, x86_64&#x60;。 (optional)
     * @return AniBangumiLoginResponse
     */
    @Suppress("UNCHECKED_CAST")
    open suspend fun bangumiLogin(aniBangumiLoginRequest: AniBangumiLoginRequest? = null): HttpResponse<AniBangumiLoginResponse> {

        val localVariableAuthNames = listOf<String>()

        val localVariableBody = aniBangumiLoginRequest

        val localVariableQuery = mutableMapOf<String, List<String>>()
        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.POST,
            "/v1/login/bangumi",
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = false,
        )

        return jsonRequest(
            localVariableConfig,
            localVariableBody,
            localVariableAuthNames,
        ).wrap()
    }



    /**
     * Bangumi OAuth 回调
     * 用于 Bangumi OAuth 授权回调，用户不应自行调用该接口。
     * @param code Bangumi OAuth 授权码
     * @param state 获取 OAuth 链接时提供的请求 ID
     * @return void
     */
    open suspend fun bangumiOauthCallback(code: kotlin.String, state: kotlin.String): HttpResponse<Unit> {

        val localVariableAuthNames = listOf<String>()

        val localVariableBody = 
            io.ktor.client.utils.EmptyContent

        val localVariableQuery = mutableMapOf<String, List<String>>()
        code?.apply { localVariableQuery["code"] = listOf("$code") }
        state?.apply { localVariableQuery["state"] = listOf("$state") }
        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.GET,
            "/v1/login/bangumi/oauth/callback",
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = false,
        )

        return request(
            localVariableConfig,
            localVariableBody,
            localVariableAuthNames,
        ).wrap()
    }


    /**
     * 获取 Bangumi OAuth 授权链接
     * 获取 Bangumi OAuth 授权链接，用于获取 Bangumi token。
     * @param requestId 唯一请求 ID，建议使用随机生成的 UUID
     * @return void
     */
    open suspend fun getBangumiOauthUrl(requestId: kotlin.String): HttpResponse<Unit> {

        val localVariableAuthNames = listOf<String>()

        val localVariableBody = 
            io.ktor.client.utils.EmptyContent

        val localVariableQuery = mutableMapOf<String, List<String>>()
        requestId?.apply { localVariableQuery["requestId"] = listOf("$requestId") }
        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.GET,
            "/v1/login/bangumi/oauth",
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = false,
        )

        return request(
            localVariableConfig,
            localVariableBody,
            localVariableAuthNames,
        ).wrap()
    }


    /**
     * 获取 Bangumi token
     * 获取 Bangumi token，用于登录。
     * @param requestId 获取 OAuth 链接时提供的请求 ID
     * @return AniBangumiUserToken
     */
    @Suppress("UNCHECKED_CAST")
    open suspend fun getBangumiToken(requestId: kotlin.String): HttpResponse<AniBangumiUserToken> {

        val localVariableAuthNames = listOf<String>()

        val localVariableBody = 
            io.ktor.client.utils.EmptyContent

        val localVariableQuery = mutableMapOf<String, List<String>>()
        requestId?.apply { localVariableQuery["requestId"] = listOf("$requestId") }
        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.GET,
            "/v1/login/bangumi/oauth/token",
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = false,
        )

        return request(
            localVariableConfig,
            localVariableBody,
            localVariableAuthNames,
        ).wrap()
    }


    /**
     * 刷新 Bangumi token
     * 刷新 Bangumi token。
     * @param aniRefreshBangumiTokenRequest 上次登录时提供的刷新 token (optional)
     * @return AniAnonymousBangumiUserToken
     */
    @Suppress("UNCHECKED_CAST")
    open suspend fun refreshBangumiToken(aniRefreshBangumiTokenRequest: AniRefreshBangumiTokenRequest? = null): HttpResponse<AniAnonymousBangumiUserToken> {

        val localVariableAuthNames = listOf<String>()

        val localVariableBody = aniRefreshBangumiTokenRequest

        val localVariableQuery = mutableMapOf<String, List<String>>()
        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.POST,
            "/v1/login/bangumi/oauth/refresh",
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = false,
        )

        return jsonRequest(
            localVariableConfig,
            localVariableBody,
            localVariableAuthNames,
        ).wrap()
    }


}
