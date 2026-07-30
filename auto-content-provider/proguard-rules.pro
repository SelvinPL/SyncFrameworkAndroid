-keepattributes *Annotation*, InnerClasses, EnclosingMethod
-keep, allowobfuscation @pl.selvin.android.autocontentprovider.annotation.Table public interface * {
          @pl.selvin.android.autocontentprovider.annotation.Column static <fields>;
          @pl.selvin.android.autocontentprovider.annotation.TableName static <fields>;
}
-if   @pl.selvin.android.autocontentprovider.annotation.Table interface **$*
-keep, allowobfuscation interface <1>
-dontwarn pl.selvin.android.syncframework.**