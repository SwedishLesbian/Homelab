package com.homelab.app.di

import com.homelab.app.ssh.SshManager
import com.homelab.app.data.security.KeystoreManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SshModule {

    @Provides
    @Singleton
    fun provideSshManager(keystoreManager: KeystoreManager): SshManager =
        SshManager(keystoreManager)
}
