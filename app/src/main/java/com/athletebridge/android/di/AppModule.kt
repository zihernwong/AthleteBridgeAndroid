package com.athletebridge.android.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.memoryCacheSettings
import com.google.firebase.firestore.persistentCacheSettings
import com.google.firebase.storage.FirebaseStorage
import com.athletebridge.android.data.repository.AuthRepository
import com.athletebridge.android.data.repository.AvailabilityTemplateRepository
import com.athletebridge.android.data.repository.CoachRepository
import com.athletebridge.android.data.repository.ClientRepository
import com.athletebridge.android.data.repository.BookingRepository
import com.athletebridge.android.data.repository.ChatRepository
import com.athletebridge.android.data.repository.ReviewRepository
import com.athletebridge.android.data.repository.TournamentRepository
import com.athletebridge.android.data.repository.NotificationRepository
import com.athletebridge.android.data.repository.PhotoRepository
import com.athletebridge.android.data.repository.SubjectRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance().apply {
        firestoreSettings = firestoreSettings {
            setLocalCacheSettings(persistentCacheSettings {
                setSizeBytes(com.google.firebase.firestore.FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
            })
        }
    }

    @Provides
    @Singleton
    fun provideFirebaseStorage(): FirebaseStorage = FirebaseStorage.getInstance()

    @Provides
    @Singleton
    fun provideAuthRepository(
        auth: FirebaseAuth,
        firestore: FirebaseFirestore
    ): AuthRepository = AuthRepository(auth, firestore)

    @Provides
    @Singleton
    fun provideCoachRepository(
        firestore: FirebaseFirestore,
        storage: FirebaseStorage
    ): CoachRepository = CoachRepository(firestore, storage)

    @Provides
    @Singleton
    fun provideClientRepository(
        firestore: FirebaseFirestore,
        storage: FirebaseStorage
    ): ClientRepository = ClientRepository(firestore, storage)

    @Provides
    @Singleton
    fun provideBookingRepository(
        firestore: FirebaseFirestore
    ): BookingRepository = BookingRepository(firestore)

    @Provides
    @Singleton
    fun provideChatRepository(
        firestore: FirebaseFirestore,
        storage: FirebaseStorage
    ): ChatRepository = ChatRepository(firestore, storage)

    @Provides
    @Singleton
    fun provideReviewRepository(
        firestore: FirebaseFirestore
    ): ReviewRepository = ReviewRepository(firestore)

    @Provides
    @Singleton
    fun provideTournamentRepository(
        firestore: FirebaseFirestore
    ): TournamentRepository = TournamentRepository(firestore)

    @Provides
    @Singleton
    fun provideNotificationRepository(
        firestore: FirebaseFirestore,
        auth: FirebaseAuth
    ): NotificationRepository = NotificationRepository(firestore, auth)

    @Provides
    @Singleton
    fun providePhotoRepository(
        storage: FirebaseStorage
    ): PhotoRepository = PhotoRepository(storage)

    @Provides
    @Singleton
    fun provideSubjectRepository(
        firestore: FirebaseFirestore
    ): SubjectRepository = SubjectRepository(firestore)

    @Provides
    @Singleton
    fun provideAvailabilityTemplateRepository(
        firestore: FirebaseFirestore
    ): AvailabilityTemplateRepository = AvailabilityTemplateRepository(firestore)

}
