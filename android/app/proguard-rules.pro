# JGit はサービスローダ(META-INF/services)とリフレクションを使う箇所がある。
# v1 は release で minify 無効だが、有効化に備えて最低限の keep を置いておく。
-keep class org.eclipse.jgit.** { *; }
-dontwarn org.eclipse.jgit.**
-dontwarn org.slf4j.**
-dontwarn javax.servlet.**
-dontwarn org.apache.**
