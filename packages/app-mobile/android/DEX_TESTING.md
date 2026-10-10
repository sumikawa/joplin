# Android DeXの検証

通常のAPKは、SamsungのDeX設定値、Samsung端末のデスクトップUIモード、またはSamsung端末の外部ディスプレイ上のActivityを判定する。React Nativeの新アーキテクチャで使う`reactHost.currentReactContext`へ構成変更を通知し、Activity復帰時にも再判定する。幅の判定には、外部表示のリサイズ後に古い値を返すことがあるReact Nativeのグローバル`Dimensions`を使わず、Activityの`Configuration.screenWidthDp`を通知する。

## エミュレータ

`dexTest`は通常版と別のアプリIDでインストールする検証用ビルド。Samsung OSの代わりに、外部表示の判定入力を模擬する。通常のProfileable/Release APKでは模擬入力を受け付けない。Samsung実機では検証用ビルドでも模擬入力を受け付けない。

Android 16 Google APIs ARM64のAVD `Joplin_DeX_API36`で確認する。内蔵表示は800dp、追加する外部表示は1000dpとする。次のコマンドをAndroidプロジェクトのディレクトリで実行する。このAVDで追加されたAndroidのdisplay IDは`2`だった。別のAVDでは`get-displays`の結果に合わせる。

```sh
./gradlew :app:assembleDexTest -PreactNativeArchitectures=arm64-v8a
adb -s emulator-5556 install -r app/build/outputs/apk/dexTest/app-dexTest.apk
adb -s emulator-5556 emu multidisplay add 1 1000 1080 160 0
adb -s emulator-5556 shell cmd display get-displays -i
adb -s emulator-5556 shell am start -W --display 2 -n net.cozic.joplin.dextest/net.cozic.joplin.MainActivity --ez joplin.dex.testExternalDisplay true
```

ノートを選び、サイドバー・ノート一覧・Markdownエディタ・プレビューが横に並ぶことを確認する。本文に見出しや太字を入力し、右側に描画されることを確認する。

```sh
adb -s emulator-5556 emu multidisplay add 1 1920 1080 160 0
adb -s emulator-5556 shell am start -W --display 0 -n net.cozic.joplin.dextest/net.cozic.joplin.MainActivity --ez joplin.dex.testExternalDisplay true
adb -s emulator-5556 shell am start -W --display 0 -n net.cozic.joplin.dextest/net.cozic.joplin.MainActivity --ez joplin.dex.testExternalDisplay false
adb -s emulator-5556 logcat -d -s JoplinDex
```

1000dpから1920dpへリサイズするとサイドバーが180dpから250dp、ノート一覧が220dpから300dpへ広がることを確認する。800dpの内蔵表示ではDeXの模擬入力があっても単一画面になり、通常表示に戻っても本文が残ることを確認する。`emulatedExternalWindow=true`は模擬入力の記録であり、Samsung実機の検知結果ではない。

本文を入力した直後にも表示先を移動し、移動先に最新の入力が表示されることを確認する。サイドメニューとナビゲーションはモバイル・DeX間で同じReactツリーを使い、ノート画面を再作成しない。

このMacではエミュレータ37.1.11のウィンドウ描画が停止したため、検証時には次の起動方法を使った。画面は`adb exec-out screencap -p`で取得できる。

```sh
emulator -avd Joplin_DeX_API36 -port 5556 -no-snapshot -no-audio -no-window -gpu swiftshader
```

## Samsung実機

Profileable APKを使い、通常表示とDeX表示の両方で`JoplinDex`ログと実画面を確認する。DeX外部表示では`externalWindow=true`になることを確認する。ログはモードと画面情報だけを含み、ノート本文を含まない。

起動・リサイズ・最小化からの復帰、マウスとキーボード、本文と選択の保持を実機で確認する。共通の手順はZ Fold8プロジェクトの`dex.md`を参照する。
