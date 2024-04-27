package com.example.myapplication;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import android.util.Log;
import android.widget.TextView;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import io.grpc.Grpc;
import io.grpc.ManagedChannel;
import io.grpc.TlsChannelCredentials;
import org.hyperledger.fabric.client.CommitException;
import org.hyperledger.fabric.client.CommitStatusException;
import org.hyperledger.fabric.client.Contract;
import org.hyperledger.fabric.client.EndorseException;
import org.hyperledger.fabric.client.Gateway;
import org.hyperledger.fabric.client.GatewayException;
import org.hyperledger.fabric.client.SubmitException;
import org.hyperledger.fabric.client.identity.Identities;
import org.hyperledger.fabric.client.identity.Identity;
import org.hyperledger.fabric.client.identity.Signer;
import org.hyperledger.fabric.client.identity.Signers;
import org.hyperledger.fabric.client.identity.X509Identity;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.cert.CertificateException;
import java.time.Instant;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private static final String MSP_ID = "Org1MSP";
    private static final String CHANNEL_NAME = "mychannel";
    private static final String CHAINCODE_NAME = "basic";

    private static final Path CRYPTO_PATH = Paths.get("crypto");
    private static final Path CERT_DIR_PATH = CRYPTO_PATH.resolve(Paths.get("users/User1@org1.example.com/msp/signcerts/cert.pem"));
    private static final Path KEY_DIR_PATH = CRYPTO_PATH.resolve(Paths.get("users/User1@org1.example.com/msp/keystore/a633924bdd7104929921492b759a790c5e8ba25367becd87cf1c1667bb68b36e_sk"));
    private static final Path TLS_CERT_PATH = CRYPTO_PATH.resolve(Paths.get("peers/peer0.org1.example.com/tls/ca.crt"));

    private static final String PEER_ENDPOINT = "192.168.1.207:7051";
    private static final String OVERRIDE_AUTH = "peer0.org1.example.com";

    private Contract contract;
    private final String assetId = "asset" + Instant.now().toEpochMilli();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private TextView displayTextView;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        File filesDir = getFilesDir();
        displayTextView = (TextView) findViewById(R.id.textView);

        try {
            ManagedChannel channel = newGrpcConnection();

            Gateway.Builder builder = Gateway.newInstance().identity(newIdentity()).signer(newSigner()).connection(channel)
                    .evaluateOptions(options -> options.withDeadlineAfter(5, TimeUnit.SECONDS))
                    .endorseOptions(options -> options.withDeadlineAfter(15, TimeUnit.SECONDS))
                    .submitOptions(options -> options.withDeadlineAfter(5, TimeUnit.SECONDS))
                    .commitStatusOptions(options -> options.withDeadlineAfter(1, TimeUnit.MINUTES));

            try (Gateway gateway = builder.connect()) {
                HyperledgerGateway hyperledgerGateway = new HyperledgerGateway(gateway);
                hyperledgerGateway.run();
            } finally {
                channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error occurred", e);
        }
    }

    private ManagedChannel newGrpcConnection() throws IOException {
        var credentials = TlsChannelCredentials.newBuilder()
                .trustManager(convertStringToInputStream(getString(R.string.tls_cert)))
                .build();
        return Grpc.newChannelBuilder(PEER_ENDPOINT, credentials)
                .overrideAuthority(OVERRIDE_AUTH)
                .build();
    }

    private Identity newIdentity() throws IOException, CertificateException {
        try (var certReader = convertStringToBufferedReader(getString(R.string.cert))) {
            var certificate = Identities.readX509Certificate(certReader);
            return new X509Identity(MSP_ID, certificate);
        }
    }

    private Signer newSigner() throws IOException, InvalidKeyException {
        try (var keyReader = convertStringToBufferedReader(getString(R.string.key))) {
            var privateKey = Identities.readPrivateKey(keyReader);
            return Signers.newPrivateKeySigner(privateKey);
        }
    }

    private Path getFirstFilePath(Path dirPath) throws IOException {
        try (java.util.stream.Stream<Path> keyFiles = Files.list(dirPath)) {
            return keyFiles.findFirst()
                    .orElseThrow(() -> new IOException("Directory is empty"));
        }
    }

    private class HyperledgerGateway {
        public HyperledgerGateway(Gateway gateway) {
            var network = gateway.getNetwork(CHANNEL_NAME);
            contract = network.getContract(CHAINCODE_NAME);
        }

        public void run() throws GatewayException, CommitException {
            try {
                // Initialize a set of asset data on the ledger using the chaincode 'InitLedger' function.
                initLedger();

                // Return all the current assets on the ledger.
                getAllAssets();

                // Create a new asset on the ledger.
                createAsset();

                // Update an existing asset asynchronously.
                transferAssetAsync();

                // Get the asset details by assetID.
                readAssetById();

                // Update an asset which does not exist.
                updateNonExistentAsset();
            } catch (EndorseException | SubmitException | CommitStatusException e) {
                Log.e(TAG, "Error occurred", e);
            }
        }

        private void initLedger() throws EndorseException, SubmitException, CommitStatusException, CommitException {
            displayTextView.append( "\n--> Submit Transaction: InitLedger, function creates the initial set of assets on the ledger");

            contract.submitTransaction("InitLedger");

            displayTextView.append( "*** Transaction committed successfully");
        }
    }

    /**
     * Evaluate a transaction to query ledger state.
     */
    private void getAllAssets() throws GatewayException {
        displayTextView.append("\n--> Evaluate Transaction: GetAllAssets, function returns all the current assets on the ledger");

        var result = contract.evaluateTransaction("GetAllAssets");

        displayTextView.append("*** Result: " + prettyJson(result));
    }

    private String prettyJson(final byte[] json) {
        return prettyJson(new String(json, StandardCharsets.UTF_8));
    }

    private String prettyJson(final String json) {
        var parsedJson = JsonParser.parseString(json);
        return gson.toJson(parsedJson);
    }

    /**
     * Submit a transaction synchronously, blocking until it has been committed to
     * the ledger.
     */
    private void createAsset() throws EndorseException, SubmitException, CommitStatusException, CommitException {
        displayTextView.append("\n--> Submit Transaction: CreateAsset, creates new asset with ID, Color, Size, Owner and AppraisedValue arguments");

        contract.submitTransaction("CreateAsset", assetId, "yellow", "5", "Tom", "1300");

        displayTextView.append("*** Transaction committed successfully");
    }

    /**
     * Submit transaction asynchronously, allowing the application to process the
     * smart contract response (e.g. update a UI) while waiting for the commit
     * notification.
     */
    private void transferAssetAsync() throws EndorseException, SubmitException, CommitStatusException {
        displayTextView.append("\n--> Async Submit Transaction: TransferAsset, updates existing asset owner");

        var commit = contract.newProposal("TransferAsset")
                .addArguments(assetId, "Saptha")
                .build()
                .endorse()
                .submitAsync();

        var result = commit.getResult();
        var oldOwner = new String(result, StandardCharsets.UTF_8);

        displayTextView.append("*** Successfully submitted transaction to transfer ownership from " + oldOwner + " to Saptha");
        displayTextView.append("*** Waiting for transaction commit");

        var status = commit.getStatus();
        if (!status.isSuccessful()) {
            throw new RuntimeException("Transaction " + status.getTransactionId() +
                    " failed to commit with status code " + status.getCode());
        }

        displayTextView.append("*** Transaction committed successfully");
    }

    private void readAssetById() throws GatewayException {
        displayTextView.append("\n--> Evaluate Transaction: ReadAsset, function returns asset attributes");

        var evaluateResult = contract.evaluateTransaction("ReadAsset", assetId);

        displayTextView.append("*** Result:" + prettyJson(evaluateResult));
    }

    /**
     * submitTransaction() will throw an error containing details of any error
     * responses from the smart contract.
     */
    private void updateNonExistentAsset() {
        try {
            displayTextView.append("\n--> Submit Transaction: UpdateAsset asset70, asset70 does not exist and should return an error");

            contract.submitTransaction("UpdateAsset", "asset70", "blue", "5", "Tomoko", "300");

            displayTextView.append("******** FAILED to return an error");
        } catch (EndorseException | SubmitException | CommitStatusException e) {
            displayTextView.append("*** Successfully caught the error: ");
            e.printStackTrace(System.out);
            displayTextView.append("Transaction ID: " + e.getTransactionId());

            var details = e.getDetails();
            if (!details.isEmpty()) {
                displayTextView.append("Error Details:");
                for (var detail : details) {
                    displayTextView.append("- address: " + detail.getAddress() + ", mspId: " + detail.getMspId()
                            + ", message: " + detail.getMessage());
                }
            }
        } catch (CommitException e) {
            displayTextView.append("*** Successfully caught the error: " + e);
            e.printStackTrace(System.out);
            displayTextView.append("Transaction ID: " + e.getTransactionId());
            displayTextView.append("Status code: " + e.getCode());
        }
    }
    public static BufferedReader convertStringToBufferedReader(String inputString) {
        try {
            // Convert string to InputStream
            InputStream inputStream = new ByteArrayInputStream(inputString.getBytes());

            // Wrap InputStream in InputStreamReader
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);

            // Wrap InputStreamReader in BufferedReader

            // Return the created BufferedReader
            return new BufferedReader(inputStreamReader);
        } catch (Exception e) {
            // Handle any exceptions
            e.printStackTrace();
            return null;
        }
    }
    public static InputStream convertStringToInputStream(String text) {
        // Convert the string to bytes
        byte[] bytes = text.getBytes();

        // Create a ByteArrayInputStream from the bytes
        return new ByteArrayInputStream(bytes);
    }
}