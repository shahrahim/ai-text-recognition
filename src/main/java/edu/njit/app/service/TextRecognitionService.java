package edu.njit.app.service;

import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class TextRecognitionService {

    private static final String SCHEME = "https";
    private final int size = 16 * 1024 * 1024;

    private final Environment env;
    private final WebClient webClient;
    private final RekognitionClient rekognitionClient;
    private SqsClient sqsClient;

    public TextRecognitionService(Environment environment) {
        this.env = environment;
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(size)).build();
        this.webClient = WebClient.builder().exchangeStrategies(strategies).build();
        this.rekognitionClient = RekognitionClient.builder().region(Region.US_WEST_2).build();
        this.sqsClient = SqsClient.builder()
                .region(Region.US_WEST_2).build();
    }

    @Scheduled(fixedRate = 0000)
    public void recognize() throws FileNotFoundException, UnsupportedEncodingException {
        Set<String> images = new HashSet<>();
        System.out.println("Began schedules SQS lookup");
        String sqsUrl = env.getProperty("SQS_URL");
        ReceiveMessageRequest receiveMessageRequest = ReceiveMessageRequest.builder()
                .queueUrl(sqsUrl)
                .visibilityTimeout(1)
                .maxNumberOfMessages(10)
                .build();
        this.sqsClient = SqsClient.builder()
                .region(Region.US_WEST_2).build();
        List<Message> messages = sqsClient.receiveMessage(receiveMessageRequest).messages();

        while (messages.size() > 0) {
            System.out.println("Check");
            for (final Message message : messages) {
                images.add(message.body());
                DeleteMessageRequest deleteMessageRequest = DeleteMessageRequest
                        .builder()
                        .queueUrl(sqsUrl)
                        .receiptHandle(message.receiptHandle())
                        .build();
                sqsClient.deleteMessage(deleteMessageRequest);
            }
            messages = sqsClient.receiveMessage(receiveMessageRequest).messages();
        }

        String fileName = "text-car-recognition-%s.txt";
        String s3Url = env.getProperty("S3_SRC");
        for (String imageName : images) {
            String outputFile = String.format(fileName, imageName.replace(".", "-"));
            PrintWriter writer = new PrintWriter(outputFile, "UTF-8");
            writer.println(String.format("FILE: %s - has a car in it with the following text above 70 percent confidence:", imageName));
            byte[] bytes = webClient.get()
                    .uri(builder ->
                            builder.scheme(SCHEME)
                                    .host(s3Url)
                                    .path(String.format("/%s", imageName))
                                    .build("yoo"))
                    .accept(MediaType.APPLICATION_OCTET_STREAM)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .toProcessor()
                    .block();

            ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
            Image image = Image
                    .builder()
                    .bytes(SdkBytes.fromByteBuffer(byteBuffer))
                    .build();
            DetectTextRequest textRequest = DetectTextRequest.builder()
                    .image(image)
                    .build();
            DetectTextResponse textResponse = rekognitionClient.detectText(textRequest);
            List<TextDetection> textCollection = textResponse.textDetections();
            Set<String> text = new HashSet<>();
            textCollection.forEach(textDetection -> {
                if (textDetection.confidence() > 70.0f) {
                    writer.println(String.format("'%s' has confidence: %s \n", textDetection.detectedText(),
                            textDetection.confidence()));
                }
            });
            writer.println("\n");
            System.out.printf("Wrote output for image: %s to file: %s.%n", imageName, outputFile);
            writer.close();
        }
    }

}
