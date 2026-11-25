package com.denser.june.core.utils


object Constants {
    const val GITHUB_URL = "https://github.com/DenserMeerkat/June"
    const val GITHUB_RELEASES_LATEST_URL = "https://api.github.com/repos/DenserMeerkat/June/releases/latest"
    const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.denser.june.play"
    const val WEBLATE_URL = "https://hosted.weblate.org/engage/june/"
    const val ODESIL_URL = "https://api.song.link/"
    const val DEVELOPER_GITHUB_URL = "https://github.com/DenserMeerkat"
    const val DEVELOPER_EMAIL = "mailto:densermeerkat11@gmail.com"
    const val BUY_ME_A_COFFEE_URL = "https://buymeacoffee.com/densermeerkat"
    const val DEVELOPER_IMAGE = "https://github.com/DenserMeerkat.png"
    const val DEVELOPER_NAME = "Denser Meerkat"

    // Map Attribution & Copyright Links
    const val MAPLIBRE_URL = "https://maplibre.org/"
    const val AMAP_URL = "https://www.amap.com/"
    const val MAPTILER_COPYRIGHT_URL = "https://www.maptiler.com/copyright/"
    const val STADIA_ATTRIBUTION_URL = "https://stadiamaps.com/attribution/"
    const val CARTO_ATTRIBUTION_URL = "https://carto.com/attributions"
    const val OPENMAP_URL = "https://openfreemap.org"
    const val OPENSTREETMAP_COPYRIGHT_URL = "https://www.openstreetmap.org/copyright"

    // Map API Key Signup Links
    const val MAPTILER_CLOUD_URL = "https://www.maptiler.com/cloud/"
    const val STADIA_CLIENT_URL = "https://client.stadiamaps.com/"

    // Map API Base URLs
    const val MAPTILER_MAPS_BASE_URL = "https://api.maptiler.com/maps/"
    const val STADIA_MAPS_BASE_URL = "https://tiles.stadiamaps.com/styles/"
    const val CARTO_MAPS_BASE_URL = "https://basemaps.cartocdn.com/gl/"
    const val OPENMAP_MAPS_BASE_URL = "https://tiles.openfreemap.org/styles/"
    const val MAPTILER_GEOCODING_BASE_URL = "https://api.maptiler.com/geocoding/"
    const val NOMINATIM_SEARCH_URL = "https://nominatim.openstreetmap.org/search"
    const val NOMINATIM_REVERSE_URL = "https://nominatim.openstreetmap.org/reverse"

    // Keyless geocoding that stays reachable from mainland China. Nominatim is often
    // unreachable there (reverse lookups time out), which is why selected points ended
    // up as "Unknown Location"; Photon returns street/district names in Chinese.
    const val PHOTON_SEARCH_URL = "https://photon.komoot.io/api/"
    const val PHOTON_REVERSE_URL = "https://photon.komoot.io/reverse"
    const val PHOTON_CHINA_BBOX = "73.5,18.0,135.0,53.8"

    // AMap (高德) raster tiles. Placeholders {x}/{y}/{z} are filled in by MapLibre.
    // Use the "wprd" (new generation) host, not "webrd": webrd serves the legacy tile
    // pyramid frozen around 2016, which is why new roads/POIs went missing.
    // Two layers are stacked: scl=2 style=7 is a 512 px road basemap (sharp up to z19),
    // scl=1 style=8 is the transparent Chinese label/POI overlay.
    const val AMAP_TILE_URL_FORMAT =
        "https://wprd0%d.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scl=2&style=7&x={x}&y={y}&z={z}"
    const val AMAP_LABEL_URL_FORMAT =
        "https://wprd0%d.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scl=1&style=8&x={x}&y={y}&z={z}"
    const val AMAP_TILE_SUBDOMAINS = 4
    const val AMAP_TILE_MIN_ZOOM = 4
    const val AMAP_TILE_MAX_ZOOM = 19
    const val AMAP_LABEL_MIN_ZOOM = 4
    const val AMAP_LABEL_MAX_ZOOM = 18
}
