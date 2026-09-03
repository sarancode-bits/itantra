package com.itantra.di

import com.itantra.core.alert.RealSosAlertPlayer
import com.itantra.core.alert.SosAlertPlayer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AlertModule {

    @Binds
    @Singleton
    abstract fun bindSosAlertPlayer(impl: RealSosAlertPlayer): SosAlertPlayer
}
