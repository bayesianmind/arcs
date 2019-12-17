/*
 * Copyright 2019 Google LLC.
 *
 * This code may only be used under the BSD style license found at
 * http://polymer.github.io/LICENSE.txt
 *
 * Code distributed by Google as part of this project is also subject to an additional IP rights
 * grant found at
 * http://polymer.github.io/PATENTS.txt
 */

package arcs.sdk.android.storage.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import arcs.android.storage.ParcelableStoreOptions
import arcs.core.storage.Store
import arcs.core.storage.StoreOptions
import arcs.core.storage.driver.RamDiskDriverProvider
import arcs.core.storage.driver.RamDiskStorageKey
import arcs.core.storage.referencemode.ReferenceModeStorageKey
import arcs.core.util.Log
import arcs.core.util.TaggedLog
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel

/**
 * Implementation of a [Service] which manages [Store]s and exposes the ability to access them via
 * the [IStorageService] interface when bound-to by a client.
 */
class StorageService : Service() {
    private val coroutineContext = Dispatchers.IO + CoroutineName("StorageService")
    private val scope = CoroutineScope(coroutineContext)
    private val stores = ConcurrentHashMap<StoreOptions<*, *, *>, Store<*, *, *>>()

    init {
        // Inititalization/registration
        Log.debug { "StorageService - initialization" }
        RamDiskDriverProvider()
        RamDiskStorageKey("poo")
        ReferenceModeStorageKey.registerParser()
    }

    override fun onCreate() {
        super.onCreate()
        log.debug { "onCreate" }
    }

    override fun onBind(intent: Intent): IBinder? {
        log.debug { "onBind" }
        val parcelableOptions = requireNotNull(
            intent.getParcelableExtra<ParcelableStoreOptions?>(EXTRA_OPTIONS)
        ) { "No StoreOptions found in Intent" }

        return BindingContext(
            stores.computeIfAbsent(parcelableOptions.actual) { Store(it) },
            parcelableOptions.crdtType,
            coroutineContext
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        log.debug { "onDestroy" }
        scope.cancel()
    }

    companion object {
        private const val EXTRA_OPTIONS = "storeOptions"
        private val log = TaggedLog { "StorageService" }

        /**
         * Creates an [Intent] to use when binding to the [StorageService] from a [ServiceStore].
         */
        fun createBindIntent(context: Context, storeOptions: ParcelableStoreOptions): Intent =
            Intent(context, StorageService::class.java).apply {
                putExtra(EXTRA_OPTIONS, storeOptions)
            }
    }
}
