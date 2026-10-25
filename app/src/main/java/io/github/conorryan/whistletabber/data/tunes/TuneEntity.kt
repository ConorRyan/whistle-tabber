package io.github.conorryan.whistletabber.data.tunes

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One stored tune. [abc] is the canonical record; every other text column is
 * an index derived from it by [TuneRepository] on each save (see [TuneIndex]).
 */
@Entity(
    tableName = "tunes",
    indices = [Index("sortKey"), Index("rhythm")],
)
data class TuneEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val abc: String,
    val title: String,
    /** Alternate titles (second and later `T:`), one per line. */
    val altTitles: String,
    /** All titles folded by [TuneIndex.fold], one per line, for search. */
    val searchKey: String,
    /** The main title folded, without a leading "the", for sorting. */
    val sortKey: String,
    /** Lowercase `R:` value, e.g. "reel" or "slip jig". */
    val rhythm: String?,
    val keySignature: String?,
    val meter: String?,
    /** [io.github.conorryan.whistletabber.music.WhistleKey] name: the tune's directive, else the one its key suggests. */
    val whistle: String?,
    val starred: Boolean = false,
    /** Where the tune came from: [SOURCE_USER], [SOURCE_IMPORT] or [SOURCE_STARTER]. */
    val source: String,
    val createdAt: Long,
    val updatedAt: Long,
) {
    companion object {
        const val SOURCE_USER = "user"
        const val SOURCE_IMPORT = "import"
        const val SOURCE_STARTER = "starter"
    }
}
