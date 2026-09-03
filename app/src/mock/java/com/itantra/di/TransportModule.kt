package com.itantra.di

import com.itantra.core.transport.MockTransport
import com.itantra.core.transport.P2pTransport
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TransportModule {

    @Binds
    @Singleton
    abstract fun bindP2pTransport(impl: MockTransport): P2pTransport
}
