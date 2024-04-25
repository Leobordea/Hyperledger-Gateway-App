package com.example.myapplication;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.hyperledger.fabric.client.CommitException;
import org.hyperledger.fabric.client.CommitStatusException;
import org.hyperledger.fabric.client.Contract;
import org.hyperledger.fabric.client.EndorseException;
import org.hyperledger.fabric.client.Gateway;
import org.hyperledger.fabric.client.GatewayException;
import org.hyperledger.fabric.client.SubmitException;
import org.hyperledger.fabric.client.identity.Identities;
import org.hyperledger.fabric.client.identity.Signer;
import org.hyperledger.fabric.client.identity.Signers;
import org.hyperledger.fabric.client.identity.X509Identity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.cert.CertificateException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import io.grpc.Grpc;
import io.grpc.ManagedChannel;
import io.grpc.TlsChannelCredentials;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private static final String MSP_ID = System.getenv().getOrDefault("MSP_ID", "Org1MSP");
    private static final String CHANNEL_NAME = System.getenv().getOrDefault("CHANNEL_NAME", "mychannel");
    private static final String CHAINCODE_NAME = System.getenv().getOrDefault("CHAINCODE_NAME", "basic");

    private static final Path CRYPTO_PATH = Paths.get("crypto");
    private static final Path CERT_DIR_PATH = CRYPTO_PATH.resolve(Paths.get("users/User1@org1.example.com/msp/signcerts/cert.pem"));
    private static final Path KEY_DIR_PATH = CRYPTO_PATH.resolve(Paths.get("users/User1@org1.example.com/msp/keystore/a82c7dde92a6bafdc5eaa8d230673cbf2ea262042a6a017713d275738fa5e350_sk"));
    private static final Path TLS_CERT_PATH = CRYPTO_PATH.resolve(Paths.get("peers/peer0.org1.example.com/tls/ca.crt"));

    private static final String PEER_ENDPOINT = "192.168.43.218:7051";
    private static final String OVERRIDE_AUTH = "peer0.org1.example.com";

    private Contract contract;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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
                .trustManager(this.getAssets().open(String.valueOf(TLS_CERT_PATH)))
                .build();
        return Grpc.newChannelBuilder(PEER_ENDPOINT, credentials)
                .overrideAuthority(OVERRIDE_AUTH)
                .build();
    }

    private X509Identity newIdentity() throws IOException, CertificateException {
        try (InputStream certReader = this.getAssets().open(String.valueOf(CERT_DIR_PATH))) {
            return new X509Identity(MSP_ID, Identities.readX509Certificate(certReader.toString()));
        }
    }

    private Signer newSigner() throws IOException, InvalidKeyException {
        try (InputStream keyReader = this.getAssets().open(String.valueOf(KEY_DIR_PATH))) {
            return Signers.newPrivateKeySigner(Identities.readPrivateKey(keyReader.toString()));
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
                initLedger();
            } catch (EndorseException | SubmitException | CommitStatusException e) {
                Log.e(TAG, "Error occurred", e);
            }
        }

        private void initLedger() throws EndorseException, SubmitException, CommitStatusException, CommitException {
            Log.i(TAG, "\n--> Submit Transaction: InitLedger, function creates the initial set of assets on the ledger");

            contract.submitTransaction("InitLedger");

            Log.i(TAG, "*** Transaction committed successfully");
        }
    }
}