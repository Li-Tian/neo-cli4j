package neo;

import com.google.common.base.Strings;
import com.google.gson.JsonArray;

import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.crypto.CryptoException;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import akka.actor.ActorRef;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import neo.consensus.ConsensusService;
import neo.cryptography.ecc.ECC;
import neo.cryptography.ecc.ECPoint;
import neo.csharp.BitConverter;
import neo.csharp.Out;
import neo.csharp.Uint;
import neo.csharp.Ushort;
import neo.csharp.io.ISerializable;
import neo.exception.FormatException;
import neo.exception.InvalidOperationException;
import neo.ledger.AssetState;
import neo.ledger.Blockchain;
import neo.ledger.CoinState;
import neo.ledger.ContractPropertyState;
import neo.log.notr.TR;
import neo.network.p2p.LocalNode;
import neo.network.p2p.Message;
import neo.network.p2p.RemoteNode;
import neo.network.p2p.payloads.AddrPayload;
import neo.network.p2p.payloads.ClaimTransaction;
import neo.network.p2p.payloads.CoinReference;
import neo.network.p2p.payloads.ContractTransaction;
import neo.network.p2p.payloads.GetBlocksPayload;
import neo.network.p2p.payloads.IInventory;
import neo.network.p2p.payloads.InvPayload;
import neo.network.p2p.payloads.InventoryType;
import neo.network.p2p.payloads.InvocationTransaction;
import neo.network.p2p.payloads.NetworkAddressWithTime;
import neo.network.p2p.payloads.Transaction;
import neo.network.p2p.payloads.TransactionAttribute;
import neo.network.p2p.payloads.TransactionOutput;
import neo.network.p2p.payloads.Witness;
import neo.persistence.leveldb.LevelDBStore;
import neo.plugins.Plugin;
import neo.services.ConsoleHelper;
import neo.services.ConsoleServiceBase;
import neo.shell.Coins;
import neo.smartcontract.ApplicationEngine;
import neo.smartcontract.Contract;
import neo.smartcontract.ContractParameter;
import neo.smartcontract.ContractParameterType;
import neo.smartcontract.ContractParametersContext;
import neo.vm.ScriptBuilder;
import neo.vm.VMState;
import neo.wallets.*;
import neo.wallets.Helper;
import neo.wallets.NEP6.NEP6Wallet;
import neo.wallets.SQLite.UserWallet;
import scala.collection.Parallel;
import scala.compat.java8.MakesParallelStream;

import static org.checkerframework.checker.units.UnitsTools.g;

/**
 * @author doubi.liu
 * @version V1.0
 * @Title: MainService
 * @Package neo
 * @Description: (用一句话描述该文件做什么)
 * @date Created in 11:32 2019/3/29
 */
public class MainService extends ConsoleServiceBase {
    private final static String peerStatePath = "peers.dat";

    private LevelDBStore store;
    private NeoSystem system;
    private WalletIndexer indexer;

    @Override
    protected String getPrompt() {
        return "neo";
    }

    @Override
    public String getServiceName() {
        return "NEO-CLI";
    }

    private WalletIndexer getIndexer() {
        if (indexer == null)
            indexer = new WalletIndexer(Settings.getDefaultInstance().getPaths().getIndex());
        return indexer;
    }

    private static boolean noWallet() {
        if (Program.wallet != null) return false;
        ConsoleHelper.writeLine("You have to open the wallet first.");
        return true;
    }

    @Override
    protected boolean onCommand(String[] args) {
        if (Plugin.sendMessage(args)) return true;
        switch (args[0].toLowerCase()) {
            case "broadcast":
                return onBroadcastCommand(args);
            case "relay":
                return onRelayCommand(args);
            case "sign":
                return onSignCommand(args);
            case "change":
                return onChangeCommand(args);
            case "create":
                return OnCreateCommand(args);
            case "export":
                return onExportCommand(args);
            case "help":
                return OnHelpCommand(args);
            case "plugins":
                return onPluginsCommand(args);
            case "import":
                return onImportCommand(args);
            case "list":
                return onListCommand(args);
            case "claim":
                return onClaimCommand(args);
            case "open":
                return onOpenCommand(args);
            case "rebuild":
                return OnRebuildCommand(args);
            case "send":
                return onSendCommand(args);
            case "show":
                return onShowCommand(args);
            case "start":
                return onStartCommand(args);
            case "upgrade":
                return onUpgradeCommand(args);
            case "deploy":
                return onDeployCommand(args);
            case "invoke":
                return onInvokeCommand(args);
            case "install":
                return onInstallCommand(args);
            case "uninstall":
                return onUnInstallCommand(args);
            default:
                return super.onCommand(args);
        }
    }

    private boolean onBroadcastCommand(String[] args) {
        String command = args[1].toLowerCase();
        ISerializable payload = null;
        switch (command) {
            case "addr":
                Calendar cal = Calendar.getInstance();
                InetSocketAddress socketAddress = null;
                try {
                    socketAddress = new InetSocketAddress(InetAddress.getByName
                            (new IPAddressString(args[2]).getAddress().toFullString()), Ushort
                            .parseUshort(args[3]).intValue());
                } catch (UnknownHostException e) {
                    TR.warn(e);
                    throw new RuntimeException(e);
                }
                payload = AddrPayload.create(new NetworkAddressWithTime[]{NetworkAddressWithTime
                        .create(socketAddress,
                        NetworkAddressWithTime.NODE_NETWORK, Uint.parseUint(String.valueOf(cal
                                .getTimeInMillis() / 1000)))});
                break;
            case "block":
                if (args[2].length() == 64 || args[2].length() == 66)
                    payload = Blockchain.singleton().getBlock(UInt256.parse(args[2]));
                else
                    payload = Blockchain.singleton().getStore().getBlock(Uint.parseUint(args[2]));
                break;
            case "getblocks":
            case "getheaders":
                payload = GetBlocksPayload.create(UInt256.parse(args[2]));
                break;
            case "getdata":
            case "inv":
                String temp = args[2].toLowerCase();
                if (temp.length() > 0) {
                    temp = temp.substring(0, 1).toUpperCase() + temp.substring(1);
                }
                //LINQ START
                payload = InvPayload.create(Enum.valueOf(InventoryType.class, temp),
                        Arrays.asList(args).stream().skip(3).map(p -> UInt256.parse(p)).toArray(UInt256[]::new));
                //args.Skip(3).Select(UInt256.Parse).ToArray());
                //LINQ END
                break;
            case "tx":
                payload = Blockchain.singleton().getTransaction(UInt256.parse(args[2]));
                break;
            case "alert":
            case "consensus":
            case "filteradd":
            case "filterload":
            case "headers":
            case "merkleblock":
            case "ping":
            case "pong":
            case "reject":
            case "verack":
            case "version":
                ConsoleHelper.writeLine(String.format("Command \"{0}\" is not supported.", command));
                return true;
        }
        system.localNode.tell(Message.create(command, payload), ActorRef.noSender());
        return true;
    }

    private boolean onDeployCommand(String[] args) {
        if (noWallet()) return true;
        InvocationTransaction tx = loadScriptTransaction(
                /* filePath */ args[1],
                /* paramTypes */ args[2],
                /* returnType */ args[3],
                /* hasStorage */ CliHelper.toBoolean(args[4]),
                /* hasDynamicInvoke */ CliHelper.toBoolean(args[5]),
                /* isPayable */ CliHelper.toBoolean(args[6]),
                /* contractName */ args[7],
                /* contractVersion */ args[8],
                /* contractAuthor */ args[9],
                /* contractEmail */ args[10],
                /* contractDescription */ args[11]);

        tx.version = 1;
        if (tx.attributes == null) tx.attributes = new TransactionAttribute[0];
        if (tx.inputs == null) tx.inputs = new CoinReference[0];
        if (tx.outputs == null) tx.outputs = new TransactionOutput[0];
        if (tx.witnesses == null) tx.witnesses = new Witness[0];
        ApplicationEngine engine = ApplicationEngine.run(tx.script, tx, null, true, new Fixed8());
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("VM State: {0}\n", engine.state));
        sb.append(String.format("Gas Consumed: {0}\n", engine.getGasConsumed()));
        //LINQ START
/*        sb.append(String.format("Evaluation Stack: {0}", new JArray(engine.resultStack.Select(p = >
                p.ToParameter().ToJson()));*/
        JsonArray array = new JsonArray();
        engine.resultStack.list.stream().forEach(p -> array.add(neo.vm.Helper.toParameter(p)
                .toJson()));
        sb.append(String.format("Evaluation Stack: {0}\n", array.toString()));
        //LINQ END
        ConsoleHelper.writeLine(sb.toString());
        if (engine.state.hasFlag(VMState.FAULT)) {
            ConsoleHelper.writeLine("Engine faulted.");
            return true;
        }

        tx.gas = Fixed8.subtract(engine.getGasConsumed(), Fixed8.fromDecimal(new BigDecimal(10)));
        if (tx.gas.compareTo(Fixed8.ZERO) < 0) tx.gas = Fixed8.ZERO;
        tx.gas = tx.gas.ceiling();

        tx = decorateScriptTransaction(tx);

        return signAndSendTx(tx);
    }

    private boolean onInvokeCommand(String[] args) {
        UInt160 scriptHash = UInt160.parse(args[1]);

        List<ContractParameter> contractParameters = new ArrayList<ContractParameter>();
        for (int i = 3; i < args.length; i++) {
            ContractParameter p = new ContractParameter();
            p.type = ContractParameterType.String;
            p.value = args[i];
            contractParameters.add(p);
        }

        ContractParameter p1 = new ContractParameter();
        p1.type = ContractParameterType.String;
        p1.value = args[2];

        ContractParameter p2 = new ContractParameter();
        p2.type = ContractParameterType.Array;
        p2.value = contractParameters.toArray();

        ContractParameter[] parameters = new ContractParameter[]{p1, p2};

        InvocationTransaction tx = new InvocationTransaction();

        ScriptBuilder scriptBuilder = new ScriptBuilder();
        neo.vm.Helper.emitAppCall(scriptBuilder, scriptHash, parameters);
        ConsoleHelper.writeLine(String.format("Invoking script with: '{0}'", BitConverter
                .toHexString(scriptBuilder.toArray())));
        tx.script = scriptBuilder.toArray();


        if (tx.attributes == null) tx.attributes = new TransactionAttribute[0];
        if (tx.inputs == null) tx.inputs = new CoinReference[0];
        if (tx.outputs == null) tx.outputs = new TransactionOutput[0];
        if (tx.witnesses == null) tx.witnesses = new Witness[0];
        ApplicationEngine engine = ApplicationEngine.run(tx.script, tx, null, false, new Fixed8());

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("VM State: {0}", engine.state) + "\r\n");
        sb.append(String.format("Gas Consumed: {0}", engine.getGasConsumed()) + "\r\n");
/*        sb.append(String.format("Evaluation Stack: {0}", new JArray(engine.resultStack.Select(p = >
                p.ToParameter().ToJson()))), "\r\n");*/
        JsonArray array = new JsonArray();
        engine.resultStack.list.stream().forEach(p -> array.add(neo.vm.Helper.toParameter(p)
                .toJson()));
        sb.append(String.format("Evaluation Stack: {0}", array.toString()));
        ConsoleHelper.writeLine(sb.toString());
        if (engine.state.hasFlag(VMState.FAULT)) {
            ConsoleHelper.writeLine("Engine faulted.");
            return true;
        }

        tx = decorateScriptTransaction(tx);
        return signAndSendTx(tx);
    }

    public InvocationTransaction loadScriptTransaction(
            String avmFilePath, String paramTypes, String returnTypeHexString,
            boolean hasStorage, boolean hasDynamicInvoke, boolean isPayable,
            String contractName, String contractVersion, String contractAuthor,
            String contractEmail, String contractDescription) {
        byte[] script = new byte[0];
        try {
            script = Files.readAllBytes(new File(avmFilePath).toPath());
        } catch (IOException e) {
            TR.warn(e);
            throw new RuntimeException(e);
        }
        // See ContractParameterType Enum
        byte[] parameterList = BitConverter.hexToBytes(paramTypes);
        //LINQ START
/*        ContractParameterType returnType = BitConverter.hexToBytes(returnTypeHexString)
                .Select(p = > (ContractParameterType ?) p).FirstOrDefault() ??
        ContractParameterType.Void;*/

        List<ContractParameterType> temp = new ArrayList<>();

        for (byte i : BitConverter.hexToBytes(returnTypeHexString)) {
            temp.add(ContractParameterType.parse(i));
        }
        ContractParameterType returnType = temp.stream().findFirst
                ().orElse(ContractParameterType.Void);
        //LINQ END
        ContractPropertyState properties = ContractPropertyState.NoProperty;
        if (hasStorage) properties = new ContractPropertyState((byte) (properties.value()
                | ContractPropertyState.HasStorage.value()));
        if (hasDynamicInvoke) properties = new ContractPropertyState((byte) (properties.value()
                | ContractPropertyState.HasDynamicInvoke.value()));
        if (isPayable) properties = new ContractPropertyState((byte) (properties.value()
                | ContractPropertyState.Payable.value()));
        ScriptBuilder sb = new ScriptBuilder();
        neo.vm.Helper.emitSysCall(sb, "Neo.Contract.Create", script, parameterList, returnType,
                properties, contractName, contractVersion, contractAuthor, contractEmail, contractDescription);
        InvocationTransaction invocationTransaction = new InvocationTransaction();
        invocationTransaction.script = sb.toArray();
        return invocationTransaction;

    }

    public InvocationTransaction decorateScriptTransaction(InvocationTransaction tx) {
        Fixed8 fee = Fixed8.fromDecimal(new BigDecimal(0.001));

        if (tx.script.length > 1024) {
            fee = Fixed8.add(fee, Fixed8.fromDecimal(new BigDecimal(tx.script.length).multiply(new
                    BigDecimal(0.00001))));
        }

        InvocationTransaction invocationTransaction = new InvocationTransaction();
        invocationTransaction.version = tx.version;
        invocationTransaction.script = tx.script;
        invocationTransaction.gas = tx.gas;
        invocationTransaction.attributes = tx.attributes;
        invocationTransaction.inputs = tx.inputs;
        invocationTransaction.outputs = tx.outputs;

        return Program.wallet.makeTransaction(invocationTransaction, null, null, fee);
    }

    public boolean signAndSendTx(InvocationTransaction tx) {
        ContractParametersContext context;
        try {
            context = new ContractParametersContext(tx);
        } catch (InvalidOperationException ex) {
            ConsoleHelper.writeLine(String.format("Error creating contract params: {0}", ex));
            throw ex;
        }
        Program.wallet.sign(context);
        String msg;
        if (context.completed()) {
            context.verifiable.setWitnesses(context.getWitnesses());
            Program.wallet.applyTransaction(tx);
            LocalNode.Relay relay = new LocalNode.Relay();
            relay.inventory = tx;
            system.localNode.tell(relay, ActorRef.noSender());
            msg = String.format("Signed and relayed transaction with hash={0}", tx.hash());
            ConsoleHelper.writeLine(msg);
            return true;
        }

        msg = String.format("Failed sending transaction with hash={0}", tx.hash());
        ConsoleHelper.writeLine(msg);
        return true;
    }

    private boolean onRelayCommand(String[] args) {
        if (args.length < 2) {
            ConsoleHelper.writeLine("You must input JSON object to relay.");
            return true;
        }
        //LINQ START
        //String jsonObjectToRelay = StringUtils.join(args.skip(1),"");
        String jsonObjectToRelay = StringUtils.join(Arrays.asList(args).stream().skip(1).toArray
                (String[]::new), "");
        //LINQ END
        if (Strings.isNullOrEmpty(jsonObjectToRelay)) {
            ConsoleHelper.writeLine("You must input JSON object to relay.");
            return true;
        }
        try {
            ContractParametersContext context = ContractParametersContext.parse(jsonObjectToRelay);
            if (!context.completed()) {
                ConsoleHelper.writeLine("The signature is incomplete.");
                return true;
            }
            context.verifiable.setWitnesses(context.getWitnesses());
            IInventory inventory = (IInventory) context.verifiable;
            LocalNode.Relay relay = new LocalNode.Relay();
            relay.inventory = inventory;
            system.localNode.tell(relay, ActorRef.noSender());
            ConsoleHelper.writeLine(String.format("Data relay success, the hash is shown as " +
                    "follows:\r\n{0}", inventory.hash()));
        } catch (Exception e) {
            ConsoleHelper.writeLine(String.format("One or more errors occurred:\r\n{0}", e
                    .getMessage()));
        }
        return true;
    }

    private boolean onSignCommand(String[] args) {
        if (noWallet()) return true;

        if (args.length < 2) {
            ConsoleHelper.writeLine("You must input JSON object pending signature data.");
            return true;
        }
        //LINQ START
        //String jsonObjectToRelay = StringUtils.join(args.skip(1),"");
        String jsonObjectToSign = StringUtils.join(Arrays.asList(args).stream().skip(1).toArray
                (String[]::new), "");
        //LINQ END
        if (Strings.isNullOrEmpty(jsonObjectToSign)) {
            ConsoleHelper.writeLine("You must input JSON object pending signature data.");
            return true;
        }
        try {
            ContractParametersContext context = ContractParametersContext.parse(jsonObjectToSign);
            if (!Program.wallet.sign(context)) {
                ConsoleHelper.writeLine("The private key that can sign the data is not found.");
                return true;
            }
            ConsoleHelper.writeLine(String.format("Signed Output:\r\n{0}", context));
        } catch (Exception e) {
            ConsoleHelper.writeLine(String.format("One or more errors occurred:\r\n{0}", e.getMessage()));
        }
        return true;
    }

    private boolean onChangeCommand(String[] args) {
        switch (args[1].toLowerCase()) {
            case "view":
                return onChangeViewCommand(args);
            default:
                return super.onCommand(args);
        }
    }

    private boolean onChangeViewCommand(String[] args) {
        if (args.length != 3) return false;
        try {
            byte viewnumber = Byte.parseByte(args[2], 16);
            if (system.consensus != null) {
                ConsensusService.SetViewNumber setViewNumber = new ConsensusService.SetViewNumber();
                setViewNumber.viewNumber = viewnumber;
                system.consensus.tell(setViewNumber, ActorRef.noSender());
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean OnCreateCommand(String[] args) {
        switch (args[1].toLowerCase()) {
            case "address":
                return onCreateAddressCommand(args);
            case "wallet":
                return onCreateWalletCommand(args);
            default:
                return super.onCommand(args);
        }
    }

    private boolean onCreateAddressCommand(String[] args) {
        if (noWallet()) return true;
        if (args.length > 3) {
            ConsoleHelper.writeLine("error");
            return true;
        }

        Ushort count;
        if (args.length >= 3)
            count = Ushort.parseUshort(args[2]);
        else
            count = Ushort.ONE;

        final Set<Integer> set = new HashSet<Integer>();
        List<String> addresses = new ArrayList<String>();
        //LINQ START
        IntStream.rangeClosed(0, count.intValue()).parallel().forEach(p -> {
            WalletAccount account = Program.wallet.createAccount();

            synchronized (addresses) {
                set.add(p);
                addresses.add(account.getAddress());
                // TODO: 2019/4/17 控制台光标移动，暂时不需要
                //Console.SetCursorPosition(0, Console.CursorTop);
                ConsoleHelper.write(String.format("[{0}/{1}]", set.size(), count));
            }
        });

/*        Parallel.For(0, count, (i) = >
                {
                        WalletAccount account = Program.wallet.createAccount();

        synchronized (addresses) {
            x++;
            addresses.add(account.address);
            // TODO: 2019/4/17 控制台光标移动，暂时不需要
            //Console.SetCursorPosition(0, Console.CursorTop);
            ConsoleHelper.write(String.format("[{0}/{1}]", x, count));
        }});*/
        //LINQ END

        if (Program.wallet instanceof NEP6Wallet)
            ((NEP6Wallet) Program.wallet).save();
        ConsoleHelper.writeLine();
        String path = "address.txt";
        ConsoleHelper.writeLine(String.format("export addresses to {0}", path));
        try {
            Files.write(new File(path).toPath(), addresses);
        } catch (IOException e) {
            e.printStackTrace();
            TR.warn(e);
            throw new RuntimeException(e);
        }
        return true;
    }

    private boolean onCreateWalletCommand(String[] args) {
        if (args.length < 3) {
            ConsoleHelper.writeLine("error");
            return true;
        }
        String path = args[2];
        String password = readPassword("password");
        if (password.length() == 0) {
            ConsoleHelper.writeLine("cancelled");
            return true;
        }
        String password2 = readPassword("password");
        if (!password.equals(password2)) {
            ConsoleHelper.writeLine("error");
            return true;
        }
        String fileName = new File(path).getName();
        switch (fileName.substring(fileName.lastIndexOf("."), fileName.length())) {
            case ".db3": {
                Program.wallet = UserWallet.create(getIndexer(), path, password);
                WalletAccount account = Program.wallet.createAccount();
                ConsoleHelper.writeLine(String.format("address: {0}", account.getAddress()));
                ConsoleHelper.writeLine(String.format(" pubkey: {0}", BitConverter.toHexString(account.getKey
                        ().publicKey.getEncoded(true))));
                if (system.rpcServer != null)
                    system.rpcServer.wallet = Program.wallet;
            }
            break;
            case ".json": {
                NEP6Wallet wallet = new NEP6Wallet(getIndexer(), path);
                wallet.unlock(password);
                WalletAccount account = wallet.createAccount();
                wallet.save();
                Program.wallet = wallet;
                ConsoleHelper.writeLine(String.format("address: {0}", account.getAddress()));
                ConsoleHelper.writeLine(String.format(" pubkey: {0}", BitConverter.toHexString(account.getKey
                        ().publicKey.getEncoded(true))));
                if (system.rpcServer != null)
                    system.rpcServer.wallet = Program.wallet;
            }
            break;
            default:
                ConsoleHelper.writeLine("Wallet files in that format are not supported, please use a .json or .db3 file extension.");
                break;
        }
        return true;
    }

    private boolean onExportCommand(String[] args) {
        switch (args[1].toLowerCase()) {
            case "key":
                return onExportKeyCommand(args);
            default:
                return super.onCommand(args);
        }
    }

    private boolean onExportKeyCommand(String[] args) {
        if (noWallet()) return true;
        if (args.length < 2 || args.length > 4) {
            ConsoleHelper.writeLine("error");
            return true;
        }
        UInt160 scriptHash = null;
        String path = null;
        if (args.length == 3) {
            try {
                scriptHash = neo.wallets.Helper.toScriptHash(args[2]);
            } catch (FormatException e) {
                path = args[2];
            }
        } else if (args.length == 4) {
            scriptHash = neo.wallets.Helper.toScriptHash(args[2]);
            path = args[3];
        }
        String password = readPassword("password");
        if (password.length() == 0) {
            ConsoleHelper.writeLine("cancelled");
            return true;
        }
        if (!Program.wallet.verifyPassword(password)) {
            ConsoleHelper.writeLine("Incorrect password");
            return true;
        }
        //LINQ START
/*        IEnumerable<KeyPair> keys;
        if (scriptHash == null)
            keys = Program.wallet.getAccounts().Where(p = > p.HasKey).Select(p = > p.GetKey());
            else
        keys = new[]{
            Program.wallet.getAccount(scriptHash).GetKey()
        } ;
        if (path == null)
            for (KeyPair key : keys)
                ConsoleHelper.writeLine(key.export());
        else
            File.WriteAllLines(path, keys.Select(p = > p.Export()));*/
        KeyPair[] keys;
        if (scriptHash == null)
            keys = StreamSupport.stream(Program.wallet.getAccounts().spliterator(), false).filter
                    (p -> p.hasKey()).map(p -> p.getKey()).toArray(KeyPair[]::new);
        else
            keys = new KeyPair[]{Program.wallet.getAccount(scriptHash).getKey()};
        if (path == null)
            for (KeyPair key : keys)
                ConsoleHelper.writeLine(key.export());
        else
            try {
                Files.write(new File(path).toPath(), Arrays.asList(keys).stream().map(p -> p.export())
                        .collect(Collectors.toList()));
            } catch (IOException e) {
                e.printStackTrace();
                TR.warn(e);
                throw new RuntimeException(e);
            }
        //LINQ END
        return true;
    }

    private boolean OnHelpCommand(String[] args) {
        ConsoleHelper.write(
                "Normal Commands:\n" +
                        "\tversion\n" +
                        "\thelp [plugin-name]\n" +
                        "\tclear\n" +
                        "\texit\n" +
                        "Wallet Commands:\n" +
                        "\tcreate wallet <path>\n" +
                        "\topen wallet <path>\n" +
                        "\tupgrade wallet <path>\n" +
                        "\trebuild index\n" +
                        "\tlist address\n" +
                        "\tlist asset\n" +
                        "\tlist key\n" +
                        "\tshow utxo [id|alias]\n" +
                        "\tshow gas\n" +
                        "\tclaim gas [all] [changeAddress]\n" +
                        "\tcreate address [n=1]\n" +
                        "\timport key <wif|path>\n" +
                        "\texport key [address] [path]\n" +
                        "\timport multisigaddress m pubkeys...\n" +
                        "\tsend <id|alias> <address> <value>|all [fee=0]\n" +
                        "\tsign <jsonObjectToSign>\n" +
                        "Contract Commands:\n" +
                        "\tdeploy <avmFilePath> <paramTypes> <returnTypeHexString> <hasStorage (true|false)> <hasDynamicInvoke (true|false)> <isPayable (true|false) <contractName> <contractVersion> <contractAuthor> <contractEmail> <contractDescription>\n" +
                        "\tinvoke <scripthash> <command> [optionally quoted params separated by space]\n" +
                        "Node Commands:\n" +
                        "\tshow state\n" +
                        "\tshow pool [verbose]\n" +
                        "\trelay <jsonObjectToSign>\n" +
                        "Plugin Commands:\n" +
                        "\tplugins\n" +
                        "\tinstall <pluginName>\n" +
                        "\tuninstall <pluginName>\n" +
                        "Advanced Commands:\n" +
                        "\tstart consensus\n");

        return true;
    }

    private boolean onPluginsCommand(String[] args) {
        if (Plugin.plugins.size() > 0) {
            ConsoleHelper.writeLine("Loaded plugins:");
            Plugin.plugins.forEach(p -> ConsoleHelper.writeLine("\t" + p.name()));
        } else {
            ConsoleHelper.writeLine("No loaded plugins");
        }
        return true;
    }

    private boolean onImportCommand(String[] args) {
        switch (args[1].toLowerCase()) {
            case "key":
                return onImportKeyCommand(args);
            case "multisigaddress":
                return onImportMultisigAddress(args);
            default:
                return super.onCommand(args);
        }
    }

    private boolean onImportMultisigAddress(String[] args) {
        if (noWallet()) return true;

        if (args.length < 5) {
            ConsoleHelper.writeLine("Error. Use at least 2 public keys to create a multisig address.");
            return true;
        }

        int m = Integer.parseInt(args[2]);
        int n = args.length - 3;

        if (m < 1 || m > n || n > 1024) {
            ConsoleHelper.writeLine("Error. Invalid parameters.");
            return true;
        }
        //LINQ START
/*        ECPoint[] publicKeys = args.skip(3).Select(p = > ECPoint.Parse(p, ECCurve.Secp256r1)).
        ToArray();

        Contract multiSignContract = Contract.createMultiSigContract(m, publicKeys);
        KeyPair keyPair = Program.wallet.getAccounts().FirstOrDefault(p = > p.HasKey &&
                publicKeys.contains(p.GetKey().PublicKey))?.
        GetKey();*/
        ECPoint[] publicKeys = Arrays.asList(args).stream().skip(3).map(p -> ECPoint.parse(p,
                ECC.Secp256r1.getCurve())).toArray(ECPoint[]::new);

        Contract multiSignContract = Contract.createMultiSigContract(m, publicKeys);
        WalletAccount walletAccount = StreamSupport.stream(Program.wallet.getAccounts().spliterator
                (), false).filter(p -> p.hasKey() && Arrays.asList(publicKeys).contains(p.getKey().publicKey))
                .findFirst().orElse(null);
        KeyPair keyPair = walletAccount == null ? null : walletAccount.getKey();
        //LINQ END

        WalletAccount account = Program.wallet.createAccount(multiSignContract, keyPair);
        if (Program.wallet instanceof NEP6Wallet)
            ((NEP6Wallet) Program.wallet).save();

        ConsoleHelper.writeLine("Multisig. Addr.: " + multiSignContract.address());

        return true;
    }

    private boolean onImportKeyCommand(String[] args) {
        if (args.length > 3) {
            ConsoleHelper.writeLine("error");
            return true;
        }
        byte[] prikey = null;
        try {
            prikey = Wallet.getPrivateKeyFromWIF(args[2]);
        } catch (FormatException e) {
        }
        if (prikey == null) {
            String[] lines = new String[0];
            try {
                lines = Files.readAllLines(new File(args[2]).toPath()).toArray(new String[0]);
            } catch (IOException e) {
                TR.warn(e);
                throw new RuntimeException(e);
            }
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].length() == 64)
                    prikey = BitConverter.hexToBytes(lines[i]);
                else
                    prikey = Wallet.getPrivateKeyFromWIF(lines[i]);
                Program.wallet.createAccount(prikey);
                Arrays.fill(prikey, 0, prikey.length, (byte) 0x00);
                // TODO: 2019/4/17 控制台光标移动，暂时不需要
                //Console.SetCursorPosition(0, Console.CursorTop);
                ConsoleHelper.write(String.format("[{0}/{1}]", i + 1, lines.length));
            }
            ConsoleHelper.writeLine();
        } else {
            WalletAccount account = Program.wallet.createAccount(prikey);
            Arrays.fill(prikey, 0, prikey.length, (byte) 0x00);
            ConsoleHelper.writeLine(String.format("address: {0}", account.getAddress()));
            ConsoleHelper.writeLine(String.format(" pubkey: {0}", BitConverter.toHexString(account.getKey()
                    .publicKey.getEncoded(true))));
        }
        if (Program.wallet instanceof NEP6Wallet)
            ((NEP6Wallet) Program.wallet).save();
        return true;
    }

    private boolean onListCommand(String[] args) {
        switch (args[1].toLowerCase()) {
            case "address":
                return onListAddressCommand(args);
            case "asset":
                return onListAssetCommand(args);
            case "key":
                return onListKeyCommand(args);
            default:
                return super.onCommand(args);
        }
    }

    private boolean onClaimCommand(String[] args) {
        if (args.length < 2 || args.length > 4 || !args[1].equalsIgnoreCase("gas"))
            return super.onCommand(args);
        if (noWallet()) return true;

        boolean all = args.length > 2 && args[2].equalsIgnoreCase("all");
        boolean useChangeAddress = (all && args.length == 4) || (!all && args.length == 3);
        UInt160 changeAddress = useChangeAddress ? Helper.toScriptHash(args[args.length - 1]) : null;

        if (useChangeAddress) {
            String password = readPassword("password");
            if (password.length() == 0) {
                ConsoleHelper.writeLine("cancelled");
                return true;
            }
            if (!Program.wallet.verifyPassword(password)) {
                ConsoleHelper.writeLine("Incorrect password");
                return true;
            }
        }
        Coins coins = new Coins(Program.wallet, system);
        ClaimTransaction[] txs = all
                ? coins.claimAll(changeAddress)
                : new ClaimTransaction[]{coins.claim(changeAddress)};
        if (txs == null) return true;
        for (ClaimTransaction tx : txs)
            if (tx != null)
                ConsoleHelper.writeLine(String.format("Transaction Succeeded: {0}", tx.hash()));
        return true;
    }

    private boolean onShowGasCommand(String[] args) {
        if (noWallet()) return true;

        Coins coins = new Coins(Program.wallet, system);
        ConsoleHelper.writeLine(String.format("unavailable: {0}", coins.unavailableBonus()
                .toString()));
        ConsoleHelper.writeLine(String.format("  available: {0}", coins.availableBonus()
                .toString()));
        return true;
    }

    private boolean onListKeyCommand(String[] args) {
        if (noWallet()) return true;
        //LINQ START
/*        for (KeyPair key : Program.Wallet.GetAccounts().Where(p = > p.HasKey).
        Select(p = > p.getKey()))
        {
            ConsoleHelper.writeLine(key.PublicKey);
        }*/
        for (KeyPair key : StreamSupport.stream(Program.wallet.getAccounts().spliterator(), false)
                .filter(p -> p.hasKey()).map(p -> p.getKey()).collect(Collectors.toList())) {
            ConsoleHelper.writeLine(key.publicKey.toString());
        }
        //LINQ END
        return true;
    }

    private boolean onListAddressCommand(String[] args) {
        if (noWallet()) return true;
        //LINQ START
/*        foreach (Contract contract in Program.Wallet.GetAccounts().Where(p => !p.WatchOnly).Select(p => p.Contract))
        {
            Console.WriteLine($"{contract.Address}\t{(contract.Script.IsStandardContract() ? "Standard" : "Nonstandard")}");
        }*/
        for (Contract contract : StreamSupport.stream(Program.wallet.getAccounts().spliterator(), false
        ).filter(p -> !p.watchOnly()).map(p -> p.contract).collect(Collectors.toList())) {
            ConsoleHelper.writeLine(String.format("{0}\t{1}", contract.address(), neo
                    .smartcontract.Helper.isStandardContract(contract
                            .script) ? "Standard" : "Nonstandard"));
        }
        //LINQ END
        return true;
    }

    private boolean onListAssetCommand(String[] args) {
        if (noWallet()) return true;
        //LINQ START
/*        for (var item : Program.wallet.getCoins().Where(p = > !p.State.HasFlag(CoinState.Spent))
        .GroupBy(p = > p.Output.AssetId, (k, g) =>
        new
        {
            Asset = Blockchain.Singleton.Store.GetAssets().TryGet(k),
                    Balance = g.Sum(p = > p.Output.Value),
            Confirmed = g.Where(p = > p.State.HasFlag(CoinState.Confirmed)).Sum(p = > p.Output.Value)
        }))
        {
            Console.WriteLine($"       id:{item.Asset.AssetId}");
            Console.WriteLine($"     name:{item.Asset.GetName()}");
            Console.WriteLine($"  balance:{item.Balance}");
            Console.WriteLine($"confirmed:{item.Confirmed}");
            Console.WriteLine();
        }*/

        List<Map<String, Object>> temp = new ArrayList<>();
        Map<UInt256, List<Coin>> tempMap = StreamSupport.stream(Program.wallet.getCoins()
                .spliterator(), false).filter(p -> !p.state.hasFlag(CoinState.Spent))
                .collect(Collectors.groupingBy(p -> p.output.assetId));
        List<Map<String, Object>> itemlist = tempMap.entrySet().stream().map(p -> {
            AssetState asset = Blockchain.singleton().getStore().getAssets().tryGet(p.getKey());
            Fixed8 balance = p.getValue().stream().map(q -> q.output.value).reduce(Fixed8.ZERO, (x, y)
                    -> Fixed8.add(x, y));
            Fixed8 confirmed = p.getValue().stream().filter(k -> k.state.hasFlag(CoinState.Confirmed))
                    .map(q -> q.output.value).reduce(Fixed8.ZERO, (x, y)
                            -> Fixed8.add(x, y));

            Map<String, Object> k = new HashMap<>();
            k.put("asset", asset);
            k.put("balance", balance);
            k.put("confirmed", confirmed);
            return k;
        }).collect(Collectors.toList());

        for (Map<String, Object> item : itemlist) {
            ConsoleHelper.writeLine(String.format("       id:{0}", ((AssetState) item.get("asset"))
                    .assetId));
            ConsoleHelper.writeLine(String.format("     name:{0}", ((AssetState) item.get("asset"))
                    .getName()));
            ConsoleHelper.writeLine(String.format("  balance:{0}", ((Fixed8) item.get("balance")).toString()));
            ConsoleHelper.writeLine(String.format("confirmed:{0}", ((Fixed8) item.get("confirmed"))
                    .toString()));
            ConsoleHelper.writeLine();
        }
        //LINQ END
        return true;
    }

    private boolean onOpenCommand(String[] args) {
        switch (args[1].toLowerCase()) {
            case "wallet":
                return OnOpenWalletCommand(args);
            default:
                return super.onCommand(args);
        }
    }

    //TODO: 目前没有想到其它安全的方法来保存密码
    //所以只能暂时手动输入，但如此一来就不能以服务的方式启动了
    //未来再想想其它办法，比如采用智能卡之类的
    private boolean OnOpenWalletCommand(String[] args) {
        if (args.length < 3) {
            ConsoleHelper.writeLine("error");
            return true;
        }
        String path = args[2];
        if (!new File(path).exists()) {
            ConsoleHelper.writeLine("File does not exist");
            return true;
        }
        String password = readPassword("password");
        if (password.length() == 0) {
            ConsoleHelper.writeLine("cancelled");
            return true;
        }
        try {
            Program.wallet = openWallet(getIndexer(), path, password);
        } catch (Exception e) {
            ConsoleHelper.writeLine(String.format("failed to open file \"{0}\"", path));
        }
        if (system.rpcServer != null)
            system.rpcServer.wallet = Program.wallet;
        return true;
    }

    private boolean OnRebuildCommand(String[] args) {
        switch (args[1].toLowerCase()) {
            case "index":
                return onRebuildIndexCommand(args);
            default:
                return super.onCommand(args);
        }
    }

    private boolean onRebuildIndexCommand(String[] args) {
        getIndexer().rebuildIndex();
        return true;
    }

    private boolean onSendCommand(String[] args) {
        if (args.length < 4 || args.length > 5) {
            ConsoleHelper.writeLine("error");
            return true;
        }
        if (noWallet()) return true;
        String password = readPassword("password");
        if (password.length() == 0) {
            ConsoleHelper.writeLine("cancelled");
            return true;
        }
        if (!Program.wallet.verifyPassword(password)) {
            ConsoleHelper.writeLine("Incorrect password");
            return true;
        }
        UIntBase assetId;
        switch (args[1].toLowerCase()) {
            case "neo":
            case "ans":
                assetId = Blockchain.GoverningToken.hash();
                break;
            case "gas":
            case "anc":
                assetId = Blockchain.UtilityToken.hash();
                break;
            default:
                assetId = UIntBase.parse(args[1]);
                break;
        }
        UInt160 scriptHash = Helper.toScriptHash(args[2]);
        boolean isSendAll = args[3].equalsIgnoreCase("all");
        Transaction tx;
        if (isSendAll) {
            //LINQ START
/*            Coin[] coins = Program.wallet.findUnspentCoins().Where(p = > p.Output.AssetId.Equals
                    (assetId)).ToArray();
            tx = new ContractTransaction();
            {
                Attributes = new TransactionAttribute[0],
                        Inputs = coins.Select(p = > p.Reference).ToArray(),
                    Outputs = new[]
                {
                    new TransactionOutput
                    {
                        AssetId = (UInt256) assetId,
                                Value = coins.Sum(p = > p.Output.Value),
                        ScriptHash = scriptHash
                    }
                }
            } ;*/
            Coin[] coins = StreamSupport.stream(Program.wallet.findUnspentCoins().spliterator(),
                    false).filter(p -> p.output.assetId.equals(assetId)).toArray(Coin[]::new);
            tx = new ContractTransaction();
            tx.attributes = new TransactionAttribute[0];
            tx.inputs = Arrays.asList(coins).stream().map(p -> p.reference).toArray(CoinReference[]::new);
            TransactionOutput transactionOutput = new TransactionOutput();
            transactionOutput.assetId = (UInt256) assetId;
            transactionOutput.value = Arrays.asList(coins).stream().map(p -> p.output.value).reduce
                    (Fixed8.ZERO, (x, y) -> Fixed8.add(x, y));
            transactionOutput.scriptHash = scriptHash;
            tx.outputs = new TransactionOutput[]{transactionOutput};
            //LINQ END
        } else {
            AssetDescriptor descriptor = new AssetDescriptor(assetId);

            BigDecimal amount = null;
            try {
                amount = new BigDecimal(args[3]);
                amount.setScale(descriptor.decimals);
                if (amount.signum() <= 0) {
                    throw new Exception();
                }
            } catch (Exception e) {
                ConsoleHelper.writeLine("Incorrect Amount Format");
                return true;
            }
            Fixed8 fee = Fixed8.ZERO;

            if (args.length >= 5) {
                Fixed8 fee1 = new Fixed8();
                if (!Fixed8.tryParse(args[4], fee1) || fee1.compareTo(Fixed8.ZERO) < 0) {
                    ConsoleHelper.writeLine("Incorrect Fee Format");
                    return true;
                }
            }

            TransferOutput transferOutput = new TransferOutput(assetId, amount, scriptHash);
            tx = Program.wallet.makeTransaction(null, Arrays.asList(new
                    TransferOutput[]{transferOutput}), null, null, fee);
            if (tx == null) {
                ConsoleHelper.writeLine("Insufficient funds");
                return true;
            }
        }
        ContractParametersContext context = new ContractParametersContext(tx);
        Program.wallet.sign(context);
        if (context.completed()) {
            tx.witnesses = context.getWitnesses();
            Program.wallet.applyTransaction(tx);
            LocalNode.Relay relay = new LocalNode.Relay();
            relay.inventory = tx;
            system.localNode.tell(relay, ActorRef.noSender());
            ConsoleHelper.writeLine(String.format("TXID: {0}", tx.hash()));
        } else {
            ConsoleHelper.writeLine("SignatureContext:");
            ConsoleHelper.writeLine(context.toString());
        }
        return true;
    }

    private boolean onShowCommand(String[] args) {
        switch (args[1].toLowerCase()) {
            case "gas":
                return onShowGasCommand(args);
            case "pool":
                return onShowPoolCommand(args);
            case "state":
                return onShowStateCommand(args);
            case "utxo":
                return onShowUtxoCommand(args);
            default:
                return super.onCommand(args);
        }
    }

    private boolean onShowPoolCommand(String[] args) {
        boolean verbose = args.length >= 3 && args[2] == "verbose";
        if (verbose) {
            Out<Collection<Transaction>> verifiedTransactions = new Out();
            verifiedTransactions.set(new ArrayList<>());
            Out<Collection<Transaction>> unverifiedTransactions = new Out();
            unverifiedTransactions.set(new ArrayList<>());
            Blockchain.singleton().getMemPool().getVerifiedAndUnverifiedTransactions(
                    verifiedTransactions, unverifiedTransactions);
            ConsoleHelper.writeLine("Verified Transactions:");
            for (Transaction tx : verifiedTransactions.get())
                ConsoleHelper.writeLine(String.format(" {0} {1}", tx.hash(), tx.getClass().getName()));
            ConsoleHelper.writeLine("Unverified Transactions:");
            for (Transaction tx : unverifiedTransactions.get())
                ConsoleHelper.writeLine(String.format(" {0} {1}", tx.hash(), tx.getClass().getName()));
        }
        ConsoleHelper.writeLine(String.format("total: {0}, verified: " +
                        "{1}, unverified: {2}", Blockchain.singleton().getMemPool().count(),
                Blockchain.singleton().getMemPool().verifiedCount(),
                Blockchain.singleton().getMemPool().unVerifiedCount()));
        return true;
    }

    private boolean onShowStateCommand(String[] args) {
        final boolean[] stop = {false};
        //Console.CursorVisible = false;
        ConsoleHelper.clear();
        //Object lock=new Object();
        Thread thread=new Thread(new Runnable() {
            @Override
            public void run() {
                while (!stop[0]) {
                    // TODO: 2019/4/17 控制台光标移动，暂时不需要
                    //Console.SetCursorPosition(0, 0);
                    Uint wh = Uint.ZERO;
                    if (Program.wallet != null)
                        wh = (Program.wallet.getWalletHeight().compareTo(Uint.ZERO) > 0) ? Program.wallet
                                .getWalletHeight().subtract(Uint.ONE) : Uint.ZERO;

                    writeLineWithoutFlicker(String.format("block: {0}/{1}/{2}  connected: {3}  " +
                            "unconnected: {4}", wh, Blockchain.singleton
                            ().height(), Blockchain.singleton().headerHeight(), LocalNode.singleton()
                            .getConnectedCount(), LocalNode.singleton().getUnconnectedCount()), 80);
                    int linesWritten = 1;
                    for (RemoteNode node : LocalNode.singleton().getRemoteNodes().toArray
                            (new RemoteNode[0])) {
                        writeLineWithoutFlicker(String.format("  ip: {0}\tport: {1}\tlisten: {2}\theight:" +
                                " {3}", node.remote.getAddress(), node.remote.getPort(), node
                                .getListenerPort(), node.version == null ? null : node.version.startHeight), 80);
                        linesWritten++;
                    }

                    //while (++linesWritten < ConsoleHelper.getConsole().WindowHeight)
                        writeLineWithoutFlicker();
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        thread.start();
/*        Task task = Task.Run(async() = >
                {
        while (!stop) {
            // TODO: 2019/4/17 控制台光标移动，暂时不需要
            //Console.SetCursorPosition(0, 0);
            Uint wh = Uint.ZERO;
            if (Program.wallet != null)
                wh = (Program.wallet.getWalletHeight().compareTo(Uint.ZERO) > 0) ? Program.wallet
                        .getWalletHeight().subtract(Uint.ONE) : Uint.ZERO;

            writeLineWithoutFlicker(String.format("block: {0}/{1}/{2}  connected: {3}  " +
                    "unconnected: {4}", wh, Blockchain.singleton
                    ().height(), Blockchain.singleton().headerHeight(), LocalNode.singleton()
                    .getConnectedCount(), LocalNode.singleton().getUnconnectedCount()), 80);
            int linesWritten = 1;
            for (RemoteNode node : LocalNode.singleton().getRemoteNodes().Take(Console.WindowHeight -
                    2).ToArray()) {
                writeLineWithoutFlicker(String.format("  ip: {0}\tport: {1}\tlisten: {2}\theight:" +
                        " {3}", node.remote.getAddress(), node.remote.getPort(), node
                        .getListenerPort(), node.version == null ? null : node.version.startHeight), 80);
                linesWritten++;
            }

            while (++linesWritten < ConsoleHelper.getConsole().WindowHeight)
                writeLineWithoutFlicker();
            await Task.Delay(500);
        }
            });*/
        ConsoleHelper.readLine();
        stop[0] = true;
        //lock.Wait();
        ConsoleHelper.writeLine();
        // TODO: 2019/4/17 控制台光标移动，暂时不需要
        //Console.CursorVisible = true;
        return true;
    }

    private boolean onShowUtxoCommand(String[] args) {
        if (noWallet()) return true;
        Iterable<Coin> coins = Program.wallet.findUnspentCoins();
        if (args.length >= 3) {
            UInt256 assetId;
            switch (args[2].toLowerCase()) {
                case "neo":
                case "ans":
                    assetId = Blockchain.GoverningToken.hash();
                    break;
                case "gas":
                case "anc":
                    assetId = Blockchain.UtilityToken.hash();
                    break;
                default:
                    assetId = UInt256.parse(args[2]);
                    break;
            }
            //LINQ START
            //coins = coins.Where(p = > p.Output.AssetId.Equals(assetId));
            coins = StreamSupport.stream(coins.spliterator(), false).filter(p -> p.output.assetId
                    .equals(assetId)).collect(Collectors.toList());
            //LINQ END
        }
        Coin[] coins_array = StreamSupport.stream(coins.spliterator(), false).toArray(Coin[]::new);
        final int MAX_SHOW = 100;
        for (int i = 0; i < coins_array.length && i < MAX_SHOW; i++)
            ConsoleHelper.writeLine(String.format("{0}:{1}", coins_array[i].reference.prevHash,
                    coins_array[i].reference.prevIndex));
        if (coins_array.length > MAX_SHOW)
            ConsoleHelper.writeLine(String.format("({0} more)", coins_array.length - MAX_SHOW));
        ConsoleHelper.writeLine(String.format("total: {0} UTXOs", coins_array.length));
        return true;
    }

    @Override
    protected void onStart(String[] args) {
        boolean useRPC = false;
        for (int i = 0; i < args.length; i++)
            switch (args[i]) {
                case "/rpc":
                case "--rpc":
                case "-r":
                    useRPC = true;
                    break;
            }
        try {
            store = new LevelDBStore(new File(Settings.getDefaultInstance().getPaths().getChain())
                    .getAbsolutePath());
        } catch (IOException e) {
            TR.warn(e);
            throw new RuntimeException(e);
        }
        system = new NeoSystem(store);
        system.startNode(Settings.getDefaultInstance().getP2p().getPort().intValue(),
                Settings.getDefaultInstance().getP2p().getMinDesiredConnections(),
                Settings.getDefaultInstance().getP2p().getMaxConnections());
        if (Settings.getDefaultInstance().getUnlockWallet().isActive()) {
            try {
                Program.wallet = openWallet(getIndexer(), Settings.getDefaultInstance()
                                .getUnlockWallet().getPath(),
                        Settings.getDefaultInstance().getUnlockWallet().getPassword());
            } catch (Exception e) {
                ConsoleHelper.writeLine(String.format("failed to open file \"{0}", Settings
                        .getDefaultInstance().getUnlockWallet().getPath()));
            }
            if (Settings.getDefaultInstance().getUnlockWallet().startConsensus && Program.wallet
                    != null) {
                onStartConsensusCommand(null);
            }
        }
        if (useRPC) {
            system.startRpc(Settings.getDefaultInstance().getRpc().bindAddress,
                    Settings.getDefaultInstance().getRpc().port.intValue(),
                    Program.wallet,
                    Settings.getDefaultInstance().getRpc().sslCert,
                    Settings.getDefaultInstance().getRpc().sslCertPassword, null, new Fixed8());
        }
    }

    private boolean onStartCommand(String[] args) {
        switch (args[1].toLowerCase()) {
            case "consensus":
                return onStartConsensusCommand(args);
            default:
                return super.onCommand(args);
        }
    }

    private boolean onStartConsensusCommand(String[] args) {
        if (noWallet()) return true;
        showPrompt = false;
        system.startConsensus(Program.wallet);
        return true;
    }

    @Override
    protected void onStop() {
        system.dispose();
        try {
            store.close();
        } catch (IOException e) {
            TR.warn(e);
            throw new RuntimeException(e);
        }
    }

    private boolean onUpgradeCommand(String[] args) {
        switch (args[1].toLowerCase()) {
            case "wallet":
                return onUpgradeWalletCommand(args);
            default:
                return super.onCommand(args);
        }
    }

    private boolean onInstallCommand(String[] args) {
/*        if (args.length < 2) {
            ConsoleHelper.writeLine("error");
            return true;
        }
        String pluginName = args[1];
        String address = string.Format(Settings.getDefaultInstance().getPluginURL(), pluginName,
                typeof(Plugin).Assembly.GetVersion());
        var fileName = Path.Combine("Plugins", $"{pluginName}.zip");
        Directory.CreateDirectory("Plugins");
        ConsoleHelper.writeLine(String.format("Downloading from {0}", address));
        WebClient wc = new WebClient();
        wc.DownloadFile(address, fileName);

        try {
            ZipFile.ExtractToDirectory(fileName, ".");
        } catch (IOException e) {
            ConsoleHelper.writeLine("Plugin already exist.");
            return true;
        } finally {
            File.Delete(fileName);
        }
        ConsoleHelper.writeLine("Install successful, please restart neo-cli.");*/
        return true;
    }

    private boolean onUnInstallCommand(String[] args) {
/*        if (args.length < 2) {
            ConsoleHelper.writeLine("error");
            return true;
        }
        String pluginName = args[1];
        Directory.Delete(Path.Combine("Plugins", pluginName), true);
        File.Delete(Path.Combine("Plugins", $"{pluginName}.dll"));
        ConsoleHelper.writeLine("Uninstall successful, please restart neo-cli.");*/
        return true;
    }

    private boolean onUpgradeWalletCommand(String[] args) {
        if (args.length < 3) {
            ConsoleHelper.writeLine("error");
            return true;
        }
        String path = args[2];
        String fileName = new File(path).getName();
        if (fileName.substring(fileName.lastIndexOf("."), fileName.length()).equals(".db3")) {
            ConsoleHelper.writeLine("Can't upgrade the wallet file.");
            return true;
        }
        if (!new File(path).exists()) {
            ConsoleHelper.writeLine("File does not exist.");
            return true;
        }
        String password = readPassword("password");
        if (password.length() == 0) {
            ConsoleHelper.writeLine("cancelled");
            return true;
        }
        String path_new = path.substring(0, path.lastIndexOf(".")) + ".json";
        NEP6Wallet.migrate(getIndexer(), path_new, path, password).save();
        ConsoleHelper.writeLine(String.format("Wallet file upgrade complete. New wallet file has been " +
                "auto-saved at: {0}", path_new));
        return true;
    }

    private static Wallet openWallet(WalletIndexer indexer, String path, String password) {
        String fileName = new File(path).getName();
        if (fileName.substring(fileName.lastIndexOf("."), fileName.length()).equals(".db3")) {
            return UserWallet.open(indexer, path, password);
        } else {
            NEP6Wallet nep6wallet = new NEP6Wallet(indexer, path);
            nep6wallet.unlock(password);
            return nep6wallet;
        }
    }

    private static void writeLineWithoutFlicker(String message, int maxWidth) {
        if (message.length() > 0) ConsoleHelper.write(message);
        int spacesToErase = maxWidth - message.length();
        if (spacesToErase < 0) spacesToErase = 0;
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < spacesToErase; i++) {
            builder.append(" ");
        }
        ConsoleHelper.writeLine(builder.toString());
    }

    private static void writeLineWithoutFlicker() {
        writeLineWithoutFlicker("", 80);
    }
}