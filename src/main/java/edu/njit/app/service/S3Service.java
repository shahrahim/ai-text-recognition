package edu.njit.app.service;

import com.amazonaws.auth.*;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.Bucket;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.DetectLabelsRequest;
import software.amazon.awssdk.services.rekognition.model.DetectLabelsResponse;
import software.amazon.awssdk.services.rekognition.model.Image;
import software.amazon.awssdk.services.rekognition.model.Label;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.nio.ByteBuffer;
import java.util.List;

@Service
public class S3Service {

    private final Environment env;

    public S3Service(Environment environment) {
        this.env = environment;
    }

    public String getImageTest(String fileId) {
        WebClient webClient = WebClient.builder()
                .build();
        Mono<byte[]> bytes = webClient.get()
                .uri(builder ->
                        builder.scheme("https")
                                .host("njit-cs-643.s3.us-east-1.amazonaws.com")
                                .path(String.format("/%s", fileId))
                                .build("yoo"))
                .accept(MediaType.APPLICATION_OCTET_STREAM)
                .retrieve()
                .bodyToMono(byte[].class);
        recognizeCar(bytes.block(), fileId);
        return "Hi";
    }

    private void recognizeCar(byte[] bytes, String fileId) {
        AmazonS3 s3client = AmazonS3ClientBuilder
                .standard()
                .withCredentials(new EnvironmentVariableCredentialsProvider())
                .withRegion(Regions.US_WEST_2)
                .build();

        String bucketName = "images-cc-assignment-1";

        if (s3client.doesBucketExist(bucketName)) {
            System.out.println("Bucket exists.");
        }

        List<Bucket> buckets = s3client.listBuckets();
        for (Bucket bucket : buckets) {
            System.out.println(bucket.getName());
        }

        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);

        Image image = Image
                .builder()
                .bytes(SdkBytes.fromByteBuffer(byteBuffer))
                .build();

        DetectLabelsRequest detectLabelsRequest = DetectLabelsRequest.builder()
                .image(image)
                .maxLabels(10)
                .build();

        RekognitionClient rekClient = RekognitionClient
                .builder()
                .region(Region.US_WEST_2)
                .build();

        DetectLabelsResponse labelsResponse = rekClient.detectLabels(detectLabelsRequest);
        List<Label> labels = labelsResponse.labels();

        System.out.println("Detected labels for the given photo");
        for (Label label: labels) {
            System.out.println(label.name() + ": " + label.confidence().toString());
        }

        SqsClient sqsClient = SqsClient
                .builder()
                .region(Region.US_WEST_2)
                .build();

        String sqsUrl = env.getProperty("SQS_URL");

        sqsClient.sendMessage(SendMessageRequest.builder()
                .queueUrl(sqsUrl)
                .messageBody(fileId)
                .delaySeconds(3)
                .build());

        ReceiveMessageRequest receiveMessageRequest = ReceiveMessageRequest.builder()
                .queueUrl(sqsUrl)
                .maxNumberOfMessages(5)
                .build();
        List<Message> messages = sqsClient.receiveMessage(receiveMessageRequest).messages();
        messages.forEach(message -> System.out.println(message.body()));
    }
}
