package dev.cpsaul.noticias.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "feeds")
data class FeedEntity(
    @PrimaryKey val id: String,
    val title: String,
    val url: String,
    val category: String,
    @ColumnInfo(name = "icon_url") val iconUrl: String? = null,
    @ColumnInfo(name = "last_synced_at") val lastSyncedAt: Long
)

@Entity(
    tableName = "articles",
    foreignKeys = [
        ForeignKey(
            entity = FeedEntity::class,
            parentColumns = ["id"],
            childColumns = ["feed_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["feed_id"], name = "idx_articles_feed_id"),
        Index(value = ["timeline_id"], name = "idx_articles_timeline_id"),
        Index(value = ["published_at"], name = "idx_articles_published_at")
    ]
)
data class ArticleEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "feed_id") val feedId: String,
    @ColumnInfo(name = "timeline_id") val timelineId: String? = null,
    val title: String,
    @ColumnInfo(name = "rewritten_title") val rewrittenTitle: String? = null,
    val author: String? = null,
    @ColumnInfo(name = "content_markdown") val contentMarkdown: String,
    val summary: String? = null,
    val url: String,
    @ColumnInfo(name = "image_local_path") val imageLocalPath: String? = null,
    @ColumnInfo(name = "published_at") val publishedAt: Long,
    @ColumnInfo(name = "is_read", defaultValue = "0") val isRead: Boolean = false,
    @ColumnInfo(name = "is_bookmarked", defaultValue = "0") val isBookmarked: Boolean = false
)

@Entity(tableName = "article_timelines")
data class ArticleTimelineEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "topic_name") val topicName: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "last_updated_at") val lastUpdatedAt: Long
)

@Entity(
    tableName = "article_quick_action_cache",
    foreignKeys = [
        ForeignKey(
            entity = ArticleEntity::class,
            parentColumns = ["id"],
            childColumns = ["article_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(
            value = ["article_id", "action_type", "input_hash"],
            name = "idx_quick_action_hash",
            unique = true
        )
    ]
)
data class QuickActionCacheEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "article_id") val articleId: String,
    @ColumnInfo(name = "action_type") val actionType: String,
    @ColumnInfo(name = "input_hash") val inputHash: String,
    @ColumnInfo(name = "output_result") val outputResult: String,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

@Dao
interface NewsDao {
    @Query("SELECT * FROM feeds ORDER BY category ASC, title ASC")
    fun observeFeeds(): Flow<List<FeedEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFeeds(feeds: List<FeedEntity>)

    @Query("SELECT * FROM articles ORDER BY published_at DESC")
    fun observeArticles(): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE timeline_id = :timelineId ORDER BY published_at ASC")
    fun observeTimelineArticles(timelineId: String): Flow<List<ArticleEntity>>

    @Query("SELECT id FROM articles")
    suspend fun getExistingArticleIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertArticles(articles: List<ArticleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTimeline(timeline: ArticleTimelineEntity)

    @Query("SELECT * FROM article_quick_action_cache WHERE article_id = :articleId AND action_type = :actionType AND input_hash = :inputHash LIMIT 1")
    suspend fun getQuickActionCache(articleId: String, actionType: String, inputHash: String): QuickActionCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertQuickActionCache(entry: QuickActionCacheEntity)
}

@Database(
    entities = [
        FeedEntity::class,
        ArticleEntity::class,
        ArticleTimelineEntity::class,
        QuickActionCacheEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class NewsDatabase : RoomDatabase() {
    abstract fun newsDao(): NewsDao
}
