# Phase 82: CAR電子署名・取得時破損検出・リポジトリ運用

Status: PLANNED
Plan date: 2026-10-09
Current status: OPEN
Closure authority: [Phase 82 checklist](phase-82-checklist.md)
Consumer: [CNCF Phase 104](../../../cloud-native-component-framework/docs/phase/phase-104.md)

## 目的

公開CARを取得する境界で外部SHAによる破損検出と、任意の電子署名による改竄検出・
提供者確認を行う。リポジトリが配布する提供者の公開鍵情報を、登録済みの信頼鍵から検証する。
成功した取得物だけをローカルキャッシュへ登録し、通常のローディング時の重複検証を避ける。

仕様は[CAR配布・取得仕様](../spec/car-distribution-verification.md)、
責務は[設計](../design/car-distribution-verification.md)に置く。
本Phaseは実装未着手。起動順・優先順位は未選択であり、現在のGoalやPhase 71.5の状態を変えない。

## 作業単位

| ID | 到達点 | 担当境界 | 状態 |
| --- | --- | --- | --- |
| P82-01 | 署名形式、SHA構文、鍵配布・更新・失効、旧公開物の移行とキャッシュ契約を確定する。 | Cozy/CNCF/リポジトリの共有契約 | OPEN |
| P82-02 | 公開用CAR全体のSHAと任意の署名、namespaceごとの署名付き公開鍵情報を配布できる。 | Cozyの公開処理・リポジトリ運用 | OPEN |
| P82-03 | 取得完了後に検証し、成功した同じCARだけを正式キャッシュへ登録できる。 | CNCFの取得・キャッシュ処理 | OPEN |
| P82-04 | 識別情報・runtime確認を保ち、ABIメタデータの責務とローディング時検証を整理する。 | CNCFの受け入れ処理・Cozyのメタデータ利用 | OPEN |
| P82-05 | リポジトリfixtureを使った往復取得、運用手順、レビューと必要な検証結果を記録する。 | 各担当の統合受け入れ | OPEN |

P82-01の決定をP82-02/03で共有し、P82-05で両者の実際の接続を確認する。
取得・キャッシュ・ローディングのconsumer作業は
[CNCF Phase 104](../../../cloud-native-component-framework/docs/phase/phase-104.md)へ引き継ぐ。
Cozy側の計画記載をCNCF側の実装・受け入れ完了とは扱わない。

## 受け入れ条件

- 新しい公開取得契約で、未署名CARはSHA一致で受け入れる。
- 署名付きCARはSHA一致と許可された鍵での署名検証成功が必要である。
- 破損・不正署名・信頼しない鍵・必要な鍵情報の検証失敗は正式キャッシュへ登録されない。
- 通信失敗や署名検証失敗が、未署名扱いへの切り替えにならない。
- 提供者鍵の登録・更新・失効と、リポジトリ初期信頼鍵の登録手順が実行可能である。
- 取得したCAR全体を検証するため、class以外の定義・画像の変更も署名検証の対象となる。
- キャッシュヒットでは取得時の全体検証を繰り返さず、差し替え・更新は新規取得として検証する。
- 識別情報・runtime範囲・安全なパス・必要な実行ファイル・I/O検証を維持する。
- ABIメタデータは生成・公開・利用でき、受け入れ時の必須ABI簡易検査を外しても利用経路が成立する。
- 公開物準備・取得・鍵更新を実際のExecutable Specificationと統合fixtureで証明する。

## 範囲外

CAR内部のSHA一覧の復活、独自classローダー、署名の一律必須化、
開発用CARの必須署名、生成物全体の保護・監視、展開後の継続的改竄検査は含めない。
SARや記事・動画・PDFへ自動的に適用範囲を広げない。
既存のPhase 71.5の除去作業や未完了義務は本Phaseへ移さない。

## 再開位置

P82-01の契約確定から開始する。まず公開処理・CNCFの実際の取得キャッシュ経路・
鍵情報の配布先を調査し、署名方式と既存リポジトリへの移行を選定する。
選択したconsumerと変更範囲を明確にしてからspec/designとExecutable Specificationを更新する。
CozyとCNCFに計画文書を作成した。外部リポジトリのソース編集、SBT、鍵生成、公開、
配備、commit、pushは実施していない。

## 参照

- [検討メモ](../notes/car-distribution-verification.md)
- [採用記録](../journal/2026/10/2026-10-09-car-distribution-verification-plan.md)
- [既存の公開処理設計](../design/component-repository-publication.md)
- [既存のABI設計](../design/car-abi-compatibility.md)
