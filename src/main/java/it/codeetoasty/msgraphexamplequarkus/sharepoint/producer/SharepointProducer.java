package it.codeetoasty.msgraphexamplequarkus.sharepoint.producer;

import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Singleton
public class SharepointProducer {

    GraphServiceClient graphClient;

    @ConfigProperty(name = "sharepoint.site.id")
    String siteId;

    @ConfigProperty(name = "sharepoint.app.id")
    String clientId;

    @ConfigProperty(name = "sharepoint.secret")
    String secret;

    @ConfigProperty(name = "sharepoint.tenant.id")
    String tenantId;

    @ConfigProperty(name = "sharepoint.scopes")
    String scopes;

    @Produces
    public GraphServiceClient produce() {
        final ClientSecretCredential credential = new ClientSecretCredentialBuilder()
                .clientId(clientId).tenantId(tenantId).clientSecret(secret).build();
        graphClient = new GraphServiceClient(credential, scopes);
        return graphClient;
    }
}
