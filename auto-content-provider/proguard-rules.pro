-keepattributes *Annotation*
-keep, allowobfuscation @pl.selvin.android.autocontentprovider.annotation.Table public interface * {
          @pl.selvin.android.autocontentprovider.annotation.Column static <fields>;
          @pl.selvin.android.autocontentprovider.annotation.TableName static <fields>;
}