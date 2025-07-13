package com.cs.timeleak.data

object AppCategoryPackages {
    val SOCIAL_MEDIA = setOf(
        "com.facebook.katana", // Facebook
        "com.facebook.lite", // Facebook Lite
        "com.instagram.android", // Instagram
        "com.instagram.barcelona", // Threads (Instagram's text-based app)
        "com.zhiliaoapp.musically", // TikTok (Most common global)
        "com.ss.android.ugc.trill", // TikTok (Alternate/older, sometimes seen)
        "com.ss.android.ugc.aweme", // Douyin (TikTok's Chinese version)
        "com.snapchat.android", // Snapchat
        "com.twitter.android", // X (formerly Twitter)
        "xyz.blueskyweb.app", // Bluesky
        "com.reddit.frontpage", // Reddit
        "com.pinterest", // Pinterest
        "com.linkedin.android", // LinkedIn
        "com.discord", // Discord
        "com.tumblr", // Tumblr
        "com.pinterest.app_lite", // Pinterest Lite (less common, but exists)
    )

    val ENTERTAINMENT = setOf(
        "com.disney.disneyplus", // Disney+
        "com.netflix.mediaclient", // Netflix
        "com.amazon.avod.thirdpartyclient", // Amazon Prime Video
        "com.hulu.plus", // Hulu
        "com.hbo.max", // Max (formerly HBO Max)
        "com.peacock.android", // Peacock TV
        "com.google.android.youtube", // YouTube
        "com.google.android.apps.youtube.kids", // YouTube Kids
        "app.revanced.android.youtube", // YouTube (Revanced)
        "com.spotify.music", // Spotify
        "com.apple.atv", // Apple TV (on Android, if available via side-loading or specific devices)
        "com.paramount.plus", // Paramount+
        "com.plexapp.android", // Plex (media server & free content)
        "tv.pluto.android", // Pluto TV (Free Live TV & Movies)
        "com.tubi", // Tubi (Free Movies & TV)
        "com.crunchyroll.crunchyroid", // Crunchyroll (Anime)
        "com.twitch.android", // Twitch (Live Streaming, primarily gaming)

        // Add more as needed
    )

    // Add more categories as needed
} 