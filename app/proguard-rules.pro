# Entidades Room y contrato IPC de AiHub / Conector IA
-keep class dev.cpsaul.noticias.data.local.** { *; }
-keep class dev.cpsaul.conectoria.** { *; }

# Preservar líneas y ficheros para desofuscar informes de ErrorLog con retrace (§5.4)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Evitar warnings de anotaciones opcionales de OkHttp / Jsoup
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
