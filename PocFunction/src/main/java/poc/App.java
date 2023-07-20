package poc;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.lambda.powertools.idempotency.Idempotency;
import software.amazon.lambda.powertools.idempotency.IdempotencyConfig;
import software.amazon.lambda.powertools.idempotency.Idempotent;
import software.amazon.lambda.powertools.idempotency.persistence.DynamoDBPersistenceStore;
import software.amazon.lambda.powertools.logging.Logging;
import software.amazon.lambda.powertools.metrics.Metrics;
import software.amazon.lambda.powertools.parameters.ParamManager;
import software.amazon.lambda.powertools.parameters.SSMProvider;
import software.amazon.lambda.powertools.tracing.Tracing;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static java.time.temporal.ChronoUnit.MINUTES;
import static software.amazon.lambda.powertools.core.internal.LambdaConstants.*;
import static software.amazon.lambda.powertools.tracing.CaptureMode.DISABLED;

public class App implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    static Logger log = LogManager.getLogger();

    static SSMProvider ssmProvider;

    static {
        var ssmClientBuilder = SsmClient.builder()
                .httpClient(UrlConnectionHttpClient.builder().build())
                .region(Region.of(System.getenv(AWS_REGION_ENV)));

        // AWS_LAMBDA_INITIALIZATION_TYPE has two values on-demand and snap-start
        // when using snap-start mode, the env var creds provider isn't used and causes a fatal error if set
        // fall back to the default provider chain if the mode is anything other than on-demand.
        var initializationType = System.getenv().get(AWS_LAMBDA_INITIALIZATION_TYPE);
        System.out.println("Initialization type: " + initializationType);
        // Commented out to give the Idempotency library a chance to fail
//        if (initializationType != null && initializationType.equals(ON_DEMAND)) {
//            ssmClientBuilder.credentialsProvider(EnvironmentVariableCredentialsProvider.create());
//        }
        var ssmClient = ssmClientBuilder.build();

        ssmProvider = ParamManager.getSsmProvider(ssmClient).defaultMaxAge(1, MINUTES);
    }

    public App() {
        System.out.println("Initialize Idempotency");
        var initializationType = System.getenv().get(AWS_LAMBDA_INITIALIZATION_TYPE);
        System.out.println("Initialization type: " + initializationType);
        Idempotency.config().withPersistenceStore(
                        DynamoDBPersistenceStore.builder()
                                .withTableName(System.getenv("IDEMPOTENCY_TABLE"))
                                .build()
                )
                .withConfig(IdempotencyConfig.builder()
                        .withExpiration(Duration.ofMinutes(1))
                        .build())
                .configure();
    }

    @Logging(logEvent = true)
    @Tracing(captureMode = DISABLED)
    @Metrics(captureColdStart = true)
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        try {
            var idempotencyKey = UUID.randomUUID().toString();
            somethingIdempotent(idempotencyKey);
            var value = ssmProvider.get(System.getenv("PARAMETER_NAME"));
            var output = "{ \"parameter-value\": \"" + value + "\" }";

            return new APIGatewayProxyResponseEvent()
                    .withHeaders(Map.of(
                            "Content-Type", "application/json",
                            "X-Custom-Header", "application/json"
                    ))
                    .withStatusCode(200)
                    .withBody(output);
        } catch (Exception e) {
            log.error("Exception: ", e);
            e.printStackTrace();
            return new APIGatewayProxyResponseEvent()
                    .withBody("Exception: " + e.getMessage())
                    .withStatusCode(500);
        }
    }

    @Idempotent
    private Void somethingIdempotent(String key) {
        System.out.println("Idempotent method called: " + key);
        return null;
    }
}
