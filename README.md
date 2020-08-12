# jena-sparql-server-aws-serverless

SPARQLエンドポイントをAWSサーバーレス環境下に作成するためのプロジェクトです。
AWSの以下のサービスを利用します。

- [AWS API Gateway](https://aws.amazon.com/api-gateway/)
- [AWS Lambda](https://aws.amazon.com/lambda/)

RDF Storeに[Apache Jena](https://github.com/beautifulinteractions/node-quadstore)を利用しています。

データセットとして、RDFファイルとTDBが使用可能です。

以下の作業を行うには、Java SE 11とGradleを利用できるようにしておいてください。

## RDF Storeの準備

あらかじめ RDFファイルを使って TDBを作成します。
RDFファイルを直接扱うことも可能ですが、TDBのほうが検索は早いのでデータ変換をお勧めします。
TDBへの変換は Apache Jena TDBの[コマンドラインツール](https://jena.apache.org/documentation/tdb/commands.html)を使います。

```
tdbloader --loc ./sparql-db dataset.ttl
```

このTDBをZIP圧縮します。
```
zip -r sparql-db.zip ./sparql-db
```

ZIP圧縮したファイルを`JenaServerFunction/static/`ディレクトリにコピーしてください。

このファイルを使用できるようにするために`JenaServerFunction/src/main/java/jenaserver/App.js`を以下のように変更してください。

```
...
public class App implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

  // TDBファイル(Zip圧縮)
  private final String DatasetType = "tdb";
  private final String DatasetPath = "sparql-db/"; // TDBのディレクトリ名
  private final String DatasetFile = "sparql-db.zip"; // ZIPファイル名
...
```

サンプルとして 国立国会図書館が公開する[「図書館及び関連組織のための国際標準識別子（ISIL）」試行版LOD](https://www.ndl.go.jp/jp/dlib/standards/opendataset/index.html)のRDFデータを変換したTDBのZIP圧縮データを格納しています。

```
JenaServerFunction/static/isilloddb1.zip
```

## デプロイ方法

デプロイには、[AWS SAM](https://aws.amazon.com/serverless/sam/)を利用します。
[AWS SAM CLI](https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/serverless-sam-cli-install.html)をあらかじめインストール、設定しておいてください。

```
cd jena-sparql-server-aws-serverless 
sam build
sam deploy --guided ※ 二回目以降は sam deploy でデプロイできます。
```

デプロイ後に表示される`JenaServerApi`のURLが実際のエンドポイントとなります。

## ローカルでの動作確認

以下のコマンドにより、ローカル環境で実行が可能です。実行には、Dockerが必要です。

```
cd jena-sparql-server-aws-serverless
sam local invoke --event events/event.json
```

これで以下のSPARQLクエリが実行されます。
```
select (count(distinct *) as ?count) 
where {
  ?s ?p ?o
}
```

AWS SAM CLI のその他の利用については以下を参照してください。

<https://docs.aws.amazon.com/ja_jp/serverless-application-model/latest/developerguide/serverless-getting-started-hello-world.html>
