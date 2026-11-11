# 内部資料パッケージと同期対象外の生成領域の開発計画

Date: 2026-11-11
Disposition: ADOPTED / Phase 90 PLANNED

Live Prototyping資料は公開technical documentではなく内部資料とし、完成版をDropbox/@/1111.takumi/のトップ、Document Projectをsource/に置く方針となりました。利用者は、この形式で継続編集・生成できるCozy機能と、Dropbox内に一時ファイルを作らない運用の開発計画を要求しました。

[機能案](../../../notes/document-project-private-package.md)、[Phase 90](../../../phase/phase-90.md)、[checklist](../../../phase/phase-90-checklist.md)に記録しました。原稿・設定・図・完成物のみを案件フォルダに保持し、生成・確認HTML・ログ・キャッシュは同期対象外の外部作業ルート配下target/で扱います。公開用exportを原稿移設に転用せず、既存生成経路を利用します。

今回の作業は計画文書の追加です。機能実装、実際のDropboxへの配置、元原稿の削除、公開やcommitは実行していません。既存Phaseの優先順位も変更していません。
