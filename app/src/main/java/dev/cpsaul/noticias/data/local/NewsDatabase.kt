package dev.cpsaul.noticias.data.local

import android.content.Context
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
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

/**
 * Catálogo local-first de verificaciones de hechos (Schema.org ClaimReview / Data Commons / Full Fact / Google Fact Check).
 */
@Entity(
    tableName = "claim_reviews",
    indices = [
        Index(value = ["source_provider"], name = "idx_claim_reviews_provider"),
        Index(value = ["synced_at"], name = "idx_claim_reviews_synced_at")
    ]
)
data class ClaimReviewEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "claim_text") val claimText: String,
    @ColumnInfo(name = "rating_label") val ratingLabel: String,
    @ColumnInfo(name = "publisher_name") val publisherName: String,
    @ColumnInfo(name = "review_url") val reviewUrl: String,
    @ColumnInfo(name = "source_provider") val sourceProvider: String,
    @ColumnInfo(name = "published_date") val publishedDate: String? = null,
    @ColumnInfo(name = "synced_at") val syncedAt: Long
)

@Dao
interface NewsDao {
    @Query("SELECT * FROM feeds ORDER BY category ASC, title ASC")
    fun observeFeeds(): Flow<List<FeedEntity>>

    @Query("SELECT * FROM feeds ORDER BY category ASC, title ASC")
    suspend fun getAllFeeds(): List<FeedEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFeeds(feeds: List<FeedEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFeed(feed: FeedEntity)

    @Query("DELETE FROM feeds WHERE id = :feedId")
    suspend fun deleteFeed(feedId: String)

    @Query("UPDATE feeds SET last_synced_at = :timestamp WHERE id = :feedId")
    suspend fun updateFeedLastSynced(feedId: String, timestamp: Long)

    @Query("SELECT * FROM articles ORDER BY published_at DESC")
    fun observeArticles(): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE is_bookmarked = 1 ORDER BY published_at DESC")
    fun observeBookmarkedArticles(): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE feed_id = :feedId ORDER BY published_at DESC")
    fun observeArticlesByFeed(feedId: String): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE timeline_id = :timelineId ORDER BY published_at ASC")
    fun observeTimelineArticles(timelineId: String): Flow<List<ArticleEntity>>

    @Query(
        """
        SELECT * FROM articles
        WHERE title LIKE '%' || :query || '%'
           OR content_markdown LIKE '%' || :query || '%'
           OR COALESCE(summary, '') LIKE '%' || :query || '%'
        ORDER BY published_at DESC
        """
    )
    fun searchArticles(query: String): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE id = :articleId LIMIT 1")
    suspend fun getArticleById(articleId: String): ArticleEntity?

    @Query("SELECT id FROM articles")
    suspend fun getExistingArticleIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertArticles(articles: List<ArticleEntity>)

    @Query("UPDATE articles SET is_read = :isRead WHERE id = :articleId")
    suspend fun setArticleRead(articleId: String, isRead: Boolean)

    @Query("UPDATE articles SET is_bookmarked = :isBookmarked WHERE id = :articleId")
    suspend fun setArticleBookmarked(articleId: String, isBookmarked: Boolean)

    @Query("UPDATE articles SET summary = :summary WHERE id = :articleId")
    suspend fun updateArticleSummary(articleId: String, summary: String)

    @Query("UPDATE articles SET rewritten_title = :rewrittenTitle WHERE id = :articleId")
    suspend fun updateArticleRewrittenTitle(articleId: String, rewrittenTitle: String)

    @Query("UPDATE articles SET image_local_path = :imageLocalPath WHERE id = :articleId")
    suspend fun updateArticleImageLocalPath(articleId: String, imageLocalPath: String?)

    @Query("SELECT * FROM article_timelines ORDER BY last_updated_at DESC")
    fun observeTimelines(): Flow<List<ArticleTimelineEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTimeline(timeline: ArticleTimelineEntity)

    @Query("SELECT * FROM article_quick_action_cache WHERE article_id = :articleId AND action_type = :actionType AND input_hash = :inputHash LIMIT 1")
    suspend fun getQuickActionCache(articleId: String, actionType: String, inputHash: String): QuickActionCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertQuickActionCache(entry: QuickActionCacheEntity)

    @Query("DELETE FROM article_quick_action_cache")
    suspend fun clearQuickActionCache()

    @Query("SELECT * FROM claim_reviews ORDER BY synced_at DESC")
    fun observeClaimReviews(): Flow<List<ClaimReviewEntity>>

    @Query("SELECT * FROM claim_reviews ORDER BY synced_at DESC")
    suspend fun getAllClaimReviews(): List<ClaimReviewEntity>

    @Query("SELECT COUNT(*) FROM claim_reviews")
    suspend fun getClaimReviewCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertClaimReviews(entries: List<ClaimReviewEntity>)

    @Query("DELETE FROM claim_reviews")
    suspend fun clearClaimReviews()
}

@Database(
    entities = [
        FeedEntity::class,
        ArticleEntity::class,
        ArticleTimelineEntity::class,
        QuickActionCacheEntity::class,
        ClaimReviewEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class NewsDatabase : RoomDatabase() {
    abstract fun newsDao(): NewsDao

    companion object {
        const val DATABASE_NAME = "pulso_news.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `claim_reviews` (
                        `id` TEXT NOT NULL,
                        `claim_text` TEXT NOT NULL,
                        `rating_label` TEXT NOT NULL,
                        `publisher_name` TEXT NOT NULL,
                        `review_url` TEXT NOT NULL,
                        `source_provider` TEXT NOT NULL,
                        `published_date` TEXT,
                        `synced_at` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_claim_reviews_provider` ON `claim_reviews` (`source_provider`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_claim_reviews_synced_at` ON `claim_reviews` (`synced_at`)")
            }
        }

        @Volatile
        private var instance: NewsDatabase? = null

        fun getInstance(context: Context): NewsDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    NewsDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
