# fakeLocationProviderBLE

Android デバイスの位置情報を変更します。

Bluetooth を使用して、外部の端末(PCなど) から位置情報を送信します。

位置情報の送信側はデスクトップアプリケーションやAndroidアプリケーションでも可能ですが、ここでは Web Bluetooth API を使用しています。


## 使用しているAPI

Android
* Google Play Services Location API
* LocationManager
* Bluetooth Low Energy

PC
* Google Maps API
* Web Bluetooth API


## 使い方

### Android
Androidデバイスの開発者モードを有効にします。

apk をビルドしてインストールします。

apk を起動します。以下のパーミッション等を設定します。
* 位置情報を使用する許可をします。
* 開発者向けオプションから、"仮の現在地情報アプリを選択"にこのアプリを指定します。

Google Map アプリなどを起動しておきます。


### PC
html/index.html を Chromeブラウザで表示します。

接続を押します。

該当する Android デバイスを選択します。

マップをダブルクリックすると、クリックした位置の位置情報を Android デバイスへ送信します。



