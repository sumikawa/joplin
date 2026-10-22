# このリポジトリの目的

- `sumikawa/joplin` は、Joplin を Samsung DeX 向けに最適化する個人利用のリポジトリです。作業と push には `dex` ブランチを使ってください。明示的な依頼なしに別のローカルブランチやリモートブランチを作成しないでください。
- 利用端末は **Galaxy ZFold 8 のみ**です。Android の **`arm64-v8a` 用 Release APK を1つだけ生成**してください。他の ABI、汎用 APK、AAB、iOS、デスクトップ向けバイナリの生成は、別途依頼がある場合に限ります。
- DeX のウィンドウ表示、サイズ変更、キーボード・マウス操作と、折りたたみ／展開時の表示を考慮してください。実機で未確認の動作を確認済みと報告しないでください。

# 作業時の方針

- 依頼された作業に必要なコード編集、依存関係の導入、検証、ビルドは、方針や実行の可否を逐一質問せずに進めてください。Git pull を依頼された場合、未コミット変更の一時退避と復元も含めて進めてください。ビルド成果物を依頼された場合は、以下の下書き Release での受け渡しまで追加確認なしで進めてください。
- この方針は新規リモートセッションにも適用します。ただし、実行環境の権限制約を回避したり、許可されていない操作を承認済みと扱ったりしないでください。環境が権限確認を要求する場合は、必要な範囲だけ申請し、その理由を説明してください。変更の破棄、履歴の強制更新、Release の公開などは、この通常作業の承認には含めません。
- 既存の変更を保持し、作業前にブランチと `git status` を確認してください。
- クラウドタスクは既に隔離されています。明示的な依頼がなければ Git worktree を追加しないでください。
- 依存関係はリポジトリ指定の Yarn と lockfile を使い、通常のインストールでは `--immutable` を指定してください。TLS、署名、チェックサム検証は維持してください。
- 認証情報や個人用の署名鍵をコード、ログ、コミットに含めないでください。

# Android Release ビルド

## 前提

- Node.js 24.22.1、Yarn 4.16.0。Node.js 22 系を使う場合も、2026年10月の脆弱性修正を含む 22.23.4 以上を使ってください。
- JDK 21 を推奨。React Native Gradle plugin 用の JDK 17 toolchain も必要です。
- Android SDK Platform 36、Build Tools 36.0.0、NDK 27.1.12297006。ネイティブ依存が追加で要求する NDK や CMake も準備してください。
- `ANDROID_HOME` と `JAVA_HOME` を設定し、チェックイン済み Gradle wrapper を使ってください。現在の構成は Gradle 9.8.1、AGP 9.2.1、Kotlin 2.4.10、React 19.2.3、React Native 0.86.3、Expo SDK 57 です。
- `.yarn/patches` の互換性修正を Yarn install で適用してください。詳細は `readme/dev/BUILD.md` を参照してください。

## コマンド

リポジトリのルートから実行します。現在のクラウド環境に `/workspace/.setup/android-env.sh` がある場合は、最初に `source /workspace/.setup/android-env.sh` を実行してください。この環境用ヘルパーは Git 管理外なので、新しい環境に存在するとは限りません。

```bash
yarn install --immutable --mode=skip-build
yarn tsc
yarn workspace @joplin/app-mobile build
cd packages/app-mobile/android
./gradlew :app:assembleRelease -PreactNativeArchitectures=arm64-v8a \
  -PJOPLIN_RELEASE_STORE_FILE=debug.keystore \
  -PJOPLIN_RELEASE_STORE_PASSWORD=android \
  -PJOPLIN_RELEASE_KEY_ALIAS=androiddebugkey \
  -PJOPLIN_RELEASE_KEY_PASSWORD=android \
  --no-daemon --max-workers=3 --console=plain
```

この署名設定は、リポジトリにある公開の Android Debug 鍵を利用する個人用テスト APK の設定です。正式な配布用署名ではありません。既存インストールの更新には署名鍵の一致が必要です。

生成物は `packages/app-mobile/android/app/build/outputs/apk/release/app-release.apk` です。APK を Git に追加せず、リポジトリ外の成果物ディレクトリ（この環境では `/workspace/artifacts`）に `joplin-<version>-arm64-release.apk` として保存してください。

## 検証

- ビルドの終了コードとログを確認し、署名を `apksigner verify --verbose`、16KB アラインメントを `zipalign -c -P 16 4` で検証してください。
- APK 内のネイティブライブラリが `arm64-v8a` のみであることを確認し、SHA-256 ファイルを生成してください。
- 変更に応じた既存の型チェック、lint、テストを実施してください。ビルド成功と実機動作確認は区別してください。

# GitHub 下書き Release での受け渡し

- ビルド成果物の受け渡し先は `sumikawa/joplin` の **下書き（draft）Release** とします。APK と SHA-256 ファイルを添付し、利用者がダウンロードできる実際の Release URL を案内してください。
- 下書き Release の作成と添付を通常の受け渡し手順とします。明示的な公開依頼がない限り、Release を公開しないでください。
- 説明には対象端末、ABI、アプリのバージョン、ビルド元コミット、署名の種類、検証結果、実機確認の有無を記載してください。未コミット変更を含むビルドの場合は、その事実を明記してください。
- 既存のタグ／Release を確認し、別の成果物を上書きしないでください。`gh release create` では `--draft` と `--target <ビルド元コミット>` を指定し、説明文は `--notes-file` で渡してください。
- GitHub API と添付のアップロードには `api.github.com`、`uploads.github.com` への通信が必要です。Android 依存の取得には `repo.reactnative.dev` も必要です。既存の許可ドメインとパッケージマネージャーのプリセットを保持してください。
- 設定画面での変更と実行中 Workspace の反映は別です。実際の接続を検証し、403 などで停止した場合は APK とログを保持して具体的な阻害要因を報告してください。Git の push 成功だけで Release API へのアクセスが可能と判断しないでください。
- ローカルファイルへのリンクを、クラウドからダウンロードできるリンクとして案内しないでください。
