package io.rbware.zamboni.data.leagues

import io.rbware.zamboni.data.model.League

/** Leagues the app knows about. IDs are stored in user preferences, so don't change them. */
object Leagues {
    val NHL = League(id = "nhl", shortName = "NHL", name = "National Hockey League")
    val AHL = League(id = "ahl", shortName = "AHL", name = "American Hockey League")
}
