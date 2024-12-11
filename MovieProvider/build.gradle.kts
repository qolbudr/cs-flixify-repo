// use an integer for version numbers
version = 17


cloudstream {
    // All of these properties are optional, you can safely remove them

    description = "Flixify Movie Repository"
    authors = listOf("qolbudr")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 // will be 3 if unspecified

    // List of video source types. Users are able to filter for extensions in a given category.
    // You can find a list of avaliable types here:
    // https://recloudstream.github.io/cloudstream/html/app/com.lagradost.cloudstream3/-tv-type/index.html
    tvTypes = listOf(
        "Movie"
    )

    iconUrl = "https://st2.depositphotos.com/3867453/6512/v/450/depositphotos_65122799-stock-illustration-letter-f-wing-flag-logo.jpg"
}
