package app.nod.trip

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.nod.R
import app.nod.data.model.PlaceKind

@DrawableRes
fun PlaceKind.iconRes(): Int = when (this) {
    PlaceKind.TRAIN -> R.drawable.ic_kind_train
    PlaceKind.METRO -> R.drawable.ic_kind_metro
    PlaceKind.TRAM -> R.drawable.ic_kind_tram
    PlaceKind.BUS -> R.drawable.ic_kind_bus
    PlaceKind.FERRY -> R.drawable.ic_kind_ferry
    PlaceKind.CITY -> R.drawable.ic_kind_city
    PlaceKind.PLACE -> R.drawable.ic_kind_place
}

@StringRes
fun PlaceKind.labelRes(): Int = when (this) {
    PlaceKind.TRAIN -> R.string.kind_train
    PlaceKind.METRO -> R.string.kind_metro
    PlaceKind.TRAM -> R.string.kind_tram
    PlaceKind.BUS -> R.string.kind_bus
    PlaceKind.FERRY -> R.string.kind_ferry
    PlaceKind.CITY -> R.string.kind_city
    PlaceKind.PLACE -> R.string.kind_place
}
