/*
 * This file is part of TestnetCOO.
 *
 * Copyright (C) 2018 IOTA Stiftung
 * TestnetCOO is Copyright (C) 2017-2018 IOTA Stiftung
 *
 * TestnetCOO is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * TestnetCOO is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with TestnetCOO.  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     IOTA Stiftung <contact@iota.org>
 *     https://www.iota.org/
 */

package org.iota.compass;

import com.beust.jcommander.JCommander;

import com.beust.jcommander.ParameterException;
import org.iota.jota.pow.SpongeFactory;
import org.iota.compass.conf.CoordinatorConfiguration;
import org.iota.compass.conf.CoordinatorState;
import org.iota.compass.exceptions.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.net.URL;
import java.rmi.Remote;
import java.security.Security;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.iota.jota.IotaAPI;
import org.iota.jota.dto.response.CheckConsistencyResponse;
import org.iota.jota.dto.response.GetNodeInfoResponse;
import org.iota.jota.dto.response.GetTransactionsToApproveResponse;
import org.iota.jota.error.ArgumentException;
import org.iota.jota.model.Transaction;

//変更箇所
import java.util.List;
import java.util.Map;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.channels.ServerSocketChannel;

import java.io.*;	//InputStreamReaderやBufferedReaderを使えるようにする宣言
import java.net.*;	//InetAddressやSoket利用のため
//import org.iota.compass.NeighborRouter; //変更箇所
import java.nio.charset.Charset;


public class Coordinator {
  //public final NeighborRouter neighborRouter; // 変更箇所

  private static final Logger log = LoggerFactory.getLogger(Coordinator.class);
  private final MilestoneSource db;
  private final IotaAPI api;
  private final CoordinatorConfiguration config;
  private CoordinatorState state;
  private List<IotaAPI> validatorAPIs;
  private Thread workerThread;
  private boolean shutdown;
  
  private long milestoneTick;
  private int depth;

  private ServerSocketChannel channel; //変更箇所
  //private SocketChannel sc;
  //private ByteBuffer bb;

  private InputStreamReader is;
  private BufferedReader br;

  private ServerSocket serverSock; 
  private Socket clientSock;
  private InputStream sok_in;
  private InputStreamReader sok_is;
  private BufferedReader sok_br;
  private OutputStream os;

  private Coordinator(CoordinatorConfiguration config, CoordinatorState state, SignatureSource signatureSource) throws IOException {
    this.config = config;
    this.state = state;
    this.shutdown = false;
    URL node = new URL(config.host);

    this.db = new MilestoneDatabase(config.powMode,
        config.powHost,
        signatureSource,
        config.layersPath);
    this.api = new IotaAPI.Builder()
        .protocol(node.getProtocol())
        .host(node.getHost())
        .port(node.getPort())
        .build();

    validatorAPIs = config.validators.stream().map(url -> {
      URI uri = URI.create(url);
      return new IotaAPI.Builder().protocol(uri.getScheme())
          .host(uri.getHost())
          .port(uri.getPort())
          .build();
    }).collect(Collectors.toList());
  }

  //変更箇所 state.latestMilestoneIndex→434525
  private static CoordinatorState loadState(String path) throws IOException, ClassNotFoundException {
    CoordinatorState state;
    try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(path))) {
      state = (CoordinatorState) ois.readObject();
      log.info("loaded index {}", state.latestMilestoneIndex);
    }
    return state;
  }

  private void storeState(CoordinatorState state, String path) throws IOException {
    try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(path))) {
      oos.writeObject(state);
      log.info("stored index {}", state.latestMilestoneIndex);
    }
  }

  private void shutdownHook() {
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      log.info("Shutting down Compass after next milestone...");
      this.shutdown = true;
      try {
        this.workerThread.join();
      } catch (InterruptedException e) {
        String msg = "Interrupted while waiting for Compass to issue next milestone.";
        log.error(msg, e);
      }
    }, "Shutdown Hook"));
  }

  //変更箇所
  private void openConnect() {

    int ECHO_PORT = 14270;
    ServerSocketChannel serverChannel = null;
    try {
        serverChannel = ServerSocketChannel.open();
        serverChannel.socket().bind(new InetSocketAddress(ECHO_PORT));
        System.out.println("ChannelEchoServerが起動しました(port=" + serverChannel.socket().getLocalPort() + ")");
        while (true) {
            SocketChannel channel = serverChannel.accept();
            System.out.println(channel.socket().getRemoteSocketAddress() + ":[接続されました]");
            new Thread(new ChannelEchoThread(channel)).start();
        }
    } catch (IOException e) {
        //e.printStackTrace();
    } finally {
        if (serverChannel != null && serverChannel.isOpen()) {
            try {
                System.out.println("ChannelEchoServerを停止します。");
                serverChannel.close();
            } catch (IOException e) {}
        }
    }
/*
    int portNumber = 14270;
    ServerSocket sSocket = null;
  	Socket socket = null;
	  BufferedReader reader = null;
	  PrintWriter writer = null;

    Thread thread = new Thread(() -> {
        try {
              sSocket = new ServerSocket();
              sSocket.bind(new InetSocketAddress("192.168.1.72",14270));
              System.out.println("クライアントからの入力待ち状態");
              socket = sSocket.accept();
              reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
              writer = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    });
    thread.start();

    try (ServerSocket serverSocket = new ServerSocket(portNumber);) {
      sSocket = new ServerSocket();
  		sSocket.bind(new InetSocketAddress("192.168.1.72",14270));
      System.out.println("クライアントからの入力待ち状態");
      socket = sSocket.accept();
  		reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
      writer = new PrintWriter(socket.getOutputStream(), true);

      //while (true) {
          try (
              Socket clientSocket = serverSocket.accept();
              PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
              BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
              ) {
              new EchoThread(clientSocket).start();
          } catch (Exception e){
              System.out.printf("%d\n", e);
          }
      //}
    } catch (Exception e){
      System.out.printf("%d\n", e);
    }
*/

    /*
      log.info("ポート解放&受信待機");
      byte crlf [] = {13,10};//キャリッジリターン(CR),改行(LF)の並び で、送信時の区切り用
      // run selector loop
      try {
          //selector = Selector.open();

          //channel = ServerSocketChannel.open();
      
          //受信ポートを指定
          //channel.socket().bind(new InetSocketAddress(14270));
          
          //接続待機
          //sc = channel.accept();
          log.info("動いてる１");

          serverSock = new ServerSocket(14270);          
          clientSock = serverSock.accept();
          serverSock.close();

          sok_in = clientSock.getInputStream();
			    sok_is = new InputStreamReader(sok_in);
			    sok_br = new BufferedReader(sok_is);

          log.info("動いてる２");
          os = clientSock.getOutputStream();

          while(true){ 
            log.info("動いてる3");
            String receive =  sok_br.readLine();//受信データ取得
            log.info(receive);
            receive =  sok_br.readLine();//受信データ取得
            log.info(receive);
            log.info("送信文字列>>");
            String send = br.readLine();	//キー1行入力
            os.write(send.getBytes());//送信
            os.write(crlf);
          }

          //バッファデータ(バイト配列)を作成（今回は4バイトのint型のみをテスト）
          //bb = ByteBuffer.allocate(4);
          
          //バッファ(バイト配列)に受信データを読み込み
          //sc.read(bb);
          
          //ソケットチャンネルクローズ
          //sc.close();
          
          //サーバーチャンネルクローズ
          //channel.close();
          
          //intデータを受信データの0バイト目から読み込み
          //System.out.println("受信:"+bb.getInt(0));

      }catch(IOException e){
          e.printStackTrace();
      }
  		try{System.in.read();}catch(Exception e){}
      */
  }

  class ChannelEchoThread implements Runnable {
  
      int BUF_SIZE = 1000;
      SocketChannel channel = null;
  
      public ChannelEchoThread(SocketChannel channel) {
          this.channel = channel;
      }
  
      public void run() {
          ByteBuffer buf = ByteBuffer.allocate(BUF_SIZE);
          Charset charset = Charset.forName("UTF-8");
          String remoteAddress = channel.socket()
                  .getRemoteSocketAddress()
                  .toString();
          try {
              if (channel.read(buf) < 0) {
                  System.out.println("run6"); 
                  return;
              }
              System.out.println("run5"); 
              // buf.flip();
              System.out.println("run7"); 
              String input = charset.decode(buf).toString();
              System.out.println("run8"); 
              // buf.flip();
              System.out.println("run9"); 
              channel.write(buf);
          } catch (IOException e) {
              e.printStackTrace();
              return;
          } finally {
              System.out.println("shutdowned");
              if (channel != null && channel.isOpen()) {
                  try {
                      channel.close();
                  } catch (IOException e) {}
              }
          }
      }
  
  }

/*
  class EchoThread extends Thread {

    private Socket socket;

    public EchoThread(Socket socket) {
        this.socket = socket;
        log.info("%s [%s] :Accepted!\n", "1010", Thread.currentThread().getName());
    }

    @Override
    public void run() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        log.info("%s [%s] :Process finished.\n", "1010", Thread.currentThread().getName());
    }
  }
  */


  public static void main(String[] args) throws Exception {

    // Limit DNS caching for resolved and failed records to 5 seconds
    Security.setProperty("networkaddress.cache.ttl" , RemoteSignatureSource.DEFAULT_CACHE_TTL);
    Security.setProperty("networkaddress.cache.negative.ttl" , RemoteSignatureSource.DEFAULT_CACHE_TTL);

    CoordinatorConfiguration config = new CoordinatorConfiguration();
    CoordinatorState state;

    JCommander.newBuilder()
        .addObject(config)
        .acceptUnknownOptions(true)
        .build()
        .parse(args);

    if (config.powHost != null && config.powMode != SpongeFactory.Mode.CURLP81) {
      throw new ParameterException("Remote PoW only supports CURLP81.");
    }

    // We want an empty state if bootstrapping
      // and to allow overriding state file using `-index` flag
    if (config.bootstrap || config.index != null) {
      state = new CoordinatorState();
      state.latestMilestoneIndex = config.index == null ? 0 : config.index;
    } else {
      try {
        state = loadState(config.statePath);
      } catch (Exception e) {
        String msg = "Error loading Compass state file '" + config.statePath + "'! State file required if not bootstrapping...";

        log.error(msg, e);
        throw new RuntimeException(e);
      }
    }
    
    Coordinator coo = new Coordinator(config, state, SignatureSourceHelper.signatureSourceFromArgs(config.signatureSource, args));
    coo.openConnect();  //変更箇所
    coo.setup();
    coo.start();
  }

  /**
   * Checks that node is solid, bootstrapped and on latest milestone.
   *
   * @param nodeInfo response from node API call
   * @return true if node is solid
   */
  private boolean nodeIsSolid(GetNodeInfoResponse nodeInfo) {
    if (nodeInfo.getLatestMilestone().equals(MilestoneSource.EMPTY_HASH) ||
            nodeInfo.getLatestSolidSubtangleMilestone().equals(MilestoneSource.EMPTY_HASH))
      return false;

    return nodeInfo.getLatestSolidSubtangleMilestoneIndex() == nodeInfo.getLatestMilestoneIndex();
  }

  /**
   * Checks that node's latest solid milestone matches internal state.
   *
   * @param nodeInfo response from node API call
   * @return true if node is solid
   */
  private boolean nodeMatchesInternalState(GetNodeInfoResponse nodeInfo) {
    return config.inception || (nodeInfo.getLatestSolidSubtangleMilestoneIndex() == state.latestMilestoneIndex);
  }

  /**
   * Sets up the coordinator and validates arguments
   */
  private void setup() throws InterruptedException {
    log.info("Setup");
    GetNodeInfoResponse nodeInfoResponse = getNodeInfoWithRetries();

    if (config.bootstrap) {
      log.info("Bootstrapping.");
      if (!nodeInfoResponse.getLatestSolidSubtangleMilestone().equals(MilestoneSource.EMPTY_HASH) ||
              !nodeInfoResponse.getLatestMilestone().equals(MilestoneSource.EMPTY_HASH)) {
        throw new RuntimeException("Network already bootstrapped");
      }
    }

    log.info("Starting index from: " + state.latestMilestoneIndex);
    if (nodeInfoResponse.getLatestMilestoneIndex() > state.latestMilestoneIndex && !config.inception) {
      throw new RuntimeException("Provided index is lower than latest seen milestone: " +
              nodeInfoResponse.getLatestMilestoneIndex() + " vs " + state.latestMilestoneIndex);
    }

    milestoneTick = config.tick;
    if (milestoneTick <= 0) {
      throw new IllegalArgumentException("tick must be > 0");
    }
    log.info("Setting milestone tick rate (ms) to: " + milestoneTick);


    depth = config.depth;
    if (depth < 0) {
      throw new IllegalArgumentException("depth must be >= 0");
    }
    log.info("Setting depth to: " + depth);

    log.info("Validating Coordinator addresses.");
    if (!Objects.equals(nodeInfoResponse.getCoordinatorAddress(), db.getRoot())) {
      log.warn("Coordinator Addresses do not match! {} vs. {}", nodeInfoResponse.getCoordinatorAddress(), db.getRoot());
      if (!config.allowDifferentCooAddress) {
        throw new IllegalArgumentException("Coordinator Addresses do not match!");
      }
    }
  }

  private void start() throws InterruptedException {
    int bootstrapStage = 0;
    int milestonePropagationRetries = 0;
    int validationRetries = 0;
    this.workerThread = Thread.currentThread();
    shutdownHook();

    while (true) {
      //assume that we will be calling gtta
      boolean isReferencingLastMilestone = false;
      String trunk, branch;
      GetNodeInfoResponse nodeInfoResponse = getNodeInfoWithRetries();

      if (!config.bootstrap) {
        if (!nodeIsSolid(nodeInfoResponse)) {
          log.warn("Node not solid.");
          Thread.sleep(config.unsolidDelay);
          continue;
        }
        if (!nodeMatchesInternalState(nodeInfoResponse)) {
          if (attemptToRepropagateLatestMilestone(milestonePropagationRetries,
                  nodeInfoResponse.getLatestSolidSubtangleMilestoneIndex())) {
            milestonePropagationRetries++;
            // We wait a third of the milestone tick
            Thread.sleep(milestoneTick / 3);
            continue;
          }
          else {
            throw new RuntimeException("Latest milestone " + state.latestMilestoneHash + " #" +
                    state.latestMilestoneIndex + " is failing to propagate!!!");
          }
        }
        milestonePropagationRetries = 0;

        //if special referencing mode
        if (config.referenceLastMilestone) {
          trunk = state.latestMilestoneHash;
          branch = state.latestMilestoneHash;
        }
        //normal flow
        else {
          // We want to perform shutdown only when we are ready to issue the next normal milestone
            // and the node is in sync with the latest milestone we issued.
          if (shutdown) {
            return;
          }
          // GetTransactionsToApprove will return tips referencing latest milestone.
          GetTransactionsToApproveResponse txToApprove = null;
          try {
            txToApprove = getGetTransactionsToApproveResponseWithRetries();

            trunk = txToApprove.getTrunkTransaction();
            if (trunk == null || trunk.isEmpty()) {
              throw new RuntimeException("GTTA failed to return trunk");
            }
            branch = txToApprove.getBranchTransaction();
            if (branch == null || branch.isEmpty()) {
              throw new RuntimeException("GTTA failed to return branch");
            }
          } catch (TimeoutException e) {
            log.warn("Due to timeout we will now reference last milestone");
            trunk = state.latestMilestoneHash;
            branch = state.latestMilestoneHash;
            //gtta was not used so we set this flag to true
            isReferencingLastMilestone = true;
          }
        }

        try {
          // This can also throw a RuntimException in case of API Error
          if (!validateTransactionsToApprove(trunk, branch)) {
            throw new RuntimeException("Trunk & branch not consistent");
          }
        } catch (RuntimeException e) {
          if (++validationRetries >= config.validationAttempts) {
            throw new RuntimeException("Trunk & branch were not consistent on multiple attempts!!! T: " + trunk + " B: " + branch);
          } else {
            log.warn("Validation failed, #{} of #{} attempts. Trying again...", validationRetries, config.validationAttempts);
            // Perform gTTA and Validation again
            continue;
          }
        }
        validationRetries = 0;
      } else {
        if (bootstrapStage >= 3) {
          config.bootstrap = false;
          continue;
        }
        if (bootstrapStage == 0) {
          log.info("Bootstrapping network.");
          trunk = MilestoneSource.EMPTY_HASH;
          branch = MilestoneSource.EMPTY_HASH;
          bootstrapStage = 1;
        } else {
          // Bootstrapping means creating a chain of milestones without pulling in external transactions.
          log.info("Reusing last milestone.");
          trunk = state.latestMilestoneHash;
          branch = MilestoneSource.EMPTY_HASH;
          bootstrapStage++;
        }

        if (bootstrapStage == 2) {
          if (!nodeIsSolid(nodeInfoResponse)) {
            log.warn("Node not solid.");
            Thread.sleep(config.unsolidDelay);
            continue;
          } else if (!nodeMatchesInternalState(nodeInfoResponse)) {
            log.warn("Node's solid milestone does not match Compass state: " + state.latestMilestoneIndex);
            Thread.sleep(config.unsolidDelay);
            continue;
          }
        }
      }

      // If all the above checks pass we are ready to issue a new milestone
      state.latestMilestoneIndex++;

      createAndBroadcastMilestone(trunk, branch);
      state.latestMilestoneTime = System.currentTimeMillis();

      // Everything went fine, now we store
      try {
        storeState(state, config.statePath);
      } catch (Exception e) {
        String msg = "Error saving Compass state to file '" + config.statePath + "'!";

        log.error(msg, e);
        throw new RuntimeException(e);
      }

      //if special referencing mode
      if (config.referenceLastMilestone) {
        //exit compass
        log.info("Referencing milestone broadcasted. Please validate manually whether it was recieved.");
        log.info("Gracefully exiting compass");
        return;
      }
      //normal mode
      else {
        Thread.sleep(milestoneTick);
      }
    }
  }

  private void createAndBroadcastMilestone(String trunk, String branch) throws InterruptedException {
    log.info("Issuing milestone: " + state.latestMilestoneIndex);
    log.info("Trunk: " + trunk + " Branch: " + branch);

    List<Transaction> latestMilestoneTransactions = db.createMilestone(trunk, branch, state.latestMilestoneIndex, config.MWM);
    state.latestMilestoneTransactions = latestMilestoneTransactions.stream().map(Transaction::toTrytes).collect(Collectors.toList());
    state.latestMilestoneHash = latestMilestoneTransactions.get(0).getHash();

    // Do not store the state before broadcasting, since if broadcasting fails we should repeat the same milestone.
    broadcastLatestMilestone();
  }

  /**
   * Checks the consistency of 2 given transactions against the nodes specified by {@link #validatorAPIs}
   * @param trunk transaction to be approved by milestone
   * @param branch transaction to be approved by milestone
   * @return {@code true} if the checks passed or didn't take place. Else return {@code false}.
   */
  private boolean validateTransactionsToApprove(String trunk, String branch) throws InterruptedException {
    if (validatorAPIs.size() > 0) {

      // Give tips time to solidify on the validators
      Thread.sleep(config.validationDelay * 1000);

      return validatorAPIs.parallelStream().allMatch(validatorApi -> {
        CheckConsistencyResponse response;
        try {
          response = getCheckConsistencyResponseWithRetries(trunk, branch, validatorApi);
          if (!response.getState()) {
            log.error("{} reported invalid consistency: {}", validatorApi.getHost(), response.getInfo());
          }
          return response.getState();
        } catch (InterruptedException e) {
          throw new RuntimeException("Validation of transactions to approve failed", e);
        }
      });
    }

    //nothing was checked so validation can't fail
    return true;
  }

  private GetNodeInfoResponse getNodeInfoWithRetries() throws InterruptedException {
    GetNodeInfoResponse response = null;
    for(int i = 0; i < config.APIRetries; i++) {
      try {
        response = api.getNodeInfo();
        break;
      } catch (IllegalStateException | ArgumentException | IllegalAccessError e) {
        log.error("API call failed: ", e);
        Thread.sleep(config.APIRetryInterval);
      }
    }
    if (response == null) {
      throw new RuntimeException("getNodeInfo failed, check node!");
    }

    return response;
  }

  private GetTransactionsToApproveResponse getGetTransactionsToApproveResponseWithRetries() throws
          TimeoutException, InterruptedException {
    GetTransactionsToApproveResponse response = null;
    for(int i = 0; i < config.APIRetries; i++) {
      try {
        response = api.getTransactionsToApprove(depth, state.latestMilestoneHash);
        break;
      } catch (IllegalStateException | IllegalAccessError e) {
        log.error("API call failed: ", e);
        Thread.sleep(config.APIRetryInterval);
      }
      catch  (ArgumentException e) {
        log.error("There was a problem processing Get Transactions To Approve: ", e);
        if (e.getMessage().contains("exceeded timeout")) {
          throw new TimeoutException("Get Transactions To Approve call timed out", e);
        }
        else {
          Thread.sleep(config.APIRetryInterval);
        }
      }
    }
    if (response == null) {
      throw new RuntimeException("getTransactionsToApprove failed, check node!");
    }

    return response;
  }

  private CheckConsistencyResponse getCheckConsistencyResponseWithRetries(String trunk, String branch, IotaAPI api) throws InterruptedException {
    CheckConsistencyResponse response = null;
    for(int i = 0; i < config.APIRetries; i++) {
      try {
        response = api.checkConsistency(trunk, branch);
        break;
      } catch (IllegalStateException | ArgumentException | IllegalAccessError e) {
        log.error("API call failed: ", e);
        Thread.sleep(config.APIRetryInterval);
      }
    }
    if (response == null) {
      throw new RuntimeException("checkConsistency failed, check node!");
    }

    return response;
  }

  private void storeAndBroadcastWithRetries(String tx) throws InterruptedException {
    for(int i = 0; i < config.APIRetries; i++) {
      try {
        api.storeAndBroadcast(tx);
        return;
      } catch (IllegalStateException | ArgumentException | IllegalAccessError e) {
        log.error("API call failed: ", e);
        Thread.sleep(config.APIRetryInterval);
      }
    }

    throw new RuntimeException("storeAndBroadcast failed, check node!");
  }

  private void broadcastLatestMilestone() throws InterruptedException {
    if (config.broadcast) {
      for (String tx : state.latestMilestoneTransactions) {
        storeAndBroadcastWithRetries(tx);
      }
      log.info("Broadcast milestone: " + state.latestMilestoneIndex);
    }
  }

  /**
   * Attempts to rebroadcast the latest milestone. Should succeed if {@code milestonePropagationRetries}
   * is not above configured threshold.
   *
   * @param milestonePropagationRetries number of propagation retries that have already taken place
   * @param lsm latest solid milestone index
   * @return {@code true} if the milestone was broadcasted again else return false
   * @throws InterruptedException upon an API problem
   */
  private boolean attemptToRepropagateLatestMilestone(int milestonePropagationRetries, int lsm) throws InterruptedException {
    // Bail if we attempted to broadcast the latest Milestone too many times
    if (milestonePropagationRetries > config.propagationRetriesThreshold) {
      return false;
    }
    log.warn("getNodeInfo returned latestSolidSubtangleMilestoneIndex #{}, " +
            "it should be #{}. Rebroadcasting latest milestone.", lsm, state.latestMilestoneIndex);
    // We reissue the previous milestone again
    broadcastLatestMilestone();
    return true;
  }
}