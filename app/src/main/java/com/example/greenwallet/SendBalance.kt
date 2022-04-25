package com.example.greenwallet

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.findFragment
import androidx.lifecycle.MutableLiveData
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.example.greenwallet.core.Configuration
import com.example.greenwallet.core.Erc20Token
import com.example.greenwallet.core.ShowTxType
import com.example.greenwallet.databinding.ActivitySendBalanceBinding
import com.google.zxing.integration.android.IntentIntegrator
import io.horizontalsystems.erc20kit.core.Erc20Kit
import io.horizontalsystems.ethereumkit.core.EthereumKit
import io.horizontalsystems.ethereumkit.core.eip1559.Eip1559GasPriceProvider
import io.horizontalsystems.ethereumkit.core.eip1559.FeeHistory
import io.horizontalsystems.ethereumkit.core.signer.Signer
import io.horizontalsystems.ethereumkit.core.toHexString
import io.horizontalsystems.ethereumkit.models.*
import io.horizontalsystems.ethereumkit.sample.SingleLiveEvent
import io.horizontalsystems.ethereumkit.sample.core.Erc20Adapter
import io.horizontalsystems.ethereumkit.sample.core.EthereumAdapter
import io.horizontalsystems.ethereumkit.sample.core.TransactionRecord
import io.horizontalsystems.hdwalletkit.Mnemonic
import io.horizontalsystems.oneinchkit.OneInchKit
import io.horizontalsystems.uniswapkit.UniswapKit
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import java.math.BigDecimal
import java.util.logging.Logger


class SendBalance : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivitySendBalanceBinding

    private val disposables = CompositeDisposable()

    private lateinit var ethereumKit: EthereumKit
    private lateinit var ethereumAdapter: EthereumAdapter
    private lateinit var erc20Adapter: Erc20Adapter

    private lateinit var signer: Signer

    private lateinit var myAddress: String
    private lateinit var myValue: String


    val estimatedGas = SingleLiveEvent<String>()
    val sendStatus = SingleLiveEvent<Throwable?>()
    private var gasPrice: GasPrice = GasPrice.Legacy(20_000_000_000)

    private val logger = Logger.getLogger("SendBalance")

    val fromToken: Erc20Token? = Configuration.erc20Tokens[0]
    val toToken: Erc20Token? = Configuration.erc20Tokens[1]


    private fun createKit(): EthereumKit {
        val rpcSource: RpcSource?
        val transactionSource: TransactionSource?

        when (Configuration.chain) {
            Chain.BinanceSmartChain -> {
                transactionSource = TransactionSource.bscscan(Configuration.bscScanKey)
//                rpcSource = if (Configuration.webSocket)
//                    RpcSource.binanceSmartChainWebSocket()
//                else
                rpcSource = RpcSource.binanceSmartChainHttp()
            }
//            Chain.Ethereum -> {
//                transactionSource = TransactionSource.ethereumEtherscan(Configuration.etherscanKey)
//                rpcSource = if (Configuration.webSocket)
//                    RpcSource.ethereumInfuraWebSocket(Configuration.infuraProjectId, Configuration.infuraSecret)
//                else
//                    RpcSource.ethereumInfuraHttp(Configuration.infuraProjectId, Configuration.infuraSecret)
//            }
//            Chain.EthereumRopsten -> {
//                transactionSource = TransactionSource.ropstenEtherscan(Configuration.etherscanKey)
//                rpcSource = if (Configuration.webSocket)
//                    RpcSource.ropstenInfuraWebSocket(Configuration.infuraProjectId, Configuration.infuraSecret)
//                else
//                    RpcSource.ropstenInfuraHttp(Configuration.infuraProjectId, Configuration.infuraSecret)
//            }
            else -> {
                rpcSource = null
                transactionSource = null
            }
        }

        checkNotNull(rpcSource) {
            throw Exception("Could not get rpcSource!")
        }

        checkNotNull(transactionSource) {
            throw Exception("Could not get transactionSource!")
        }

        val words = Configuration.defaultsWords.split(" ")
        return EthereumKit.getInstance(
            this.applicationContext as Application, words, "",
            Configuration.chain, rpcSource, transactionSource,
            Configuration.walletId
        )
    }
    val lastBlockHeight = MutableLiveData<Long>()
    val syncState = MutableLiveData<EthereumKit.SyncState>()
    val transactionsSyncState = MutableLiveData<EthereumKit.SyncState>()
    val erc20SyncState = MutableLiveData<EthereumKit.SyncState>()
    val erc20TransactionsSyncState = MutableLiveData<EthereumKit.SyncState>()

    val erc20TokenBalance = MutableLiveData<BigDecimal>()
    val showTxTypeLiveData = MutableLiveData<ShowTxType>()

    private fun updateLastBlockHeight() {
        lastBlockHeight.postValue(ethereumKit.lastBlockHeight)
    }

    private fun updateState() {
        syncState.postValue(ethereumAdapter.syncState)
    }

    private fun updateTransactionsSyncState() {
        transactionsSyncState.postValue(ethereumAdapter.transactionsSyncState)
    }

    private fun updateErc20State() {
        erc20SyncState.postValue(erc20Adapter.syncState)
    }

    private fun updateErc20TransactionsSyncState() {
        erc20TransactionsSyncState.postValue(erc20Adapter.transactionsSyncState)
    }

    val balance = MutableLiveData<BigDecimal>()

    private fun updateBalance() {
        balance.postValue(ethereumAdapter.balance)
    }

    private fun updateErc20Balance() {
        erc20TokenBalance.postValue(erc20Adapter.balance)
    }


    private var ethTxs = listOf<TransactionRecord>()
    private var erc20Txs = listOf<TransactionRecord>()

    private fun updateEthTransactions() {

    }

    private fun updateErc20Transactions() {
        erc20Adapter.transactions()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { list: List<TransactionRecord> ->
                erc20Txs = list
                updateTransactionList()
            }.let {
                disposables.add(it)
            }
    }
    private var showTxType = ShowTxType.Eth
    val transactions = MutableLiveData<List<TransactionRecord>>()

    private fun updateTransactionList() {
        val list = when (showTxType) {
            ShowTxType.Eth -> ethTxs
            ShowTxType.Erc20 -> erc20Txs
        }
        transactions.value = list
    }


    //
    // Ethereum
    //

    fun refresh() {
        ethereumAdapter.refresh()
        erc20Adapter.refresh()
    }

    fun clear() {
        EthereumKit.clear(this.applicationContext, Configuration.chain, Configuration.walletId)
        Erc20Kit.clear(this.applicationContext, Configuration.chain, Configuration.walletId)
        init()
    }

    private lateinit var uniswapKit: UniswapKit

    fun filterTransactions(ethTx: Boolean) {
        showTxType = if (ethTx) {
            updateEthTransactions()
            ShowTxType.Eth
        } else {
            updateErc20Transactions()
            ShowTxType.Erc20
        }
        showTxTypeLiveData.postValue(showTxType)
    }

    fun init() {
        EthereumKit.init()
        val words = Configuration.defaultsWords.split(" ")
        val seed = Mnemonic().toSeed(words)
        signer = Signer.getInstance(seed, Configuration.chain)
        ethereumKit = createKit()
        ethereumAdapter = EthereumAdapter(ethereumKit, signer)
        erc20Adapter = Erc20Adapter(
            this.applicationContext, fromToken ?: toToken
        ?: Configuration.erc20Tokens.first(), ethereumKit, signer)
        uniswapKit = UniswapKit.getInstance(ethereumKit)

        Erc20Kit.addTransactionSyncer(ethereumKit)
        Erc20Kit.addDecorator(ethereumKit)
        UniswapKit.addDecorator(ethereumKit)
        OneInchKit.addDecorator(ethereumKit)

        updateBalance()
        updateErc20Balance()
        updateState()
        updateTransactionsSyncState()
        updateErc20State()
        updateErc20TransactionsSyncState()
        updateLastBlockHeight()

        filterTransactions(true)

        //
        // Ethereum
        //

        ethereumAdapter.lastBlockHeightFlowable.subscribe {
            updateLastBlockHeight()
            updateEthTransactions()
        }.let {
            disposables.add(it)
        }

        ethereumAdapter.transactionsFlowable.subscribe {
            updateEthTransactions()
        }.let {
            disposables.add(it)
        }

        ethereumAdapter.balanceFlowable.subscribe {
            updateBalance()
        }.let {
            disposables.add(it)
        }

        ethereumAdapter.syncStateFlowable.subscribe {
            updateState()
        }.let {
            disposables.add(it)
        }

        ethereumAdapter.transactionsSyncStateFlowable.subscribe {
            updateTransactionsSyncState()
        }.let {
            disposables.add(it)
        }


        //
        // ERC20
        //

        erc20Adapter.transactionsFlowable.subscribe {
            updateErc20Transactions()
        }.let {
            disposables.add(it)
        }

        erc20Adapter.balanceFlowable.subscribe {
            updateErc20Balance()
        }.let {
            disposables.add(it)
        }

        erc20Adapter.syncStateFlowable.subscribe {
            updateErc20State()
        }.let {
            disposables.add(it)
        }

        erc20Adapter.transactionsSyncStateFlowable.subscribe {
            updateErc20TransactionsSyncState()
        }.let {
            disposables.add(it)
        }

        ethereumAdapter.start()
        erc20Adapter.start()

        val eip1559GasPriceProvider = Eip1559GasPriceProvider(ethereumKit)

        eip1559GasPriceProvider
            .feeHistory(4, rewardPercentile = listOf(50))
            .subscribe({
                Log.e("AAA", "FeeHistory: $it")
                handle(it)
            }, {
                Log.e("AAA", "error: ${it.localizedMessage ?: it.message ?: it.javaClass.simpleName}")
            }).let { disposables.add(it) }

    }


    private fun handle(feeHistory: FeeHistory) {
        var recommendedBaseFee: Long? = null
        var recommendedPriorityFee: Long? = null

        feeHistory.baseFeePerGas.lastOrNull()?.let { currentBaseFee ->
            recommendedBaseFee = currentBaseFee
        }

        var priorityFeeSum: Long = 0
        var priorityFeesCount = 0
        feeHistory.reward.forEach { priorityFeeArray ->
            priorityFeeArray.firstOrNull()?.let { priorityFee ->
                priorityFeeSum += priorityFee
                priorityFeesCount += 1
            }
        }

        if (priorityFeesCount > 0) {
            recommendedPriorityFee = priorityFeeSum / priorityFeesCount
        }

        recommendedBaseFee?.let { baseFee ->
            recommendedPriorityFee?.let { tip ->

                gasPrice = GasPrice.Eip1559(baseFee + tip, tip)
                Log.e("AAA", "set gasPrice: $gasPrice")
            }

        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        init()

        binding = ActivitySendBalanceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navController = findNavController(R.id.nav_host_fragment_content_send_balance)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        binding.fab.setOnClickListener { view ->

//            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
//                .setAction("Action", null).show()
            finish()
        }
    }



    fun send(toAddress: String, amount: BigDecimal) {
        val gasLimit = 21000//21000

        val address = Address(toAddress)
        ethereumAdapter.send(address, amount, gasPrice, gasLimit.toLong())
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ fullTransaction ->
                //success
                logger.info("Successfully sent, hash: ${fullTransaction.transaction.hash.toHexString()}")

                sendStatus.value = null
            }, {
                logger.warning("Ether send failed: ${it.message}")
                sendStatus.value = it
            }).let { disposables.add(it) }

    }

    //
    // ERC20
    //

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null) {
            if (result.contents == null) {
                Toast.makeText(this, "Cancelado", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "El valor escaneado es: " + result.contents, Toast.LENGTH_LONG)
                    .show()

                //var fragment =
            //        fragmentManager.findFragmentById(R.id.SecondFragment) as SecondFragment
                //fragment.my_func(result.contents)

                findViewById<TextView>(R.id.lb_qr).setText("To:" + result.contents)
                //binding.root.findFragment<SecondFragment>().my_func(result.contents)
            myAddress = result.contents
            }

        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

        fun sendValueAddress () {
    //            findNavController().navigate(R.id.action_SecondFragment_to_FirstFragment)
            var sendAddress: String ="" // "0x6f95CE590872129aee367AC4EBcaFeB89F8090b2"
            var sendAmount: String = "" // "0.0001"

            var words = myAddress.split(";")
            for (s in words) {
                if(s.isEmpty()) {
                    return
                } else {
                    if (s.startsWith("0x"))
                        sendAddress = s

                    if(s.contains("."))
                        sendAmount=s
                }
            }

            if (sendAddress.isEmpty() || sendAmount.isEmpty())
                return

            (this as SendBalance?)
                ?.send(sendAddress, sendAmount.toBigDecimal())

        }



fun sendERC20(toAddress: String, amount: BigDecimal) {
        val gasLimit = estimatedGas.value?.toLongOrNull() ?: kotlin.run {
            sendStatus.value = Exception("No gas limit!!")
            return
        }

        erc20Adapter.send(Address(toAddress), amount, gasPrice, gasLimit)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ fullTransaction ->
                logger.info("Successfully sent, hash: ${fullTransaction.transaction.hash.toHexString()}")
                //success
                sendStatus.value = null
            }, {
                logger.warning("Erc20 send failed: ${it.message}")
                sendStatus.value = it
            }).let { disposables.add(it) }
    }



    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_send_balance)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }
}