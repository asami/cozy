# 最終受入済み完全 Logical UI の投影パッケージ

PHASE-50.1 / S50.1.2 / E4 / S50.1.2.3 のパッケージです。
所有会話 `01a0ff01-4772-76b2-bba1-e2542f0a9647` の人間ユーザーは、
クライアント日付 2026-10-13 に次の独立した最終受入メッセージを述べました。
個人名や正確なメッセージ時刻は記録しません。

> この候補を最終受入れする。Phase 50.1を最後まで続けて。コミットは引き続き親による手動ローカルコミットで。

[acceptance.json](acceptance.json) は実際の人間判断と帰属を保持する業務入力です。
親の [受入解決記録](../../../../.codex-workflow/phases/PHASE-50.1/p501-e4-final-acceptance-resolution-001.json)
に対応し、先の「修正不要」という [フィードバック](../readiness-candidate-001/feedback.md)
とは別個の証拠です。意味ソース変更は要求されておらず、
[候補レビューの操作ガイド](../readiness-candidate-001/README.md) の完全入力を維持します。

| 受入対象 | 正確な同一性 |
| --- | --- |
| `candidateIdentity` | `sha256:dcb64b218d5921c65be0f1be7b1483565446dfd9fc3dbefd215c202e83f813b1` |
| `consumedInputIdentity` | `sha256:dd1f3b249795e3857bdcb19a72abc2cc205edee8e4d736f8f2db42a2dd08c0a1` |
| `decisionId` | `P501-E4-FINAL-ACCEPTANCE-001` |
| `reviewerId` | `owning-chat-human:01a0ff01-4772-76b2-bba1-e2542f0a9647` |

## 権威入力と生成境界

Phase 51 の意味入力は、native 構造 receipt からそのまま出力する
[accepted-logical-ui.json](accepted-logical-ui.json) の `binding` と `sourceInputs`
です。四つの権威入力役割は `kernelContent`、`projectionContent`、
`semanticContent`、`readinessContent` です。kernel candidate/input、projection、
semantic projection も候補の六つの完全ソース同一性を保持します。
実際の `acceptedIdentity` と `decisionIdentity` は native export/receipts から読み取り、
ここで計算・仮定しません。native factory は入力関係を検証し、人間の同意は
上記の実メッセージに帰属します。

生成済み成果物は [app](site/index.html)、[構造レビュー](site/review.html)、
[3 viewport 比較](site/compare.html)、[構造 receipt](evidence/readiness-review-receipt.json)、
[Web receipt](evidence/readiness-web-receipt.json) です。両投影は同じ完全入力と
native 受入・decision・reviewer 値を結び付けます。HTML、画像、receipt は投影・
観察証拠であり、生成 Web ソースを Phase 51 の意味権威にはしません。
構造レビューは全 21 宣言ケースを表示し、比較は同じ app の独立した三つの frame です。

上記 export、三 HTML、二 receipt と、実観察 JSON・八画像を実際に生成し、
親が検査・admission・promotion しました。native 生成・currentness・決定性と
二つの producer suite（21 test）が PASS です。accepted-binding run002 は
Chrome 154.0.8037.94 / Playwright 1.62.1 で六ケース・107 観察が PASS し、
親が全八画像を確認しました。外部要求・変更要求・browser error はすべて 0、
owned Chrome/context/profile/server cleanup は完了しています。
[実 native admission](../../../../.codex-workflow/phases/PHASE-50.1/p501-e4-accepted-native-admission-001.json)
と [実 browser admission](../../../../.codex-workflow/phases/PHASE-50.1/p501-e4-accepted-browser-admission-001.json)
に選択した入力・実行・範囲を記録しています。開始前の依頼001は uv PATH の
canonical 要件で停止し、実ブラウザーを起動していません。実行証拠は修正した依頼002です。

| native 受入結果 | 実 export の同一性 |
| --- | --- |
| `acceptedIdentity` | `sha256:07ba4c050dd4d393ab893d53e921a5f6b24f09b6ff5f8d552d14597acf275210` |
| `decisionIdentity` | `sha256:4be0685d7bbe310e648589fe385503d117d19ebed5cb2e23b095ec9747f2af39` |

全21ケースの失敗・復旧・reset/replay・resize は既存 E3 run007 の証拠を参照します。
whole E4 Step review・親の手動ローカルコミット・full Phase review・最終 full suite・
distinct release/59項目 closure は引き続き保留です。

## 再現手順と限定 browser 証明

[RenderAcceptedReadiness.scala](../../../../scripts/test/phase501/RenderAcceptedReadiness.scala)
は既存 `readyCandidate()` / `readinessWebScenario()` を再構成する薄い Test-classpath
adapter です。正確な candidate/consumed identity の確認、`verifyCurrent`、native
`accept`、両 `renderAccepted` / `verifyAcceptedCurrent`、両 receipt の完全一致と
Web 再生成の決定性を確認してから、既存の `Web.write` で五ファイルを出力します。
新しい CLI、意味 codec、受入取得機能は追加しません。Cozy root で再現する
論理 SBT 引数は次のとおりです。親が登録 runner と共有直列 wrapper を使用します。

```text
--batch
set Test / unmanagedSources += baseDirectory.value / "scripts/test/phase501/RenderAcceptedReadiness.scala"
testOnly cozy.ui.CozyLogicalUiReadinessReviewSpec cozy.ui.CozyLogicalUiReadinessWebSpec
Test / runMain cozy.ui.RenderAcceptedReadiness /Users/asami/src/dev2026/cozy/docs/evidence/phase-50.1/readiness-accepted-001/acceptance.json /Users/asami/src/dev2026/cozy/target/phase501-accepted-preview
```

同じ [browser driver](../../../../scripts/test/phase501/run-readiness-browser.mjs) の既存の
五つの絶対パス指定 `--site`、`--receipt`、`--evidence`、`--playwright`、`--browser`
に `--accepted-input` と生成された export の絶対パスを追加します。
元の五指定は candidate-full / 21 ケースのままです。accepted-binding は
normal/loading の六ケースだけを実観察し、他の 15 ケースを再観察したとは
扱いません。`proofMode` と `proofScope` がこの範囲を明示します。

| profile | viewport | 実 loading 画像 | 実 normal 画像 |
| --- | --- | --- | --- |
| compact | 390 × 844 | [loading](screenshots/compact-loading.png) | [normal](screenshots/compact-normal.png) |
| medium | 834 × 1112 | [loading](screenshots/medium-loading.png) | [normal](screenshots/medium-normal.png) |
| expanded | 1440 × 900 | [loading](screenshots/expanded-loading.png) | [normal](screenshots/expanded-normal.png) |

さらに [構造レビュー画像](screenshots/structural-review.png) と
[完了済み三 frame の比較画像](screenshots/responsive-comparison.png)、
[portable observations](browser-observations.json) を実際に生成・確認しました。
完全 embedded receipt、実 keyboard navigation、入力・variant・focus、loading、
重複 invoke 無効化と normal 完了を確認します。各 viewport で native の
`Complete source provenance` summary を開き、可視テキスト・geometry・parsed provenance
から accepted/decision/reviewer を確認し、document overflow を拒否します。
構造 accepted 表示と比較 shell/独立 frame の同じ入力・帰属も確認します。
GET-only loopback、外部・変更要求拒否、owned Chrome/profile/server cleanup は維持します。

変更のない失敗・復旧・reset/replay・resize は
[E3 run007](../readiness-candidate-001/browser-review.md) の 21 ケース、1067 観察、23 画像、
12 pointer recovery、五つの focused-recovery resize walk を参照します。
実 Operation/backend/domain/Workflow 進行、device/platform の受入は対象外です。
Phase は IN_PROGRESS です。whole E4 Step review と親の手動ローカルコミット、
full Phase review、最終 full suite、distinct release/59 項目の closure は保留です。
