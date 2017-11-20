-keepattributes *Annotation*
-keep, allowobfuscation @pl.selvin.android.autocontentprovider.annotation.Table public interface * {
          @pl.selvin.android.autocontentprovider.annotation.Column static <fields>;
          @pl.selvin.android.autocontentprovider.annotation.TableName static <fields>;
}
-keepnames class pl.selvin.android.listsyncsample.ui.** extends android.support.v4.app.Fragment
-keep class pl.selvin.android.listsyncsample.ui.** extends pl.selvin.android.listsyncsample.app.ListFragmentCommon
-dontwarn okio.**
-dontnote okhttp3.**
-dontnote android.net.**
-dontnote org.apache.**
-dontnote pl.selvin.android.syncframework.content.**
-keep class net.sqlcipher.** { *; }
-keep public class net.sqlcipher.database.** { *; }