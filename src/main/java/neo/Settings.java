package neo;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;

import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import neo.csharp.Ushort;
import neo.network.p2p.Message;
import neo.network.p2p.Peer;

/**
 * @author doubi.liu
 * @version V1.0
 * @Title: Settings
 * @Package neo
 * @Description: 配置文件加载类
 * @date Created in 10:02 2019/4/11
 */
public class Settings {

    private PathsSettings paths;
    private P2PSettings p2p;
    private RPCSettings rpc;
    private UnlockWalletSettings unlockWallet;
    private String pluginURL;

    private static Settings defaultInstance;

    public PathsSettings getPaths() {
        return paths;
    }

    public P2PSettings getP2p() {
        return p2p;
    }

    public RPCSettings getRpc() {
        return rpc;
    }

    public UnlockWalletSettings getUnlockWallet() {
        return unlockWallet;
    }

    public String getPluginURL() {
        return pluginURL;
    }

    public static Settings getDefaultInstance() {
        return defaultInstance;
    }

    static {
        URL url=Settings.class.getClassLoader().getResource("config.json");
        File file = new File(url.getPath());
        if (file.exists()) {
            try {
                BufferedReader bufferedReader = new BufferedReader(
                        new InputStreamReader(new FileInputStream(file)));
                StringBuffer stringBuffer = new StringBuffer();
                String temp = "";
                while ((temp = bufferedReader.readLine()) != null) {
                    stringBuffer.append(temp);
                }
                String tempString = stringBuffer.toString();
                // 解析,创建Gson,需要导入gson的jar包
                JsonObject section = new JsonParser().parse(tempString).getAsJsonObject();
                defaultInstance=new Settings(section);
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public Settings(JsonObject object) {
        JsonObject section=object.getAsJsonObject("ApplicationConfiguration");
        this.paths = new PathsSettings(section.getAsJsonObject("Paths"));
        this.p2p = new P2PSettings(section.getAsJsonObject("P2P"));
        this.rpc = new RPCSettings(section.getAsJsonObject("RPC"));
        this.unlockWallet = new UnlockWalletSettings(section.getAsJsonObject("UnlockWallet"));
        this.pluginURL = section.get("PluginURL").getAsString();
    }


    class PathsSettings {
        private String chain;

        private String index;

        public String getChain() {
            return chain;
        }

        public String getIndex() {
            return index;
        }

        public PathsSettings(JsonObject section) {
            this.chain = String.format(section.get("Chain").getAsString(),Long.toOctalString(Message.Magic
                    .longValue()));
            this.index = String.format(section.get("Index").getAsString(),Long.toOctalString(
                    Message.Magic.longValue()));
        }
    }

    class P2PSettings {
        private Ushort port;


        private Ushort wsPort;


        private int minDesiredConnections;


        private int maxConnections;

        public Ushort getPort() {
            return port;
        }

        public Ushort getWsPort() {
            return wsPort;
        }

        public int getMinDesiredConnections() {
            return minDesiredConnections;
        }

        public int getMaxConnections() {
            return maxConnections;
        }

        public P2PSettings(JsonObject section) {
            this.port = Ushort.parseUshort(section.get("Port").getAsString());
            this.wsPort = Ushort.parseUshort(section.get("WsPort").getAsString());
            if (section.get("MinDesiredConnections")!=null&&section.get("MinDesiredConnections").isJsonNull()){
                this.minDesiredConnections= Peer.DefaultMinDesiredConnections;
            }else {
                this.minDesiredConnections = section.get("MinDesiredConnections").getAsInt();
            }
            if (section.get("MaxConnections")!=null&&section.get("MaxConnections")
                    .isJsonNull()){
                this.maxConnections= Peer.DefaultMaxConnections;
            }else {
                this.maxConnections = section.get("MaxConnections").getAsInt();
            }
        }
    }

    class RPCSettings {
        public IPAddress bindAddress;

        public Ushort port;

        public String sslCert;

        public String sslCertPassword;

        public RPCSettings(JsonObject section) {
            this.bindAddress =  new IPAddressString(section.get("BindAddress").getAsString()).getAddress();
            this.port = Ushort.parseUshort(section.get("Port").getAsString());
            this.sslCert = section.get("SslCert").getAsString();
            this.sslCertPassword = section.get("SslCertPassword").getAsString();
        }
    }

    class UnlockWalletSettings {
        public String path;

        public String password;

        public boolean startConsensus;

        public boolean isActive;

        public String getPath() {
            return path;
        }

        public String getPassword() {
            return password;
        }

        public boolean isStartConsensus() {
            return startConsensus;
        }

        public boolean isActive() {
            return isActive;
        }

        public UnlockWalletSettings(JsonObject section) {
            if (section!=null&&!section.isJsonNull()) {
                this.path = section.get("Path").getAsString();
                this.password = section.get("Password").getAsString();
                this.startConsensus = section.get("StartConsensus").getAsBoolean();
                this.isActive = section.get("IsActive").getAsBoolean();
            }
        }
    }
}