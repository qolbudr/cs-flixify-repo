
import com.beust.klaxon.Klaxon
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addQuality
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.toRatingInt
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class MovieProvider : MainAPI() {
    override var mainUrl = "https://45.87.41.16"
    override var name = "Flixify Movie"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override var lang = "id"
    override val hasMainPage = true

    override val mainPage: List<MainPageData> = mainPageOf(
        "$mainUrl/movies/" to "Featured",
        "$mainUrl/movies/" to "Movies",
        "$mainUrl/tvshows/" to "TV Shows",
    )

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.selectFirst(".data a")?.text()?.trim() ?: ""
        val href = this.selectFirst(".data a")?.attr("href") ?: ""
        val posterUrl = this.selectFirst(".poster img")?.attr("data-src")
        val quality = this.selectFirst(".quality")?.text() ?: "CAM"

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            addQuality(quality)
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        if(request.name == "Featured") {
            val document = app.get(request.data).document
            val home = ArrayList<SearchResponse>()

            document.select(".featured .item.movies").mapNotNull {
                home.add(it.toSearchResult())
            }

            return newHomePageResponse(request.name, home.toList(), hasNext = false)
        } else {
            val document = app.get(request.data + "page/$page/").document
            val home = ArrayList<SearchResponse>()

            document.select("#archive-content .item.movies").mapNotNull {
                home.add(it.toSearchResult())
            }

            return newHomePageResponse(request.name, home.toList(), hasNext = true)
        }
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        val document = app.get("$mainUrl/?s=$query").document
        val result = ArrayList<SearchResponse>()

        document.select(".search-page .result-item").mapNotNull {
            val title = it.selectFirst(".title a")?.text() ?: ""
            val href = it.selectFirst(".title a")?.attr("href") ?: ""
            val posterUrl = it.selectFirst(".thumbnail img")?.attr("src") ?: ""
            val type = if(href.contains("tvshows")) {
                TvType.TvSeries
            } else {
                TvType.Movie
            }

            result.add(
                newMovieSearchResponse(
                    title,
                    href,
                    type,
                ) {
                    this.posterUrl = posterUrl
                }
            )
        }

        return result
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst(".sheader .data h1")?.text() ?: ""
        val year = document.selectFirst(".sheader .extra .date")?.text()?.split(",")?.last() ?: ""
        val poster = document.selectFirst(".shreader .poster img")?.attr("src") ?: ""
        val genres = document.select(".sgeneros a").mapNotNull {
            it.text()
        }

        val actors = document.select(".person").mapNotNull {
            ActorData(
                Actor(it.selectFirst(".name a")?.text() ?: "", it.selectFirst("img")?.attr("src") ?: ""),
                roleString = it.selectFirst(".caracter")?.text() ?: ""
            )
        }

        val plot = document.selectFirst(".sbox .wp-content")?.text()?.trim()

        if(url.contains("tvshows")) {
            val episodes = ArrayList<Episode>();
            document.select(".se-c").mapNotNull {
                var season = 1;
                it.select(".episodios li").mapNotNull {eps ->
                    var epsNumber = 1;
                    episodes.add(
                        Episode(
                            eps.selectFirst(".episodiotitle a")?.attr("href") ?: "",
                            eps.selectFirst(".episodiotitle a")?.text(),
                            season,
                            epsNumber,
                        )
                    )
                    epsNumber++;
                }
                season++;
            }

            return newTvSeriesLoadResponse(
                title,
                url,
                TvType.TvSeries,
                episodes = episodes
            ) {
                this.posterUrl = poster
                this.backgroundPosterUrl = poster
                this.year = year.toIntOrNull()
                this.plot = plot
                this.tags = genres
                this.actors = actors
            }
        } else {
            return newMovieLoadResponse(
                title,
                url,
                TvType.Movie,
                url,
            ) {
                this.posterUrl = poster
                this.backgroundPosterUrl = poster
                this.year = year.toIntOrNull()
                this.plot = plot
                this.tags = genres
                this.actors = actors
            }
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
//        val document = app.get(data).document
//        val title = document.selectFirst(".entry-title")?.text() ?: ""
//        val year = document.selectFirst("#w-info > div.info > div.detail > div:nth-child(3) > span > a")?.text() ?: ""
//        val tmdbId = getTmdbId(query = title, year = year);
//        val append = "alternative_titles,credits,external_ids,keywords,videos,recommendations"
//        val urlFetch = "$tmdbURL/movie/${tmdbId}?api_key=$apiTmdb&append_to_response=$append"
//
//        val result = app.get(urlFetch).parsedSafe<MediaDetail>()
//            ?: throw ErrorLoadingException("Invalid Json Response")
//
//        val link = "https://www.2embed.cc/embed/${result.externalIds?.imdbId}"

//        loadExtractor(link, mainUrl, subtitleCallback, callback)
        return true
    }
}