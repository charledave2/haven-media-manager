package com.haven.media.cloud

import com.pcloud.sdk.ApiClient
import com.pcloud.sdk.Authenticators
import com.pcloud.sdk.PCloudSdk
import com.pcloud.sdk.RemoteEntry
import com.pcloud.sdk.RemoteFolder

/** Authenticated pCloud API client and asynchronous folder access. */
class PCloudConnection private constructor(private val client: ApiClient) {
    fun listFolder(folderId: Long, onResult: (Result<List<RemoteEntry>>) -> Unit) {
        client.listFolder(folderId).enqueue(object : com.pcloud.sdk.Callback<RemoteFolder> {
            override fun onResponse(call: com.pcloud.sdk.Call<RemoteFolder>, response: RemoteFolder) {
                onResult(Result.success(response.children()))
            }

            override fun onFailure(call: com.pcloud.sdk.Call<RemoteFolder>, throwable: Throwable) {
                onResult(Result.failure(throwable))
            }
        })
    }

    companion object {
        /** apiHost is returned by OAuth and identifies the account's US/EU data center. */
        fun fromAccessToken(accessToken: String, apiHost: String): PCloudConnection {
            val client = PCloudSdk.newClientBuilder()
                .apiHost(apiHost)
                .authenticator(Authenticators.newOAuthAuthenticator(accessToken))
                .create()
            return PCloudConnection(client)
        }
    }
}
