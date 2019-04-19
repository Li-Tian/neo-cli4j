package neo.shell;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import akka.pattern.Patterns;
import akka.util.Timeout;
import neo.Fixed8;
import neo.NeoSystem;
import neo.UInt160;
import neo.csharp.Uint;
import neo.exception.InvalidOperationException;
import neo.ledger.Blockchain;
import neo.ledger.RelayResultReason;
import neo.network.p2p.payloads.ClaimTransaction;
import neo.network.p2p.payloads.CoinReference;
import neo.network.p2p.payloads.Transaction;
import neo.network.p2p.payloads.TransactionAttribute;
import neo.network.p2p.payloads.TransactionOutput;
import neo.persistence.Snapshot;
import neo.smartcontract.ContractParametersContext;
import neo.wallets.Wallet;
import scala.concurrent.Await;
import scala.concurrent.Future;

/**
 * @author doubi.liu
 * @version V1.0
 * @Title: Coins
 * @Package neo.shell
 * @Description: (用一句话描述该文件做什么)
 * @date Created in 10:30 2019/3/29
 */
public class Coins {
    private Wallet current_wallet;
    private NeoSystem system;
    public static int MAX_CLAIMS_AMOUNT = 50;

    public Coins(Wallet wallet, NeoSystem system) {
        this.current_wallet = wallet;
        this.system = system;
    }

    public Fixed8 unavailableBonus() {
        Snapshot snapshot = Blockchain.singleton().getSnapshot();
        Uint height = snapshot.getHeight().add(Uint.ONE);
        Fixed8 unavailable;

        try {
            //LINQ START
/*            unavailable = snapshot.calculateBonus(current_wallet.findUnspentCoins().Where(p
                    = > p.Output.AssetId.Equals(Blockchain.GoverningToken.hash())).Select(p = > p
                    .Reference),height);*/
            unavailable = snapshot.calculateBonus(StreamSupport.stream(current_wallet
                    .findUnspentCoins().spliterator(),false)
                    .filter(p-> p.output.assetId.equals(Blockchain.GoverningToken.hash()))
                    .map(p-> p.reference).collect(Collectors.toList()),height);
            //LINQ END
        } catch (Exception e) {
            unavailable = Fixed8.ZERO;
        }

        return unavailable;
    }


    public Fixed8 availableBonus() {
        Snapshot snapshot = Blockchain.singleton().getSnapshot();
        //LINQ START
        //return snapshot.calculateBonus(current_wallet.getUnclaimedCoins().Select(p = > p
        //        .Reference));
        return snapshot.calculateBonus(StreamSupport.stream(current_wallet.getUnclaimedCoins()
                .spliterator(),false).map(p->p.reference).collect(Collectors.toList()));
        //LINQ END
    }

    public ClaimTransaction claim() {
        return claim(null);
    }


    public ClaimTransaction claim(UInt160 change_address) {

        if (Fixed8.ZERO.equals(this.availableBonus())) {
            System.out.println("no gas to claim");
            return null;
        }
        //LINQ START
/*        CoinReference[] claims = current_wallet.getUnclaimedCoins().Select(p = > p.Reference)
        .toArray();*/
        CoinReference[] claims = StreamSupport.stream(current_wallet.getUnclaimedCoins()
                .spliterator(), false).map(p -> p.reference).toArray(CoinReference[]::new);
        //LINQ END
        if (claims.length == 0) return null;

        Snapshot snapshot = Blockchain.singleton().getSnapshot();

            ClaimTransaction tx = new ClaimTransaction();
            //LINQ START
            //tx.claims=claims.Take(MAX_CLAIMS_AMOUNT).ToArray();
            tx.claims= Arrays.asList(claims).stream().limit(MAX_CLAIMS_AMOUNT).toArray(CoinReference[]::new);
            //LINQ END
            tx.attributes = new TransactionAttribute[0];
            tx.inputs = new CoinReference[0];
            TransactionOutput temp=new TransactionOutput();
        temp.assetId=Blockchain.UtilityToken.hash();
        //LINQ START
        //Value = snapshot.CalculateBonus(claims.Take(MAX_CLAIMS_AMOUNT)
        temp.value = snapshot.calculateBonus(Arrays.asList(claims).stream().limit
                (MAX_CLAIMS_AMOUNT).collect(Collectors.toList()));
        //LINQ END
        temp.scriptHash = change_address!=null?change_address:current_wallet.getChangeAddress();
        tx.outputs = new TransactionOutput[]{temp};
            return (ClaimTransaction) signTransaction(tx);

    }


    public ClaimTransaction[] claimAll() {
       return  claimAll(null);
    }

    public ClaimTransaction[] claimAll(UInt160 change_address) {

        if (this.availableBonus() == Fixed8.ZERO) {
            System.out.println("no gas to claim");
            return null;
        }

        //LINQ START
/*        CoinReference[] claims = current_wallet.getUnclaimedCoins().Select(p = > p.Reference)
        .toArray();*/
        CoinReference[] claims = StreamSupport.stream(current_wallet.getUnclaimedCoins()
                .spliterator(), false).map(p -> p.reference).toArray(CoinReference[]::new);
        //LINQ END
        if (claims.length == 0) return null;

        Snapshot snapshot = Blockchain.singleton().getSnapshot();
        int claim_count = (claims.length - 1) / MAX_CLAIMS_AMOUNT + 1;
        List<ClaimTransaction> txs = new ArrayList<ClaimTransaction>();
        if (claim_count > 1) {
            System.out.println("total claims: {claims.Length}, processing(0/{claim_count})...");
        }
        for (int i = 0; i < claim_count; i++) {
            if (i > 0) {
                System.out.println("{i * MAX_CLAIMS_AMOUNT} claims processed({i}/{claim_count})...");
            }
            ClaimTransaction tx = new ClaimTransaction();
            //LINQ START
            //tx.claims=claims.Skip(i * MAX_CLAIMS_AMOUNT).Take(MAX_CLAIMS_AMOUNT).ToArray();
            tx.claims=Arrays.asList(claims).stream().skip(i * MAX_CLAIMS_AMOUNT).limit(MAX_CLAIMS_AMOUNT)
                    .toArray(CoinReference[]::new);
            //LINQ END
            tx.attributes = new TransactionAttribute[0];
            tx.inputs=new CoinReference[0];
            TransactionOutput temp=new TransactionOutput();
            temp.assetId=Blockchain.UtilityToken.hash();
            //LINQ START
            //temp.value=snapshot.calculateBonus(claims.Skip(i * MAX_CLAIMS_AMOUNT).Take
                    //(MAX_CLAIMS_AMOUNT));
            temp.value=snapshot.calculateBonus(Arrays.asList(claims).stream().skip(i*MAX_CLAIMS_AMOUNT)
                    .limit(MAX_CLAIMS_AMOUNT).collect(Collectors.toList()));
            //LINQ END
            temp.scriptHash=change_address!=null?change_address:current_wallet.getChangeAddress();
            tx.outputs=new TransactionOutput[]{temp};
            if ((tx = (ClaimTransaction) signTransaction(tx)) != null) {
                txs.add(tx);
            } else {
                break;
            }
        }

        return txs.toArray(new ClaimTransaction[0]);
    }


    private Transaction signTransaction(Transaction tx) {
        if (tx == null) {
            System.out.println("no transaction specified");
            return null;
        }
        ContractParametersContext context;

        try {
            context = new ContractParametersContext(tx);
        } catch (InvalidOperationException e) {
            System.out.println("unsynchronized block");
            return null;
        }

        current_wallet.sign(context);
        if (context.completed()) {
            context.verifiable.setWitnesses(context.getWitnesses());
            current_wallet.applyTransaction(tx);
            //此处与C# AKKA 实现不同，60s暂定
            Timeout timeout = Timeout.create(Duration.ofSeconds(60));
            Future<Object> future= Patterns.ask(system.blockchain, tx,timeout);
            try {
                RelayResultReason result = (RelayResultReason)Await.result(future, timeout.duration());
                boolean relay_result = result.equals(RelayResultReason.Succeed);
                if (relay_result) {
                    return tx;
                } else {
                    System.out.println("Local Node could not relay transaction: {tx.Hash.ToString()}");
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            System.out.println("Incomplete Signature: {context.ToString()}");
        }
        return null;
    }
}