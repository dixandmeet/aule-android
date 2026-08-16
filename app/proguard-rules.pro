# Règles de rétrécissement pour les builds release.
#
# Elles s'ajoutent à proguard-android-optimize.txt. Chaque règle doit dire
# pourquoi elle existe : une règle « -keep class ** » sans raison annule le
# bénéfice du rétrécissement et masque les vrais problèmes.

# kotlinx.serialization génère des sérialiseurs référencés par réflexion depuis
# les compagnons. Sans cela, un DTO se décode en debug et lève en release.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class io.aule.android.**$$serializer { *; }
-keepclassmembers class io.aule.android.** {
    *** Companion;
}
-keepclasseswithmembers class io.aule.android.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Les traces de pile d'un plantage en production doivent rester lisibles une fois
# désobfusquées avec le mapping.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
