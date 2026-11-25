package com.denser.june.core.domain.model.enums

import com.denser.june.core.R

enum class MapStyleProvider(val displayNameRes: Int) {
    AMAP(R.string.amap),
    CARTO(R.string.carto),
    OPENMAP(R.string.openmap),
    STADIA(R.string.stadia),
    MAPTILER(R.string.maptiler)
}
