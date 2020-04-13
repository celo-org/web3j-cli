/*
 * Copyright 2019 Web3 Labs Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.web3j.console;

import static org.web3j.codegen.SolidityFunctionWrapperGenerator.COMMAND_SOLIDITY;
import static org.web3j.console.project.ProjectCreator.COMMAND_NEW;
import static org.web3j.console.project.ProjectImporter.COMMAND_IMPORT;
import static org.web3j.console.project.UnitTestCreator.COMMAND_GENERATE_TESTS;
import static org.web3j.crypto.Hash.sha256;
import static org.web3j.utils.Collection.tail;

import java.math.BigInteger;

import org.web3j.codegen.Console;
import org.web3j.codegen.SolidityFunctionWrapperGenerator;
import org.web3j.codegen.TruffleJsonFunctionWrapperGenerator;
import org.web3j.console.account.AccountManager;
import org.web3j.console.config.CliConfig;
import org.web3j.console.project.ProjectCreator;
import org.web3j.console.project.ProjectImporter;
import org.web3j.console.project.UnitTestCreator;
import org.web3j.console.update.Updater;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.ECKeyPair;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.gas.StaticGasProvider;
import org.web3j.utils.Version;


/** Main entry point for running command line utilities. */
public class Runner {

    private static final String USAGE =
            "Usage: web3j version|wallet|solidity|new|import|generate-tests|audit|account ...";

    private static final String LOGO =
            "\n" // generated at http://patorjk.com/software/taag
                    + "              _      _____ _     _        \n"
                    + "             | |    |____ (_)   (_)       \n"
                    + "__      _____| |__      / /_     _   ___  \n"
                    + "\\ \\ /\\ / / _ \\ '_ \\     \\ \\ |   | | / _ \\ \n"
                    + " \\ V  V /  __/ |_) |.___/ / | _ | || (_) |\n"
                    + "  \\_/\\_/ \\___|_.__/ \\____/| |(_)|_| \\___/ \n"
                    + "                         _/ |             \n"
                    + "                        |__/              \n";

    public static void main(String[] args) throws Exception {
        System.out.println(LOGO);
        CliConfig config = CliConfig.getConfig(CliConfig.getWeb3jConfigPath().toFile());
        Updater updater = new Updater(config);
        updater.promptIfUpdateAvailable();
        Thread updateThread = new Thread(updater::onlineUpdateCheck);
        updateThread.setDaemon(true);
        updateThread.start();

        if (args.length < 1) {
            Console.exitError(USAGE);
        } else {
            Web3j web3j;
            switch (args[0]) {
                case "wallet":
                    WalletRunner.run(tail(args));
                    break;
                case COMMAND_SOLIDITY:
                    SolidityFunctionWrapperGenerator.main(tail(args));
                    break;
                case "truffle":
                    TruffleJsonFunctionWrapperGenerator.run(tail(args));
                    break;
                case COMMAND_NEW:
                    ProjectCreator.main(tail(args));
                    break;
                case COMMAND_IMPORT:
                    ProjectImporter.main(tail(args));
                    break;
                case "version":
                    Console.exitSuccess(
                            "Version: "
                                    + Version.getVersion()
                                    + "\n"
                                    + "Build timestamp: "
                                    + Version.getTimestamp());
                    break;
                case "audit":
                    ContractAuditor.main(tail(args));
                    break;
                case "account":
                    AccountManager.main(config, tail(args));
                    break;
                case COMMAND_GENERATE_TESTS:
                    UnitTestCreator.main(tail(args));
                    break;
                case "block":
                    web3j =
                            Web3j.build(
                                    new HttpService("https://alfajores-forno.celo-testnet.org"));
                    BigInteger blockNumber = new BigInteger("60655");
                    EthBlock.Block block =
                            web3j.ethGetBlockByNumber(
                                            DefaultBlockParameter.valueOf(blockNumber), true)
                                    .send()
                                    .getResult();
                    EthBlock.TransactionResult<EthBlock.TransactionObject> result =
                            block.getTransactions().get(0);
                    System.out.println(
                            String.format(
                                    "Fee Currency: %s \nGateWayRecipient: %s \nGateWayFee %s \n",
                                    result.get().getFeeCurrency(),
                                    result.get().getGatewayFeeRecipient(),
                                    result.get().getGatewayFee()));
                    Console.exitSuccess("Fee Currency: \n" + result.get().getFeeCurrency());
                case "transfer":
                    // TODO [amyslawson] pull logic into seprate java file and add test
                    String to = args[1]; // eg. 0x7963325c601bec69b9b43dd3ebd07c4911106c93
                    BigInteger value = new BigInteger(args[2]);
                    web3j =
                            Web3j.build(
                                    new HttpService("https://alfajores-forno.celo-testnet.org"));
                    // TODO [amyslawson] sign with sender
                    ECKeyPair ecKeyPair = ECKeyPair.create(sha256("test_sk".getBytes()));
                    Credentials credentials = Credentials.create(ecKeyPair);
                    // TODO [amyslawson] change address to be downloaded based on env (pull in
                    // protocol
                    // download_artifacts.sh) and prepopulated in Contracts.java deployedAddresses
                    // map
                    StableToken stableToken =
                            StableToken.load(
                                    // TODO [amyslawson] move contract java wrapper generation into
                                    // web3j
                                    "0xa561131a1C8aC25925FB848bCa45A74aF61e5A38",
                                    web3j,
                                    credentials,
                                    new StaticGasProvider(
                                            // TODO [amyslawson] get gasPriceMinimum from contract
                                            BigInteger.valueOf(2000000000),
                                            BigInteger.valueOf(20000000)));

                    String name = stableToken.name().send();
                    // TODO [amyslawson] remove print statements and convert to tests
                    System.out.println(
                            "BEFORE SENDER: "
                                    + stableToken
                                            .balanceOf(credentials.getAddress())
                                            .send());
                    System.out.println(
                            "BEFORE RECEVER: "
                                    + stableToken
                                            .balanceOf(to)
                                            .send());
                    stableToken
                            .transfer(to, value)
                            .send();
                    System.out.println(
                            "AFTER RECEVER: "
                                    + stableToken
                                            .balanceOf(to)
                                            .send());
                    Console.exitSuccess(
                            String.format("Exchanged: %s %s from to %s", value, name, to));
                default:
                    Console.exitError(USAGE);
            }
        }
        config.save();
        Console.exitSuccess();
    }
}
