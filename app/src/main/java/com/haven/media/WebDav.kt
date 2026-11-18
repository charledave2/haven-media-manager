package com.haven.media

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

internal enum class WebDavRegion(val label: String, val baseUrl: String) {
    US("United States", "https://webdav.pcloud.com/"),
    EU("Europe", "https://ewebdav.pcloud.com/")
}

internal data class WebDavAccount(
    val region: WebDavRegion,
    val email: String,
    val password: String
) {
    val authorization: String
        get() = "Basic " + Base64.encodeToString("$email:$password".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
}

internal data class WebDavEntry(
    val name: String,
    val href: String,
    val isFolder: Boolean,
    val size: Long,
    val mimeType: String?,
    val modified: String?
)

internal class WebDavException(message: String) : Exception(message)

internal object WebDavVault {
    private const val PREFS = "haven_webdav_secure"
    private const val KEY = "account_blob"
    private const val KEY_ALIAS = "haven_webdav_aes_v1"

    fun save(context: Context, account: WebDavAccount) {
        val payload = listOf(account.region.name, account.email, account.password).joinToString("\n").toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(payload)
        val combined = cipher.iv + encrypted
        val value = Base64.encodeToString(combined, Base64.NO_WRAP)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, value).apply()
    }

    fun load(context: Context): WebDavAccount? = runCatching {
        val value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return null
        val combined = Base64.decode(value, Base64.NO_WRAP)
        require(combined.size > 12)
        val iv = combined.copyOfRange(0, 12)
        val ciphertext = combined.copyOfRange(12, combined.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        val parts = cipher.doFinal(ciphertext).toString(Charsets.UTF_8).split("\n", limit = 3)
        val region = WebDavRegion.valueOf(parts[0])
        WebDavAccount(region, parts[1], parts[2])
    }.getOrNull()

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}

internal object WebDavClient {
    private val propfindBody = """<?xml version="1.0" encoding="utf-8"?>
<d:propfind xmlns:d="DAV:"><d:prop><d:displayname/><d:resourcetype/><d:getcontentlength/><d:getcontenttype/><d:getlastmodified/></d:prop></d:propfind>""".toByteArray(Charsets.UTF_8)

    suspend fun listFolder(account: WebDavAccount, href: String = "/"): List<WebDavEntry> = withContext(Dispatchers.IO) {
        val target = resolveUrl(account, href)
        val connection = (target.openConnection() as HttpURLConnection).apply {
            requestMethod = "PROPFIND"
            connectTimeout = 15_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Authorization", account.authorization)
            setRequestProperty("Depth", "1")
            setRequestProperty("Content-Type", "application/xml; charset=utf-8")
            setRequestProperty("Accept", "application/xml")
        }
        try {
            connection.outputStream.use { it.write(propfindBody) }
            when (val code = connection.responseCode) {
                207, 200 -> parseEntries(connection.inputStream, target)
                401, 403 -> throw WebDavException("pCloud did not accept this login. Check your email and password, and approve any new-login email from pCloud.")
                else -> throw WebDavException("pCloud WebDAV returned HTTP $code.")
            }
        } finally {
            connection.disconnect()
        }
    }

    fun fileUrl(account: WebDavAccount, href: String): String = resolveUrl(account, href).toString()

    private fun resolveUrl(account: WebDavAccount, href: String): URL {
        val base = URI(account.region.baseUrl)
        val safeHref = if (href.startsWith("http://") || href.startsWith("https://")) href else href
        val target = base.resolve(safeHref).toURL()
        require(target.protocol == "https" && target.host == base.host) { "Unexpected WebDAV host." }
        return target
    }

    private fun parseEntries(input: java.io.InputStream, target: URL): List<WebDavEntry> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
        }
        val document = input.use { factory.newDocumentBuilder().parse(it) }
        val responses = document.getElementsByTagNameNS("*", "response")
        val targetPath = target.path.trimEnd('/').ifEmpty { "/" }
        buildList {
            for (i in 0 until responses.length) {
                val response = responses.item(i) as? Element ?: continue
                val hrefRaw = response.firstText("href") ?: continue
                val hrefUrl = runCatching { URI(target.toURI()).resolve(hrefRaw).toURL() }.getOrNull() ?: continue
                if (hrefUrl.host != target.host) continue
                val path = hrefUrl.path
                if (path.trimEnd('/').ifEmpty { "/" } == targetPath) continue
                val prop = response.getElementsByTagNameNS("*", "prop").item(0) as? Element ?: continue
                val name = prop.firstText("displayname")
                    ?.takeIf { it.isNotBlank() }
                    ?: Uri.decode(path.trimEnd('/').substringAfterLast('/')).ifBlank { "File" }
                val resourceType = prop.getElementsByTagNameNS("*", "resourcetype").item(0) as? Element
                val folder = resourceType?.getElementsByTagNameNS("*", "collection")?.length?.let { it > 0 } == true
                val length = prop.firstText("getcontentlength")?.toLongOrNull() ?: 0L
                add(WebDavEntry(name, hrefRaw, folder, length, prop.firstText("getcontenttype"), prop.firstText("getlastmodified")))
            }
        }.sortedWith(compareByDescending<WebDavEntry> { it.isFolder }.thenBy { it.name.lowercase() })
    }

    private fun Element.firstText(localName: String): String? =
        getElementsByTagNameNS("*", localName).item(0)?.textContent?.trim()
}
