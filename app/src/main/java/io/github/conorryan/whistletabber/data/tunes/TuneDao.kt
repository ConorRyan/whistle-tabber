package io.github.conorryan.whistletabber.data.tunes

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TuneDao {
    @Query("SELECT * FROM tunes ORDER BY sortKey, id")
    fun observeAll(): Flow<List<TuneEntity>>

    /**
     * [pattern] is a LIKE pattern over [TuneEntity.searchKey] (see
     * [TuneIndex.likePattern]); null [rhythm] matches every rhythm.
     */
    @Query(
        """
        SELECT * FROM tunes
        WHERE searchKey LIKE :pattern ESCAPE '\'
          AND (:rhythm IS NULL OR rhythm = :rhythm)
          AND (:starredOnly = 0 OR starred = 1)
        ORDER BY sortKey, id
        """,
    )
    fun search(pattern: String, rhythm: String?, starredOnly: Boolean): Flow<List<TuneEntity>>

    /** Rhythms present in the library, most common first. */
    @Query(
        """
        SELECT rhythm FROM tunes WHERE rhythm IS NOT NULL AND rhythm != ''
        GROUP BY rhythm ORDER BY COUNT(*) DESC, rhythm
        """,
    )
    fun observeRhythms(): Flow<List<String>>

    @Query("SELECT * FROM tunes WHERE id = :id")
    fun observe(id: Long): Flow<TuneEntity?>

    @Query("SELECT * FROM tunes WHERE id = :id")
    suspend fun get(id: Long): TuneEntity?

    @Query("SELECT * FROM tunes ORDER BY sortKey, id")
    suspend fun getAll(): List<TuneEntity>

    @Query("SELECT * FROM tunes WHERE title = :title")
    suspend fun findByTitle(title: String): List<TuneEntity>

    @Query("SELECT COUNT(*) FROM tunes")
    fun observeCount(): Flow<Int>

    @Insert
    suspend fun insert(tune: TuneEntity): Long

    @Insert
    suspend fun insertAll(tunes: List<TuneEntity>): List<Long>

    @Update
    suspend fun update(tune: TuneEntity)

    @Query("UPDATE tunes SET starred = :starred WHERE id = :id")
    suspend fun setStarred(id: Long, starred: Boolean)

    @Query("DELETE FROM tunes WHERE id = :id")
    suspend fun delete(id: Long)
}
