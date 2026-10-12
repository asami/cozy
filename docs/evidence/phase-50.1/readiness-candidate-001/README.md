# Logical UI 完全候補のレビューガイド

この資料は PHASE-50.1 / S50.1.2 / E4 の人によるレビュー用です。
対象は `sales-order-target-ready-v1` の完全な Logical UI 候補であり、
HTML と画像はその投影・観察証拠です。2026-10-13 に帰属付きの実フィードバック
「修正不要」を受領し、ソース変更を要求しない処置を [feedback.md](feedback.md)
に記録しました。現在の完全候補を維持し、2026-10-13 に人による別個の明示的な最終受入を受領した実判断を下記に記録しています。

## 対象の同一性と進捗

| 項目 | 現在の値 |
| --- | --- |
| 完全候補 `candidateIdentity` | `sha256:dcb64b218d5921c65be0f1be7b1483565446dfd9fc3dbefd215c202e83f813b1` |
| 消費入力 `consumedInputIdentity` | `sha256:dd1f3b249795e3857bdcb19a72abc2cc205edee8e4d736f8f2db42a2dd08c0a1` |

[構造レビューの完全ソース receipt](evidence/readiness-review-receipt.json) と
[Web の完全ソース receipt](evidence/readiness-web-receipt.json) に、実際に消費した
`kernelContent`、`projectionContent`、`semanticContent`、`readinessContent`、
fixture・flow・coverage が保存されています。両 receipt の上記 binding が
今回の対象です。`acceptedIdentity`、`decisionIdentity`、`decision` はすべて
`null` のままです。

S50.1.1/E3 は手動ローカルコミット
`84ab85a0984a0286507d63aca906c9a88c923f9b` に記録済みです。
[Step commit checkpoint 002](../../../../.codex-workflow/phases/PHASE-50.1/p501-s5011-step-acceptance-commit-002.json)
は最新の独立 integrity focused review PASS と review ledger revision 5 を参照します。
recovery focused review / ledger revision 3 は以前の修復証拠として保持されています。
現在の comparison では provenance の空区切り行のスペースだけを除去する
書式整合性の変更があり、producer の 21 テストが成功しています。
run007 は操作の証拠として保持されていますが、この書式変更より前の実行であり、
現在の comparison bytes を新たにブラウザで観察した証拠ではありません。

## レビューの順序

1. [構造レビュー](site/review.html) で画面・操作・入力・状態・公開 binding・
   診断と出典を確認します。これは読み取り専用の構造表示です。
2. [操作シミュレーション](site/index.html) で SalesOrder の一覧 `order-list` から
   注文 `SO-001` を選び、詳細 `order-detail` に進みます。入力の初期値は
   `SO-001` です。確認選択 `awaiting-confirmation` を選び、confirm を実行します。
   draft/pending の loading と重複実行の無効化を確認し、別の fixture 操作
   `complete-confirmation` で normal の confirmed/succeeded 結果を表示します。
3. 必須入力を空または空白だけにして blur・実行時のエラーを確認します。
   識別子は大文字 ASCII 形式で、小文字や非 ASCII の入力はローカル検証に失敗します。
   入力・選択の保持、エラー文、関連付けられた説明を確認してください。
4. `fixture-outcome` の 7 選択肢を確認します。normal、loading、empty、
   validation-failed、conflict、unavailable、operation-failed が対象です。
   validation-failed と conflict は edit-input で入力に戻り、
   unavailable と operation-failed は入力・選択を保持して invoke を再試行します。
   empty は entry の再試行で一覧の初期状態へ戻ります。復旧後の loading と
   normal 完了まで確認します。この fixture に return-to-entry 復旧はありません。
5. `reset-fixture` で初期入力・一覧・draft/idle に戻り、注文と variant の選択、
   エラー・結果・復旧・pending が消えることを確認します。`replay-fixture` は
   同じ初期値から一覧→詳細→選択→loading→normal を決定的に再生します。
   再実行時の状態も確認してください。

## 画面幅・キーボード・証拠

[3 viewport の比較](site/compare.html) と
[比較画像](screenshots/responsive-comparison.png) を使い、各幅で上記の操作を確認します。
normal の実観察画像は以下です。

| profile | viewport | normal 画像 | 確認する表示 |
| --- | --- | --- | --- |
| compact | 390 × 844 | [compact](screenshots/compact-normal.png) | 1 つの active screen |
| medium | 834 × 1112 | [medium](screenshots/medium-normal.png) | navigation と active content |
| expanded | 1440 × 900 | [expanded](screenshots/expanded-normal.png) | 一覧と詳細、未選択時の詳細操作無効化 |

Tab で有効な操作へ到達できること、focus ring、入力ラベルとエラー説明、
loading の busy 表示と結果の通知を確認してください。入力に focus がある状態から
pointer でも recovery を実行し、キーボードによる復旧も確認します。
idle、入力変更・選択後、loading、各復旧可能な失敗で viewport を切り替え、
入力・注文・variant・outcome と復旧状態の保持、必要な内容の可視性・overflow、
隠れた操作から到達可能な対応操作への focus 移動を確認します。
境界幅 599/600/1023/1024 も既存の観察対象です。

[実際の browser review](browser-review.md) が run007 の 21 ケース、1067 観察、
23 画像と制限を説明しています。正規のケース行は
[specification section 11](../../../spec/logical-ui-model.md#11-shared-salesorder-compact-success-contract)
にあります。comparison の frame は縦スクロールでき、下部も各ケースの画像で
確認できます。

この Web は宣言済み Logical UI のシミュレーションです。実 Operation、backend、
domain 処理や Workflow の進行は実行しません。Workflow の `awaiting` は出典表示です。
ローカル検証は宣言された必須入力・識別子検証に限定され、server-authoritative な
制約の証明や device/framework/platform の受入にはなりません。

## 実フィードバックと今後の記入欄

実際のレビュー結果は [フィードバック記録](feedback.md) にあります。
所有会話の人間ユーザーによる「修正不要」を 2026-10-13 のクライアント日付で
帰属させ、意味ソースの改訂要求なしとして現在の完全候補を継続します。
これは明示的な最終受入の記録ではありません。以前の E3 実装改訂や
Executable Specification の改訂 fixture は履歴・能力の証拠であり、
今回の人間フィードバックによるソース改訂ではありません。

レビュー担当者の帰属と対象を明記し、項目ごとに以下を記入してください。
以下は今後のフィードバック用の未記入テンプレートです。
上記の実フィードバック記録とは別で、受入判断を記録したものではありません。

```text
レビュー担当者・日時:
candidateIdentity:
対象 screen / profile / outcome:
期待する挙動:
観察した挙動・再現手順・証拠:
要求する Logical UI ソース変更:
```

意味の変更は Logical UI 入力に反映し、両投影を再生成します。
改訂した identity と currentness、および影響する検証を確認した後、
1 つの完全な対象 identity に対して明示的な最終受入を行います。

## 別個の最終受入対象

別個の最終受入対象は、上記の完全な `candidateIdentity` と
`consumedInputIdentity`、および両完全ソース receipt の `sourceInputs` にある
`kernelContent`、`projectionContent`、`semanticContent`、`readinessContent`
の四つの権威入力役割です。Web receipt では `reviewReceipt` 内に同じ入力が
記録されています。生成 HTML や画像を権威にせず、この完全な target-neutral
Logical UI の完全宣言を Phase 51 入力とします。
「修正不要」から最終受入を推定しません。`acceptedIdentity`、
`decisionIdentity`、`decision` は `null` のままです。

同じ所有会話の人間ユーザーは、クライアント日付 2026-10-13 に別個に述べました。

> この候補を最終受入れする。Phase 50.1を最後まで続けて。コミットは引き続き親による手動ローカルコミットで。

[acceptance.json](../readiness-accepted-001/acceptance.json) がこの実判断を上記の正確な
完全 candidate/consumed identity と所有会話の reviewer 帰属に結び付けます。
個人名・正確な時刻は追加しません。候補 receipts の null は履歴として維持し、
[accepted package](../readiness-accepted-001/README.md) で同じ六つのソース同一性と
四つの完全宣言を実際の native accepted/decision/reviewer に結び付けました。
native 生成・currentness・決定性と二つの producer suite（21 test）が通りました。
accepted-binding run002 は normal/loading 六ケース・107 観察・親が確認した八画像で
PASS し、native/browser admission と owned cleanup も完了しました。
変更のない失敗・復旧・reset/replay・resize は run007 を引き続き参照します。
whole E4 Step review と親の手動ローカルコミット、完全 Phase review、
最終 full suite、distinct release/closure は保留です。
