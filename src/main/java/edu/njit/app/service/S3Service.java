package edu.njit.app.service;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.rekognition.AmazonRekognitionClient;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.rekognition.AmazonRekognitionClientBuilder;
import com.amazonaws.services.s3.model.Bucket;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

@Service
public class S3Service {

    public Mono<byte[]> getImageTest(String fileId) {
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
        uploadToS3(bytes.block());
        return bytes;
    }

    private void uploadToS3(byte[] bytes) {
        AmazonS3 s3client = AmazonS3ClientBuilder
                .standard()
                .withCredentials(new EnvironmentVariableCredentialsProvider())
                .withRegion(Regions.US_WEST_2)
                .build();


        String bucketName = "images-cc-assignment-1";

        if (s3client.doesBucketExist(bucketName)) {
            System.out.println("Bucket does not exist");
        }

        List<Bucket> buckets = s3client.listBuckets();
        for (Bucket bucket : buckets) {
            System.out.println(bucket.getName());
        }

//        InputStream targetStream = new ByteArrayInputStream(bytes);
//        s3client.putObject("images-cc-assignment-1", "1.jpg", targetStream, null);
//
//
//
//        AmazonRekognitionClient rekognitionClient = AmazonRekognitionClientBuilder
//                .standard()
//                .withCredentials(new AWSStaticCredentialsProvider(credentials))

    }
}
