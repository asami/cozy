# Document Project外部作業領域のGit管理方針

Date: 2026-11-15
Disposition: ADOPTED / Phase 90 PLANNED

利用者は、SmartDox Document Projectの中間ファイルをProject内に置かずDropboxの余分な同期を避ける計画について、Gitでも余分なファイルを対象外にすることを要求しました。

[Phase 90](../../../phase/phase-90.md)、[checklist](../../../phase/phase-90-checklist.md)、[機能案](../../../notes/document-project-private-package.md)にGitの要件を追加しました。原稿・永続設定・必要な画像は追跡可能に保ち、中間生成物・ログ・キャッシュ・一時的な実行状態を外部WorkRootへ分離します。WorkRootがGit作業ツリーに含まれる場合の限定したignore設定はP90-01で定義します。完成物の管理はプロジェクトの選択に従い、既存ignoreと追跡済みファイルを保持します。

Dropbox対策は生成中も同期領域に中間物を書かないことで実現し、Gitのignore設定で代用しません。正常・失敗・中断時に中間物がGitの追加候補へ混入せず、入力資源が追跡可能であることを受入項目に追加しました。

今回は計画文書の更新のみです。Phase 90は未着手で全項目OPENです。実装、実際のignore設定変更、既存ファイルの削除・追跡解除、commitは行っていません。
