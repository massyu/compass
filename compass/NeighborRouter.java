package org.iota.compass;

import java.util.List;
import java.util.Map;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.channels.ServerSocketChannel;

public class NeighborRouter {
    //private final AtomicBoolean shutdown = new AtomicBoolean(false);
    //private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "Neighbor Router"));

    //private Selector selector;
    private ServerSocketChannel channel;
    
    public void start() {
        route();
        //executor.execute(this::route);
    }

    public void route() {
        log.info("ポート解放&受信待機");

        // run selector loop
        try {
            //selector = Selector.open();

            channel = ServerSocketChannel.open();
        
            //受信ポートを指定
            channel.socket().bind(new InetSocketAddress(10007));
            
            //接続待機
            SocketChannel sc = channel.accept();
            
            //バッファデータ(バイト配列)を作成（今回は4バイトのint型のみをテスト）
            ByteBuffer bb = ByteBuffer.allocate(4);
            
            //バッファ(バイト配列)に受信データを読み込み
            sc.read(bb);
            
            //ソケットチャンネルクローズ
            sc.close();
            
            //サーバーチャンネルクローズ
            channel.close();
            
            //intデータを受信データの0バイト目から読み込み
            System.out.println("受信:"+bb.getInt(0));

        }catch(IOException e){
            e.printStackTrace();
		}
    }

    /*
    public void shutdown() {
        shutdown.set(true);
        executor.shutdownNow();
    }
    */
}
