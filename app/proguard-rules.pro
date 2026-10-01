# ============================================================================
# CloakDroid ProGuard / R8 rules
# ============================================================================

# --- General / debugging ---------------------------------------------------
-optimizationpasses 5
-dontpreverify
-verbose
-ignorewarnings
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault,PermittedSubclasses,Record

# --- Native method signatures ----------------------------------------------
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# --- Enum members referenced by reflection ---------------------------------
-keepclassmembers,allowoptimization enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    public static **[] $VALUES;
}

# --- Parcelable / Serializable CREATOR -------------------------------------
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# --- AndroidX / support components instantiated by name --------------------
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.Fragment
-keep public class * extends androidx.fragment.app.Fragment
-keep class * extends androidx.fragment.app.Fragment { *; }
-keep class * extends android.preference.Preference { *; }
-keep class * extends android.view.View { *; }
-keep class * extends android.view.ViewGroup { *; }
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class * extends androidx.lifecycle.AndroidViewModel { *; }

# ============================================================================
# GeckoView (org.mozilla.geckoview)
# ============================================================================
-keep class org.mozilla.geckoview.** { *; }
-keep class org.mozilla.geckoview.**$* { *; }
-keep class org.mozilla.gecko.** { *; }
-keep class org.mozilla.gecko.**$* { *; }
-keep interface org.mozilla.geckoview.** { *; }
-keep interface org.mozilla.geckoview.**$* { *; }

# Callback / delegate interfaces invoked through JNI
-keep class org.mozilla.geckoview.GeckoSession$GeckoViewDelegate { *; }
-keep class org.mozilla.geckoview.GeckoSession$PromptDelegate { *; }
-keep class org.mozilla.geckoview.GeckoSession$NavigationDelegate { *; }
-keep class org.mozilla.geckoview.GeckoSession$PermissionDelegate { *; }
-keep class org.mozilla.geckoview.GeckoSession$ContentDelegate { *; }
-keep class org.mozilla.geckoview.GeckoSession$ProgressDelegate { *; }
-keep class org.mozilla.geckoview.GeckoSession$FilePromptDelegate { *; }
-keep class org.mozilla.geckoview.GeckoSession$SelectionActionDelegate { *; }
-keep class org.mozilla.geckoview.GeckoSession$TextInputDelegate { *; }
-keep class org.mozilla.geckoview.RuntimeSelectionActionDelegate { *; }

# Values / objects marshalled across the Gecko IPC boundary
-keep @org.mozilla.geckoview.AllowDocShellAccess class * { *; }
-keep class org.mozilla.geckoview.** implements android.os.Parcelable { *; }
-keepclassmembers class org.mozilla.geckoview.** {
    @com.google.auto.value.AutoValue *;
    public <init>(...);
    public <fields>;
}

-dontwarn org.mozilla.gecko.**
-dontwarn org.mozilla.geckoview.**
-dontwarn org.mozilla.gecko.**$*
-dontwarn org.mozilla.geckoview.**$*
-dontwarn org.mozilla.mozglue.**
-dontwarn org.mozilla.gecko.util.**
-dontwarn org.mozilla.gecko.**
-dontwarn xulapp.**
-dontwarn xpcwrapper.**

# ============================================================================
# Hilt / Dagger / javax.inject
# ============================================================================
-dontwarn dagger.hilt.**
-dontwarn dagger.hilt.android.**
-dontwarn dagger.hilt.internal.**
-dontwarn dagger.hilt.generators.**
-dontwarn javax.inject.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.squareup.javapoet.**
-dontwarn kotlin.DeprecationLevel

-keep class dagger.hilt.** { *; }
-keep class dagger.hilt.android.** { *; }
-keep class dagger.hilt.internal.** { *; }
-keep class dagger.hilt.components.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.internal.GeneratedComponent { *; }
-keep class * extends dagger.hilt.internal.GeneratedComponentManager { *; }
-keep class * extends dagger.hilt.internal.GeneratedComponentManagerImpl { *; }
-keep class * extends dagger.hilt.android.internal.lifecycle.HiltViewModelFactory { *; }

# Generated entry points and aggregating types
-keep class * extends dagger.hilt.android.EntryPointAccessors { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep @dagger.hilt.InstallIn class * { *; }
-keep @dagger.hilt.components.SingletonComponent class * { *; }
-keep @dagger.Module class * { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keep @dagger.hilt.android.lifecycle.KeepInViewModelComponent class * { *; }
-keep class * extends androidx.hilt.lifecycle.ViewModelFactory { *; }

# Dagger keeps its own graph; keep MembersInjectors used by generated code
-keep class dagger.internal.MembersInjector { *; }
-keep class * extends dagger.internal.MembersInjector { *; }
-keep class *$$InjectAdapter { *; }
-keep class *$$Factory { *; }
-keep class *$$ModuleFactory { *; }
-keep class *$$ProvisionFactory { *; }
-keep class *Hilt_* { *; }
-keep class *Hilt_*HiltModules* { *; }
-keep class *Hilt_*BindsModule { *; }
-keep class *Hilt_*KeyModule { *; }
-keep class **_HiltModules { *; }
-keep class **_HiltModules$* { *; }
-keep class **_GeneratedInjector { *; }

# ============================================================================
# Room
# ============================================================================
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class * extends androidx.room.paging.LimitOffsetPagingSource { *; }

-keep @androidx.room.Database class * { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Embedded class * { *; }
-keep @androidx.room.TypeConverters class * { *; }
-keep @androidx.room.Ignore class * { *; }
-keep @androidx.room.PrimaryKey class * { *; }
-keep @androidx.room.ColumnInfo class * { *; }
-keep @androidx.room.Relation class * { *; }
-keep @androidx.room.Fts4 class * { *; }
-keep @androidx.room.Fts3 class * { *; }
-keep @androidx.room.Ignore class * { *; }
-keep @androidx.room.Index class * { *; }

-keep class * implements androidx.room.migration.Migration { *; }
-keep class * implements androidx.room.InvalidationTracker.Observer { *; }
-keep class * extends androidx.sqlite.db.SupportSQLiteOpenHelper$Callback { *; }

# Generated Room implementation / DAO delegates
-keep class *_Impl { *; }
-keep class **.RoomDatabase { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public static ** getInstance(...);
    public static ** newInstance(...);
    public abstract ***(...);
}
-keep class **.automigration.** { *; }
-dontwarn androidx.room.paging.**
-dontwarn androidx.room.**

# ============================================================================
# kotlinx-serialization
# ============================================================================
-keepattributes AnnotationDefault

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.cloakdroid.**$$serializer { *; }
-keepclassmembers class com.cloakdroid.** {
    *** Companion;
}
-keepclasseswithmembers class com.cloakdroid.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.cloakdroid.** {
    kotlinx.serialization.KSerializer serializer(kotlinx.serialization.internal.SerialDescriptor);
}

# Keep the serializer lookup for @Serializable classes via reflection
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers @kotlinx.serialization.Serializable class * {
    static ** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

-keep class kotlinx.serialization.** { *; }
-keep class kotlinx.serialization.internal.** { *; }
-keep class kotlinx.serialization.modules.** { *; }
-keep class kotlinx.datetime.** { *; }
-dontwarn kotlinx.serialization.**
-dontwarn kotlinx.serialization.internal.**
-dontwarn kotlinx.coroutines.**
-dontwarn kotlinx.datetime.**

# Kotlin metadata required by serialization reified generics
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata { public <methods>; }
-dontwarn kotlin.**
-dontwarn kotlin.reflect.jvm.internal.**
-keep class kotlin.reflect.** { *; }

# ============================================================================
# OkHttp / Okio
# ============================================================================
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn android.app.admin.**
-dontwarn org.slf4j.**

-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okhttp3.internal.** { *; }
-keep class okhttp3.internal.tls.** { *; }
-keep class okhttp3.internal.publicsuffix.PublicSuffixDatabase { *; }
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

-keep class okio.** { *; }
-keep interface okio.** { *; }
-keep class okio.internal.** { *; }

-keepclassmembers class * {
    @okhttp3.Headers$.* <methods>;
}

# Keep ApplicationInterceptor / NetworkInterceptor implementations
-keep class * implements okhttp3.Interceptor { *; }
-keep class * implements okhttp3.WebSocket$Listener { *; }
-keep class * implements okhttp3.EventListener { *; }

# ============================================================================
# Coroutines
# ============================================================================
-keepclassmembers class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keepclassmembers class kotlinx.coroutines.CoroutineExceptionHandler { *; }
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.debug.**

# ============================================================================
# App-specific model classes kept for JSON / DB round-tripping
# ============================================================================
-keep class com.cloakdroid.data.model.** { *; }
-keep class com.cloakdroid.data.db.entity.** { *; }
-keep class com.cloakdroid.data.db.dao.** { *; }
-keep class com.cloakdroid.network.** { *; }
-keep class com.cloakdroid.domain.** { *; }

# ============================================================================
