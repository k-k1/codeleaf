package com.k1.gitreader.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RepoDao {
    @Query("SELECT * FROM repos ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Repo>>

    @Query("SELECT * FROM repos WHERE id = :id")
    suspend fun getById(id: Long): Repo?

    @Insert
    suspend fun insert(repo: Repo): Long

    @Update
    suspend fun update(repo: Repo)

    @Delete
    suspend fun delete(repo: Repo)
}
