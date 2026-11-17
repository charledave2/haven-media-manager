package com.haven.media.cloud

/** Filled after the Haven app registration is approved in pCloud My Apps. */
object PCloudConfig {
    // Client ID is public app metadata; never commit a Client Secret or access token.
    const val CLIENT_ID = ""
    const val REDIRECT_URI = "pcloud-oauth://com.haven.media"
}
