package it.codeetoasty.msgraphexamplequarkus.sharepoint.services;

import com.microsoft.graph.core.models.UploadResult;
import com.microsoft.graph.core.tasks.LargeFileUploadTask;
import com.microsoft.graph.drives.item.items.item.createlink.CreateLinkPostRequestBody;
import com.microsoft.graph.drives.item.items.item.createuploadsession.CreateUploadSessionPostRequestBody;
import com.microsoft.graph.models.*;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.kiota.ApiException;
import com.microsoft.kiota.RequestInformation;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;

@Singleton
public class SharepointService {

    @ConfigProperty(name = "sharepoint.site.id")
    String siteId;

    @Inject
    GraphServiceClient graphClient;

    private String rootId;

    @PostConstruct
    public void getRootId() throws Exception {
        rootId = graphClient.sites().bySiteId(siteId)
                .drives().get().getValue().get(0).getId();
    }


    /**
     * this method formats every path with the right syntax, so that Graph Api can us it.
     * @param path
     * @return
     */
    private String formatPath(String path){
        return String.format("root:/%s:",path);
    }

    /**
     * Download a file given it's path on Sharepoint
     *
     * @param path
     * @return
     * @throws IOException
     */
    public byte[] downloadItem(String path) throws IOException {
        return graphClient
                .drives()
                .byDriveId(rootId)
                .items()
                .byDriveItemId(formatPath(path)).content().get().readAllBytes();
    }


    /**
     * This method finds folders or files given the right path.
     *
     * @param path
     * @return
     */
    public String findDriveItem(String path) {
        try {
            DriveItem driveItem = graphClient
                    .drives()
                    .byDriveId(rootId)
                    .items()
                    .byDriveItemId(formatPath(path))
                    .get();
            return driveItem.getId();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Method to recursively create nested folders.
     * It uses the path array to construct a StringBuilder to create, at every loop, the next folder.
     * @param path array of folder to be created, in the array order.
     */
    public void recursiveCreateFolder(String[] path){

        StringBuilder steppingPath = new StringBuilder();
        for(int i = 0; i<path.length; i++){

            String currentFolder = path[i];
            steppingPath.append(currentFolder);

            //check for existing folder
            String idCurrentFolder = findDriveItem(steppingPath.toString());
            if(idCurrentFolder == null){
                //if null create.
                DriveItem driveItem = new DriveItem();
                Folder folder = new Folder();
                driveItem.setName(currentFolder);
                driveItem.setFolder(folder);

                graphClient
                        .drives()
                        .byDriveId(rootId)
                        .items()
                        .byDriveItemId(formatPath(steppingPath.toString()))
                        .children()
                        .post(driveItem);
            }
            steppingPath.append("/");
        }
    }

    /**
     * creates a public link to a folder or file, it will be accessible for everyone
     * @param path
     * @return
     */
    public String createUrl(String path){
        CreateLinkPostRequestBody body = new CreateLinkPostRequestBody();
        body.setType("view");
        body.setScope("organization");

        Permission permission = graphClient
                .drives()
                .byDriveId(rootId)
                .items()
                .byDriveItemId(formatPath(path))
                .createLink()
                .post(body);
        return permission.getLink().getWebUrl();
    }

    /**
     * creates a private link to a folder or file, it will be accessible ONLY for users with access to that
     * folder or file
     * @param path
     * @return
     */
    public String createPrivateUrl(String path){
        CreateLinkPostRequestBody body = new CreateLinkPostRequestBody();
        body.setType("view");
        body.setScope("users");


        Permission permission = graphClient
                .drives()
                .byDriveId(rootId)
                .items()
                .byDriveItemId(formatPath(path))
                .createLink()
                .post(body);
        return permission.getLink().getWebUrl();
    }

    /**
     * This method let's you upload a file to Sharepoint.
     * In the official documentation it says it's intended for smaller files (4MB tops), but since the large file's
     * method is not working for now i use this instead, tested up to 100MB files and no problem, even when downloading.
     *
     * @param filePath is the FULL file path, starting from the root folder.
     * @param filename
     * @param file
     * @throws URISyntaxException
     */
    public void normalUpload(String filePath, String filename, byte[] file) throws URISyntaxException {
        String[] folders = filePath.split("/");
        recursiveCreateFolder(folders);
        ByteArrayInputStream bias = new ByteArrayInputStream(file);

        RequestInformation requestInformation = graphClient.drives()
                .byDriveId(rootId).items()
                .byDriveItemId("root:/"+filePath+"/"+filename+":")
                .content().toPutRequestInformation(bias);

        URI uriIncludesConflictBehavior = new URI(requestInformation.getUri().toString()+"?@microsoft.graph.conflictBehavior=fail");
        requestInformation.setUri(uriIncludesConflictBehavior);

        graphClient.getRequestAdapter()
                .sendPrimitive(requestInformation, null, InputStream.class);
    }

    /** I've tried to use this as the documentation intended but there's seems to be a bug sticking around even
     * in the latest version as of now (6.4.0).
     * Marked as deprecated to avoid using this without noticing.
     * For upload check method above
     */
    @Deprecated
    public void hugeUpload(String filePath, String filename, byte[] file) throws IOException, InvocationTargetException, IllegalAccessException, NoSuchMethodException {
        String[] folders = filePath.split("/");
        recursiveCreateFolder(folders);
        ByteArrayInputStream bias = new ByteArrayInputStream(file);
        long fileSize = bias.available();


        CreateUploadSessionPostRequestBody uploadSessionRequest = new CreateUploadSessionPostRequestBody();
        DriveItemUploadableProperties properties = new DriveItemUploadableProperties();
        properties.getAdditionalData().put("@microsoft.graph.conflictBehavior", "fail");
        uploadSessionRequest.setItem(properties);


        UploadSession uploadSession = graphClient.drives()
                .byDriveId(rootId)
                .items()
                .byDriveItemId("root:/"+filePath+"/"+filename+":")
                .createUploadSession()
                .post(uploadSessionRequest);


        LargeFileUploadTask<DriveItem> largeFileUploadTask = new LargeFileUploadTask<>(
                graphClient.getRequestAdapter(),
                uploadSession,
                bias,
                fileSize,
                DriveItem::createFromDiscriminatorValue);

        try{
            UploadResult<DriveItem> uploadResult = largeFileUploadTask.upload();
        } catch(ApiException | InterruptedException | IOException exception) {
            System.out.println(exception.getMessage());
        }
    }



}
