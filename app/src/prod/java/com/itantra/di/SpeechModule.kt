package com.itantra.di

import com.itantra.core.speech.AndroidSttEngine
import com.itantra.core.speech.AndroidTtsEngine
import com.itantra.core.speech.SpeechToText
import com.itantra.core.speech.TextToSpeechEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SpeechModule {

    @Binds
    @Singleton
    abstract fun bindSpeechToText(impl: AndroidSttEngine): SpeechToText

    @Binds
    @Singleton
    abstract fun bindTextToSpeech(impl: AndroidTtsEngine): TextToSpeechEngine
}
