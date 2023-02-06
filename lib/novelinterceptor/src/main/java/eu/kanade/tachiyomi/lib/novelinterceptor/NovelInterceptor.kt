package eu.kanade.tachiyomi.lib.novelinterceptor

import android.graphics.Bitmap
import android.net.Uri
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.claudemirovsky.noveltomanga.NovelToManga
import java.io.ByteArrayOutputStream

/**
 * This class intercepts urls made with [createUrl], gets the text page from
 * the url and returns a PNG image generated with the [NovelToManga] instance
 * that was passed as argument to the interceptor.
 */
class NovelInterceptor(private val noveltomanga: NovelToManga) : Interceptor {
    companion object {
        const val HOST = "noveltomanga"

        fun createUrl(page: CharSequence) = "http://noveltomanga/${Uri.encode(page.toString())}"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        if (url.host != HOST) return chain.proceed(request)
        val page = url.pathSegments[0]
        val bitmap = noveltomanga.drawPage(page)
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 0, stream)
        val responseBody = stream.toByteArray().toResponseBody("image/png".toMediaType())
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(responseBody)
            .build()
    }
}
