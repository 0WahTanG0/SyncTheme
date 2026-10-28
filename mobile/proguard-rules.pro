# R8 для SyncTheme — только удаление неиспользуемого кода, без переименования и оптимизаций.
#
# Ужимается то, у чего правильные правила R8 и нет рефлексии по именам: Compose и AndroidX,
# Kotlin и kotlinx, код самого приложения, а также мусор, который валидатор циферблатов тянет
# с собой (затенённый AutoValue, аннотации Checker Framework, ICU — до него наши циферблаты не доходят).
#
# Всё остальное остаётся целиком: валидатор циферблатов (Xerces, ICU, Eclipse WST, aapt, apksig,
# protobuf, Guava, Gson, Glide), ADB по Wi‑Fi (libadb, Conscrypt, BouncyCastle), ML Kit и сервисы
# Google Play — они находят классы по именам и через ServiceLoader.

-dontobfuscate
-dontoptimize
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions,SourceFile,LineNumberTable

-keep class !androidx.**,!kotlin.**,!kotlinx.**,!app.synctheme.**,!autovalue.shaded.**,!org.checkerframework.**,!com.ibm.icu.**,** { *; }

# Классы, недоступные на Android (их не было и без R8), — не повод останавливать сборку.
-dontwarn **
