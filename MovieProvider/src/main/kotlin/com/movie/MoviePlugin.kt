package com.movie

import FlixifyEmbedApi
import MovieProvider
import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class MoviePlugin: Plugin() {
    override fun load(context: Context) {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(MovieProvider())
        registerExtractorAPI(FlixifyEmbedApi())
    }
}