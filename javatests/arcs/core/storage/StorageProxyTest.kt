package arcs.core.storage

import arcs.core.crdt.CrdtData
import arcs.core.crdt.CrdtModel
import arcs.core.crdt.CrdtOperation
import arcs.core.crdt.internal.VersionMap
import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import kotlin.random.Random

@Suppress("UNCHECKED_CAST", "UNUSED_VARIABLE")
@ExperimentalCoroutinesApi
@RunWith(JUnit4::class)
class StorageProxyTest {
    @Mock private lateinit var mockStorageEndpoint:
            StorageCommunicationEndpoint<CrdtData, CrdtOperation, String>
    @Mock private lateinit var mockStorageEndpointProvider:
            StorageCommunicationEndpointProvider<CrdtData, CrdtOperation, String>
    @Mock private lateinit var mockCrdtOperation: CrdtOperation
    @Mock private lateinit var mockCrdtModel: CrdtModel<CrdtData, CrdtOperation, String>
    @Mock private lateinit var mockCrdtData: CrdtData

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        whenever(mockStorageEndpointProvider.getStorageEndpoint()).thenReturn(mockStorageEndpoint)
        whenever(mockCrdtModel.data).thenReturn(mockCrdtData)
        whenever(mockCrdtModel.versionMap).thenReturn(VersionMap())
    }

    @Test
    fun propagatesStorageOpToReaders() = runBlockingTest {
        val storageProxy = StorageProxy(mockStorageEndpointProvider, mockCrdtModel)
        val readHandle = newHandle("testReader", storageProxy, true)
        mockCrdtModel.appliesOpAs(mockCrdtOperation, true)

        storageProxy.registerHandle(readHandle)
        storageProxy.onMessage(ProxyMessage.Operations(listOf(mockCrdtOperation), null))

        verify(readHandle.callback!!).onUpdate(mockCrdtOperation)
    }

    @Test
    fun propagatesStorageFullModelToReaders() = runBlockingTest {
        val storageProxy = StorageProxy(mockStorageEndpointProvider, mockCrdtModel)
        val readHandle = newHandle("testReader", storageProxy, true)
        mockCrdtModel.appliesOpAs(mockCrdtOperation, true)

        storageProxy.registerHandle(readHandle)
        storageProxy.onMessage(ProxyMessage.ModelUpdate(mockCrdtData, null))

        verify(readHandle.callback!!).onSync()
    }

    @Test
    fun propagatesStorageSyncReqToStorage() = runBlockingTest {
        val storageProxy = StorageProxy(mockStorageEndpointProvider, mockCrdtModel)
        mockCrdtModel.appliesOpAs(mockCrdtOperation, true)

        storageProxy.onMessage(ProxyMessage.SyncRequest(null))

        val modelUpdate = ProxyMessage.ModelUpdate<CrdtData, CrdtOperation, String>(mockCrdtData, null)
        verify(mockStorageEndpoint).onProxyMessage(modelUpdate)
    }

    @Test
    fun propagatesUpdatesToReadersAndNotToWriters() = runBlockingTest {
        val storageProxy = StorageProxy(mockStorageEndpointProvider, mockCrdtModel)
        val readHandle = newHandle("testReader", storageProxy, true)
        val writeHandle = newHandle("testWriter", storageProxy, false)
        mockCrdtModel.appliesOpAs(mockCrdtOperation, true)

        storageProxy.registerHandle(readHandle)
        storageProxy.registerHandle(writeHandle)
        assertThat(storageProxy.applyOp(mockCrdtOperation)).isTrue()

        verify(readHandle.callback!!).onUpdate(mockCrdtOperation)
        verifyNoMoreInteractions(writeHandle.callback!!)
    }

    @Test
    fun failedApplyOpTriggersSync() = runBlockingTest {
        val storageProxy = StorageProxy(mockStorageEndpointProvider, mockCrdtModel)
        val readHandle = newHandle("testReader", storageProxy, true)
        mockCrdtModel.appliesOpAs(mockCrdtOperation, false)

        storageProxy.registerHandle(readHandle)
        assertThat(storageProxy.applyOp(mockCrdtOperation)).isFalse()

        val syncReq = ProxyMessage.SyncRequest<CrdtData, CrdtOperation, String>(null)
        verify(mockStorageEndpoint).onProxyMessage(syncReq)
    }

    @Test
    fun getParticleViewReturnsSyncedState() = runBlockingTest {
        val storageProxy = StorageProxy(mockStorageEndpointProvider, mockCrdtModel)
        whenever(mockCrdtModel.consumerView).thenReturn("someData")
        val readHandle = newHandle("testReader", storageProxy, true)
        mockCrdtModel.appliesOpAs(mockCrdtOperation, true)

        storageProxy.registerHandle(readHandle)
        assertThat(storageProxy.onMessage(ProxyMessage.Operations(listOf(mockCrdtOperation), null)))
            .isTrue()
        val view = storageProxy.getParticleView()

        assertThat(view.value).isEqualTo("someData")
        assertThat(view.versionMap).isEqualTo(VersionMap())
    }

    @Test
    fun getParticleViewWhenUnsyncedQueues() = runBlockingTest {
        val storageProxy = StorageProxy(mockStorageEndpointProvider, mockCrdtModel)
        whenever(mockCrdtModel.consumerView).thenReturn("someData")
        val readHandle = newHandle("testReader", storageProxy, true)
        mockCrdtModel.appliesOpAs(mockCrdtOperation, true)
        storageProxy.registerHandle(readHandle)

        // get view when not synced
        val future = storageProxy.getParticleViewAsync()
        assertThat(future.isCompleted).isFalse()

        // cleanly apply op from Store so we are now synced
        assertThat(storageProxy.onMessage(ProxyMessage.Operations(listOf(mockCrdtOperation), null)))
            .isTrue()

        assertThat(future.isCompleted).isTrue()
        val view = future.await()

        assertThat(view.value).isEqualTo("someData")
        assertThat(view.versionMap).isEqualTo(VersionMap())
    }

    @Test
    fun deadlockDetectionTest() = runBlockingTest {
        val storageProxy = StorageProxy(mockStorageEndpointProvider, mockCrdtModel)
        whenever(mockCrdtModel.consumerView).thenReturn("someData")

        val regHandleFunc = suspend {
            storageProxy.registerHandle(newHandle("testReader", storageProxy, true))
        }

        val deregHandleFunc = suspend {
            val handle = newHandle("testReader", storageProxy, true)
            storageProxy.registerHandle(handle)
            storageProxy.deregisterHandle(handle)
        }

        val proxySyncFunc = suspend {
            val syncReq = ProxyMessage.SyncRequest<CrdtData, CrdtOperation, String>(null)
            verify(mockStorageEndpoint).onProxyMessage(syncReq)
        }

        val proxyOpFunc = suspend {
            val op = mock(CrdtOperation::class.java)
            mockCrdtModel.appliesOpAs(op, Random.nextBoolean())
            storageProxy.onMessage(ProxyMessage.Operations(listOf(op), null))
        }


    }

    private fun newHandle(name: String,
                          storageProxy: StorageProxy<CrdtData, CrdtOperation, String>,
                          reader: Boolean
    ) = Handle(name, storageProxy, reader).apply {
        this.callback = mock(Callbacks::class.java) as Callbacks<CrdtOperation>
    }

    fun CrdtModel<CrdtData, CrdtOperation, String>.appliesOpAs(op: CrdtOperation, result: Boolean) {
        whenever(this.applyOperation(op)).thenReturn(result)
    }
}
