package com.juying.app

import android.app.Application
import coil.Coil
import coil.ImageLoader
import com.juying.app.source.SourceLogManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class JuyingApp : Application() {

    override fun onCreate() {
        super.onCreate()
        SourceLogManager.init(this)
        setupCoilImageLoader()
    }

    private fun setupCoilImageLoader() {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(Interceptor { chain ->
                val originalRequest = chain.request()
                var urlStr = originalRequest.url.toString()
                val requestBuilder = originalRequest.newBuilder()

                // Default Browser UA to bypass CDN restrictions
                requestBuilder.header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                )

                // Parse @Referer= or @Headers= in image URL if present
                if (urlStr.contains("@Referer=")) {
                    val refererVal = urlStr.substringAfter("@Referer=").substringBefore("@")
                    if (refererVal.isNotBlank()) {
                        requestBuilder.header("Referer", refererVal)
                    }
                } else {
                    // Default Referer based on host for hotlink protection
                    val host = originalRequest.url.host
                    when {
                        host.contains("doubanio") -> requestBuilder.header("Referer", "https://movie.douban.com/")
                        host.contains("meituan") -> requestBuilder.header("Referer", "https://www.meituan.com/")
                        host.contains("bilibili") || host.contains("hdslb") -> requestBuilder.header("Referer", "https://www.bilibili.com/")
                        host.contains("baidu") -> requestBuilder.header("Referer", "https://www.baidu.com/")
                        else -> requestBuilder.header("Referer", "https://${host}/")
                    }
                }

                chain.proceed(requestBuilder.build())
            })
            .build()

        val imageLoader = ImageLoader
            .Builder(this@JuyingApp)
            .okHttpClient(okHttpClient = okHttpClient)
            .crossfade(true)
            .build()

        Coil.setImageLoader(imageLoader)
    }
}
