package co.krypt.kryptonite.transport;

import android.util.Log;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.internal.StaticCredentialsProvider;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.DeleteMessageBatchRequest;
import com.amazonaws.services.sqs.model.DeleteMessageBatchRequestEntry;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.amazonaws.util.Base64;

import java.util.ArrayList;
import java.util.List;

import co.krypt.kryptonite.exception.TransportException;
import co.krypt.kryptonite.pairing.Pairing;
import co.krypt.kryptonite.protocol.NetworkMessage;

/**
 * Created by Kevin King on 12/3/16.
 * Copyright 2016. KryptCo, Inc.
 */

public class SQSTransport {
    private static String accessKey = "AKIAJMZJ3X6MHMXRF7QQ";
    private static String secretKey = "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";
    private static final String TAG = "SQSTransport";

    private static AmazonSQSClient getClient() {
        StaticCredentialsProvider creds = new StaticCredentialsProvider(new BasicAWSCredentials(accessKey, secretKey));
        return new AmazonSQSClient(creds);
    }

    private static String sendQueueURL(Pairing pairing) {
        return "https://sqs.us-east-1.amazonaws.com/911777333295/" + pairing.getUUIDString();
    }

    private static String recvQueueURL(Pairing pairing) {
        return "https://sqs.us-east-1.amazonaws.com/911777333295/" + pairing.getUUIDString() + "-responder";
    }

    public static void sendMessage(Pairing pairing, NetworkMessage message) throws TransportException {
        AmazonSQSClient client = getClient();
        client.sendMessage(recvQueueURL(pairing), Base64.encodeAsString(message.bytes()));
    }

    public static List<byte[]> receiveMessages(final Pairing pairing) throws TransportException {
        final AmazonSQSClient client = getClient();
        ReceiveMessageRequest request = new ReceiveMessageRequest(sendQueueURL(pairing));
        request.setWaitTimeSeconds(10);
        request.setMaxNumberOfMessages(10);
        ReceiveMessageResult result = client.receiveMessage(request);

        final List<DeleteMessageBatchRequestEntry> deleteEntries = new ArrayList<>();

        ArrayList<byte[]> messages = new ArrayList<byte[]>();
        for (Message m : result.getMessages()) {
            deleteEntries.add(new DeleteMessageBatchRequestEntry(m.getMessageId(), m.getReceiptHandle()));
            try {
                messages.add(Base64.decode(m.getBody()));
            } catch (Exception e) {
                Log.e(TAG, "failed to decode message: " + e.getMessage());
            }
        }

        if (!deleteEntries.isEmpty()) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        DeleteMessageBatchRequest deleteRequest = new DeleteMessageBatchRequest(sendQueueURL(pairing))
                                .withEntries(deleteEntries);
                        client.deleteMessageBatch(deleteRequest);
                    } catch (Exception e) {
                        Log.e(TAG, "failed to delete messages: " + e.getMessage());
                    }
                }
            }).start();
        }

        return messages;
    }
}
