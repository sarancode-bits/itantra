package com.itantra.di

import android.content.Context
import androidx.room.Room
import com.itantra.data.db.AppDatabase
import com.itantra.data.db.MessageDao
import com.itantra.data.db.PeerDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "itantra_db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideMessageDao(db: AppDatabase): MessageDao = db.messageDao()

    @Provides
    fun providePeerDao(db: AppDatabase): PeerDao = db.peerDao()
}
