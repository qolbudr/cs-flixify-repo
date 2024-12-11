
import com.beust.klaxon.Klaxon
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
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
import com.lagradost.cloudstream3.toRatingInt
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class MovieProvider : MainAPI() {
    override var mainUrl = "https://bflixhd.lol"
    override var name = "Flixify Movie"
    override val supportedTypes = setOf(TvType.Movie)
    override var lang = "en"
    override val hasMainPage = true

    val tmdbURL = "https://api.themoviedb.org/3"
    val apiTmdb = "cad7722e1ca44bd5f1ea46b59c8d54c8"

    data class TmdbSearchResponse(val results: List<TmdbBody>?)
    data class TmdbBody(val id: String?, val first_air_date: String?, val release_date: String?)

    override val mainPage: List<MainPageData> = mainPageOf(
        "$mainUrl/category/movies/" to "Latest Movie",
        "$mainUrl/category/action/" to "Action",
        "$mainUrl/category/comedy/" to "Comedy",
        "$mainUrl/category/animation/" to "Animation",
        "$mainUrl/category/romance/" to "Romance",
        "$mainUrl/category/drama/" to "Drama",
    )

    /* CUSTOM FUNCTION */

    private suspend fun getTmdbId(query: String, year: String): String? {
        val result = app.get("$tmdbURL/search/movie?query=$query&api_key=$apiTmdb").text
        val data = parseJson<TmdbSearchResponse>(result)

        return data.results?.firstOrNull {it.release_date?.contains(year) ?: false}?.id;
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.selectFirst(".meta a")?.text()?.trim() ?: ""
        val href = this.selectFirst(".meta a")?.attr("href") ?: ""
        val posterUrl = this.selectFirst(".poster img")?.attr("data-src")
        val quality = this.selectFirst(".quality")?.text() ?: "CAM"

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            addQuality(quality)
        }
    }

    /* CUSTOM FUNCTION */

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val document = app.get(request.data + "/page/$page/").document
        val home = ArrayList<SearchResponse>()

        document.select(".filmlist .item").mapNotNull {
            val title = it.selectFirst(".meta a")?.text() ?: ""
            val year = it.selectFirst(".meta div span")?.text() ?: ""

            val tmdbId = getTmdbId(query = title, year = year)
            if (tmdbId != null) {
                home.add(it.toSearchResult())
            }
        }

        return newHomePageResponse(request.name, home.toList(), hasNext = true);
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        val document = app.get("$mainUrl/?s=$query").document
        val result = ArrayList<SearchResponse>()

        document.select(".filmlist .item").mapNotNull {
            val title = it.selectFirst(".meta a")?.text() ?: ""
            val year = it.selectFirst(".meta div span")?.text() ?: ""

            val tmdbId = getTmdbId(query = title, year = year)
            if (tmdbId != null) {
                result.add(it.toSearchResult())
            }
        }

        return result
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst(".entry-title")?.text() ?: ""
        val year = document.selectFirst("#w-info > div.info > div.detail > div:nth-child(3) > span > a")?.text() ?: ""
        val tmdbId = getTmdbId(query = title, year = year);
        val append = "alternative_titles,credits,external_ids,keywords,videos,recommendations"
        val urlFetch = "$tmdbURL/movie/${tmdbId}?api_key=$apiTmdb&append_to_response=$append"

        val res = app.get(urlFetch).parsedSafe<MediaDetail>()
            ?: throw ErrorLoadingException("Invalid Json Response")

        val resTitle = res.title ?: res.name ?: return null
        val poster = getOriImageUrl(res.posterPath)
        val bgPoster = getOriImageUrl(res.backdropPath)
        val rating = res.vote_average.toString().toRatingInt()
        val genres = res.genres?.mapNotNull { it.name }
        val actors = res.credits?.cast?.mapNotNull { cast ->
            ActorData(
                Actor(cast.name ?: cast.originalName
            ?: return@mapNotNull null, getImageUrl(cast.profilePath)), roleString = cast.character)
        } ?: return null

        val trailer = res.videos?.results?.map { "https://www.youtube.com/watch?v=${it.key}" }?.randomOrNull()

        return newMovieLoadResponse(
                resTitle,
                url,
                TvType.Movie,
                url,
            ) {
                this.posterUrl = poster
                this.backgroundPosterUrl = bgPoster
                this.year = year.toIntOrNull()
                this.plot = res.overview
                this.duration = res.runtime
                this.tags = genres
                this.rating = rating
                this.recommendations = recommendations
                this.actors = actors
                addTrailer(trailer)
            }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = app.get(data).document
        val title = document.selectFirst(".entry-title")?.text() ?: ""
        val year = document.selectFirst("#w-info > div.info > div.detail > div:nth-child(3) > span > a")?.text() ?: ""
        val tmdbId = getTmdbId(query = title, year = year);
        val append = "alternative_titles,credits,external_ids,keywords,videos,recommendations"
        val urlFetch = "$tmdbURL/movie/${tmdbId}?api_key=$apiTmdb&append_to_response=$append"

        val result = app.get(urlFetch).parsedSafe<MediaDetail>()
            ?: throw ErrorLoadingException("Invalid Json Response")

        val link = "https://www.2embed.cc/embed/${result.externalIds?.imdbId}"

        loadExtractor("https://ngr2dnwrdasv7gvg.premilkyway.com/hls2/01/06467/o92z9w0kzlpw_l/master.m3u8?t=mDiksjZdT795X-kRAz8ixJvw9gSp4O_yec66RRZkJKk&s=1733888614&e=129600&f=32471809&srv=zukkcg9r2ba2&i=0.0&sp=500&p1=zukkcg9r2ba2&p2=zukkcg9r2ba2&asn=17451", mainUrl, subtitleCallback, callback)
        return true
    }
}


open class FlixifyEmbedApi : ExtractorApi() {
    override val name = "2Embed"
    override val mainUrl = "https://www.2embed.cc"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val document = app.get(url).document
        val iframe = document.select("iframe").attr("data-src")
        val id = iframe.split("id=").last()

        val sourceDoc = app.get("https://uqloads.xyz/e/$id", headers = mapOf(
            "Referer" to "https://streamsrcs.2embed.cc/"
        )).document

        sourceDoc.select("script").map {
            it.data()
        } .filter {
            it.contains("eval(function(p,a,c,k,e,d)")
        } .map {script ->
            val data = getAndUnpack(script)
            val link = Regex("file:['\\\"]https(.*)['\\\"]").find(data)?.groupValues?.getOrNull(1)
                ?: return@map null

            val returnLink = link.replace("file:\"", "").replace("\"", "")
            callback.invoke(
                ExtractorLink(
                    this.name,
                    this.name,
                    returnLink,
                    "",
                    Qualities.Unknown.value
                )
            )
        }
    }
}


data class MediaDetail(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("poster_path") val posterPath: String? = null,
    @JsonProperty("backdrop_path") val backdropPath: String? = null,
    @JsonProperty("overview") val overview: String? = null,
    @JsonProperty("runtime") val runtime: Int? = null,
    @JsonProperty("vote_average") val vote_average: Any? = null,
    @JsonProperty("genres") val genres: ArrayList<Genres>? = arrayListOf(),
    @JsonProperty("seasons") val seasons: ArrayList<Seasons>? = arrayListOf(),
    @JsonProperty("videos") val videos: ResultsTrailer? = null,
    @JsonProperty("credits") val credits: Credits? = null,
    @JsonProperty("external_ids") val externalIds: ExternalIds? = null,
    @JsonProperty("last_episode_to_air") val last_episode_to_air: LastEpisodeToAir? = null,
)

data class ExternalIds(
    @JsonProperty("imdb_id") val imdbId: String? = null,
)

data class Trailers(
    @JsonProperty("key") val key: String? = null,
)

data class ResultsTrailer(
    @JsonProperty("results") val results: ArrayList<Trailers>? = arrayListOf(),
)

data class AltTitles(
    @JsonProperty("iso_3166_1") val iso_3166_1: String? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("type") val type: String? = null,
)

data class Credits(
    @JsonProperty("cast") val cast: ArrayList<Cast>? = arrayListOf(),
)

data class Cast(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("original_name") val originalName: String? = null,
    @JsonProperty("character") val character: String? = null,
    @JsonProperty("known_for_department") val knownForDepartment: String? = null,
    @JsonProperty("profile_path") val profilePath: String? = null,
)

data class Genres(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("name") val name: String? = null,
)

data class Seasons(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("season_number") val seasonNumber: Int? = null,
    @JsonProperty("air_date") val airDate: String? = null,
)

data class Episodes(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("overview") val overview: String? = null,
    @JsonProperty("air_date") val airDate: String? = null,
    @JsonProperty("still_path") val stillPath: String? = null,
    @JsonProperty("vote_average") val voteAverage: Double? = null,
    @JsonProperty("episode_number") val episodeNumber: Int? = null,
    @JsonProperty("season_number") val seasonNumber: Int? = null,
)

data class MediaDetailEpisodes(
    @JsonProperty("episodes") val episodes: ArrayList<Episodes>? = arrayListOf(),
)

private fun getOriImageUrl(link: String?): String? {
    if (link == null) return null
    return if (link.startsWith("/")) "https://image.tmdb.org/t/p/original/$link" else link
}

private fun getImageUrl(link: String?): String? {
    if (link == null) return null
    return if (link.startsWith("/")) "https://image.tmdb.org/t/p/w500/$link" else link
}

data class LastEpisodeToAir(
    @JsonProperty("episode_number") val episode_number: Int? = null,
    @JsonProperty("season_number") val season_number: Int? = null,
)

data class StreamData(
    @JsonProperty("extra2") val extra2: String? = null,
)