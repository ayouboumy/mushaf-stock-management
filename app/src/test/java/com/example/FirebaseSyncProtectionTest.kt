package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.entity.DestinationEntity
import com.example.data.entity.ProductEntity
import com.example.data.entity.ProductVariantEntity
import com.example.data.entity.StockMovementEntity
import com.example.data.sync.FirebaseSyncManager
import com.example.data.sync.RemoteProductRecord
import com.example.data.sync.SyncEntityType
import com.example.data.sync.UniqueIdGenerator
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FirebaseSyncProtectionTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var syncManager: FirebaseSyncManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        syncManager = FirebaseSyncManager(database, context = null)
        syncManager.clearLocalSyncMetadata()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun seedLocalSampleData(): Long {
        val prodId = 1001L
        val varId = 2001L
        val destId = 3001L
        val moveId = 4001L

        database.productDao().insertProduct(
            ProductEntity(
                id = prodId,
                nameArabic = "المصحف المحمدي",
                initialStock = 500,
                updatedAt = 1000L
            )
        )
        database.productVariantDao().insertVariant(
            ProductVariantEntity(
                id = varId,
                productId = prodId,
                nameArabic = "رواية ورش"
            )
        )
        database.destinationDao().insertDestination(
            DestinationEntity(
                id = destId,
                name = "مسجد الفتح"
            )
        )
        database.stockMovementDao().insertMovement(
            StockMovementEntity(
                id = moveId,
                productId = prodId,
                variantId = varId,
                movementType = "STOCK_IN",
                quantity = 200,
                dateMillis = 1774500000000L,
                dateFormatted = "2026-09-29"
            )
        )
        return prodId
    }

    @Test
    fun `pullAllFromRestApi returning null on 403 or offline preserves all local Room data`() = runBlocking {
        seedLocalSampleData()

        val result = syncManager.pullAllFromRestApiWithFetcher { null }

        assertFalse("REST sync must return false when requests fail", result)
        assertEquals(1, database.productDao().getAllProductsList().size)
        assertEquals(1, database.productVariantDao().getAllVariantsList().size)
        assertEquals(1, database.stockMovementDao().getAllMovementsList().size)
        assertEquals(1, database.destinationDao().getAllDestinationsList().size)
    }

    @Test
    fun `partial REST failure preserves data in failed collections`() = runBlocking {
        seedLocalSampleData()

        val result = syncManager.pullAllFromRestApiWithFetcher { collection ->
            if (collection == "movements") null else emptyList()
        }

        assertFalse(result)
        // Local unsynced items are preserved in all collections including failed movements
        assertEquals(1, database.productDao().getAllProductsList().size)
        assertEquals(1, database.stockMovementDao().getAllMovementsList().size)
    }

    @Test
    fun `empty server snapshot preserves local unsynced records created before sync`() = runBlocking {
        val prodId = seedLocalSampleData()
        syncManager.markLocalPendingPush(SyncEntityType.PRODUCT, prodId)

        // Simulate another user opening app or empty remote server snapshot
        syncManager.mergeRemoteProducts(emptyList(), isAuthoritativeServerSnapshot = true)
        syncManager.mergeRemoteVariants(emptyList(), isAuthoritativeServerSnapshot = true)
        syncManager.mergeRemoteMovements(emptyList(), isAuthoritativeServerSnapshot = true)
        syncManager.mergeRemoteDestinations(emptyList(), isAuthoritativeServerSnapshot = true)

        assertEquals(1, database.productDao().getAllProductsList().size)
        assertEquals(1, database.productVariantDao().getAllVariantsList().size)
        assertEquals(1, database.stockMovementDao().getAllMovementsList().size)
        assertEquals(1, database.destinationDao().getAllDestinationsList().size)
    }

    @Test
    fun `non-authoritative cache snapshot never prunes previously synced cloud records`() = runBlocking {
        val prodId = seedLocalSampleData()
        syncManager.syncStore.markKnownCloud(SyncEntityType.PRODUCT, prodId)

        // Simulate empty cached snapshot (isFromCache = true -> isAuthoritative = false)
        syncManager.mergeRemoteProducts(emptyList(), isAuthoritativeServerSnapshot = false)

        assertNotNull(database.productDao().getProductById(prodId))
    }

    @Test
    fun `authoritative server snapshot prunes only previously synced item removed from server while keeping local unsynced item`() = runBlocking {
        val syncedId = 1001L
        val unsyncedId = 1002L
        database.productDao().insertProduct(ProductEntity(id = syncedId, nameArabic = "صنف متزامن"))
        database.productDao().insertProduct(ProductEntity(id = unsyncedId, nameArabic = "صنف محلي جديد"))

        syncManager.syncStore.markKnownCloud(SyncEntityType.PRODUCT, syncedId)
        syncManager.markLocalPendingPush(SyncEntityType.PRODUCT, unsyncedId)

        // Server says products is empty -> syncedId was deleted on server, unsyncedId hasn't uploaded yet
        syncManager.mergeRemoteProducts(emptyList(), isAuthoritativeServerSnapshot = true)

        assertNull("Previously synced item deleted on server should be pruned", database.productDao().getProductById(syncedId))
        assertNotNull("Unsynced local item must NEVER be deleted", database.productDao().getProductById(unsyncedId))
    }

    @Test
    fun `newer local pending edit is not overwritten by stale remote product`() = runBlocking {
        val id = 5001L
        database.productDao().insertProduct(
            ProductEntity(id = id, nameArabic = "النسخة المحدثة محلياً", updatedAt = 5000L)
        )
        syncManager.markLocalPendingPush(SyncEntityType.PRODUCT, id)

        val staleRemote = RemoteProductRecord(
            entity = ProductEntity(id = id, nameArabic = "النسخة القديمة من السحابة", updatedAt = 1000L),
            updatedAt = 1000L
        )
        syncManager.mergeRemoteProducts(listOf(staleRemote), isAuthoritativeServerSnapshot = true)

        assertEquals("النسخة المحدثة محلياً", database.productDao().getProductById(id)?.nameArabic)
    }

    @Test
    fun `UniqueIdGenerator produces unique positive 53-bit safe monotonic IDs`() {
        val ids = (1..100).map { UniqueIdGenerator.nextId() }
        assertEquals(100, ids.toSet().size)
        for (i in 1 until ids.size) {
            assertTrue(ids[i] > ids[i - 1])
            assertTrue(ids[i] > 0L)
            assertTrue(ids[i] < 9_007_199_254_740_991L)
        }
    }
}
